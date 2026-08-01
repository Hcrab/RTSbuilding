package com.rtsbuilding.rtsbuilding.common.persist;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * UI 状态二进制"编译器"。
 *
 * <p>将 {@link RtsClientUiStateStore.UiState} "编译"为不可读的二进制格式（.rtsd），
 * 防止玩家直接编辑 JSON 文件篡改配置。附带 HMAC-SHA256 完整性校验，
 * 文件被篡改后模组会自动忽略并恢复默认值。
 *
 * <h3>二进制格式</h3>
 * <pre>
 * 偏移   长度    说明
 * ───────────────────────────────────────────
 * 0      4B     魔数 0x52545344 ("RTSD")
 * 4      4B     加密数据长度（大端序，不含 header 和 HMAC）
 * 8      N      XOR 混淆的 GZip 压缩 JSON 数据
 * 8+N    32B    HMAC-SHA256(魔数 || 数据长度 || 加密数据)
 * </pre>
 *
 * <p>写入流程：{@code UiState → Gson.toJson() → GZip → XOR → HMAC → byte[]}
 * <br>读取流程：{@code byte[] → 验证 HMAC → XOR → GUnzip → Gson.fromJson() → UiState}
 */
final class UiStateCodec {
    private static final Logger LOG = LoggerFactory.getLogger("RtsClientUiState");

    /** 魔数 "RTSD"（RTS Data） */
    private static final int MAGIC = 0x52545344;

    /** HMAC 密钥（编译固定，防君子不防小人） */
    private static final byte[] HMAC_KEY = "RTS_UI_2024!".getBytes(StandardCharsets.UTF_8);

    /** XOR 混淆密钥（16 字节循环使用） */
    private static final byte[] XOR_KEY = {
            (byte) 0x5A, (byte) 0x3C, (byte) 0x1F, (byte) 0x7B,
            (byte) 0x2D, (byte) 0x4E, (byte) 0x6F, (byte) 0x8A,
            (byte) 0x9B, (byte) 0xC1, (byte) 0xD2, (byte) 0xE3,
            (byte) 0xF4, (byte) 0x05, (byte) 0x16, (byte) 0x27
    };

    /** HMAC-SHA256 输出长度 */
    static final int HMAC_LENGTH = 32;

    /** 固定头长度：4B 魔数 + 4B 数据长度 */
    private static final int HEADER_SIZE = 8;

    private UiStateCodec() {
    }

    // ======================== 公开 API ========================

    /**
     * "编译" UI 状态为二进制数据。
     *
     * @param state 要编译的 UI 状态（建议先 {@code sanitized()}）
     * @return 编译后的二进制字节数组
     * @throws IOException 序列化或压缩失败
     */
    static byte[] encode(RtsClientUiStateStore.UiState state) throws IOException {
        // 1. 序列化为 JSON → UTF-8 字节
        Gson gson = RtsClientUiStateStore.gson();
        byte[] jsonBytes = gson.toJson(state).getBytes(StandardCharsets.UTF_8);

        // 2. GZip 压缩（隐藏原始 JSON 结构）
        byte[] compressed;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            gzipOut.write(jsonBytes);
            gzipOut.finish();
            compressed = baos.toByteArray();
        }

        // 3. XOR 混淆（掩盖 GZip 魔数 1f8b，让它看起来像纯随机二进制）
        xorTransform(compressed, 0, compressed.length);

        // 4. 组装 header
        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
        header.putInt(MAGIC);
        header.putInt(compressed.length);

        // 5. 计算 HMAC-SHA256
        byte[] hmac = computeHmac(header.array(), compressed);

        // 6. 拼装最终输出
        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + compressed.length + HMAC_LENGTH);
        out.put(header.array());
        out.put(compressed);
        out.put(hmac);
        return out.array();
    }

    /**
     * "反编译"二进制数据为 UI 状态。
     *
     * @param data 二进制数据
     * @return 反编译成功的 UiState，校验失败时返回 null
     */
    static RtsClientUiStateStore.UiState decode(byte[] data) {
        if (data == null || data.length < HEADER_SIZE + HMAC_LENGTH) {
            LOG.warn("二进制文件格式错误：数据太短 ({} 字节)", data == null ? 0 : data.length);
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // 1. 验证魔数
            int magic = buffer.getInt();
            if (magic != MAGIC) {
                LOG.warn("二进制文件魔数不匹配：期望 0x{}, 实际 0x{}",
                        Integer.toHexString(MAGIC), Integer.toHexString(magic));
                return null;
            }

            // 2. 读取数据长度
            int dataLen = buffer.getInt();
            if (dataLen <= 0 || HEADER_SIZE + dataLen + HMAC_LENGTH > data.length) {
                LOG.warn("二进制文件长度字段异常: {}", dataLen);
                return null;
            }

            // 3. 分离加密数据和 HMAC
            byte[] encrypted = new byte[dataLen];
            buffer.get(encrypted);
            byte[] storedHmac = new byte[HMAC_LENGTH];
            buffer.get(storedHmac);

            // 4. 验证 HMAC（先还原 header）
            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE);
            header.putInt(MAGIC);
            header.putInt(dataLen);
            byte[] expectedHmac = computeHmac(header.array(), encrypted);

            if (!MessageDigest.isEqual(storedHmac, expectedHmac)) {
                LOG.warn("文件完整性校验失败，疑似被篡改，将使用默认值");
                return null;
            }

            // 5. XOR 解密
            xorTransform(encrypted, 0, encrypted.length);

            // 6. GZip 解压
            byte[] jsonBytes;
            try (ByteArrayInputStream bais = new ByteArrayInputStream(encrypted);
                 GZIPInputStream gzipIn = new GZIPInputStream(bais)) {
                jsonBytes = gzipIn.readAllBytes();
            }

            // 7. 反序列化为 UiState
            String json = new String(jsonBytes, StandardCharsets.UTF_8);
            Gson gson = RtsClientUiStateStore.gson();
            return gson.fromJson(json, RtsClientUiStateStore.UiState.class);

        } catch (Exception e) {
            LOG.warn("反编译二进制 UI 状态失败", e);
            return null;
        }
    }

    // ======================== 内部工具方法 ========================

    /**
     * XOR 变换（可逆操作，加密和解密用同一个方法）。
     */
    private static void xorTransform(byte[] data, int offset, int length) {
        for (int i = 0; i < length; i++) {
            data[offset + i] ^= XOR_KEY[i % XOR_KEY.length];
        }
    }

    /**
     * 计算 HMAC-SHA256。
     *
     * @param header 头部数据（魔数 + 数据长度）
     * @param data   加密数据
     * @return 32 字节 HMAC 值
     */
    private static byte[] computeHmac(byte[] header, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(HMAC_KEY, "HmacSHA256");
            mac.init(keySpec);
            mac.update(header);
            mac.update(data);
            return mac.doFinal();
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }
}
