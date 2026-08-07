package com.rtsbuilding.rtsbuilding.common.trace;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成并格式化一次客户端会话内唯一的诊断 traceId。
 *
 * <p>本类不依赖 Minecraft 或 Forge，方便旧版本和现代版本共用同一身份语义。
 * 高 31 位来自本次 JVM 会话，低 32 位为单调计数；零始终表示没有客户端因果来源。</p>
 */
public final class RtsTraceIds {
    public static final long NONE = 0L;

    private static final long SESSION_NONCE = createSessionNonce();
    private static final AtomicLong COUNTER = new AtomicLong(1L);

    private RtsTraceIds() {
    }

    public static long nextClientTraceId() {
        long sequence = COUNTER.getAndIncrement() & 0xffffffffL;
        if (sequence == 0L) {
            sequence = COUNTER.getAndIncrement() & 0xffffffffL;
        }
        return SESSION_NONCE | sequence;
    }

    public static boolean isPresent(long traceId) {
        return traceId > 0L;
    }

    /** latest.log 中固定输出 16 位小写十六进制，便于复制和搜索。 */
    public static String format(long traceId) {
        if (!isPresent(traceId)) {
            return "-";
        }
        String raw = Long.toHexString(traceId);
        StringBuilder padded = new StringBuilder(16);
        for (int i = raw.length(); i < 16; i++) {
            padded.append('0');
        }
        return padded.append(raw).toString();
    }

    /** 当前 JVM 启动周期的短标识；仅用于把不同诊断事件归入同一次运行。 */
    public static String runId() {
        String raw = Long.toHexString(SESSION_NONCE >>> 32);
        StringBuilder padded = new StringBuilder(8);
        for (int i = raw.length(); i < 8; i++) {
            padded.append('0');
        }
        return padded.append(raw).toString();
    }

    private static long createSessionNonce() {
        long mixed = System.currentTimeMillis()
                ^ System.nanoTime()
                ^ ((long) System.identityHashCode(RtsTraceIds.class) << 17);
        long positive31Bits = (mixed ^ (mixed >>> 32)) & 0x7fffffffL;
        if (positive31Bits == 0L) {
            positive31Bits = 1L;
        }
        return positive31Bits << 32;
    }
}
