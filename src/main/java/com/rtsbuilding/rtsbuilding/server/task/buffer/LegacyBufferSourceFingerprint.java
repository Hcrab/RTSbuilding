package com.rtsbuilding.rtsbuilding.server.task.buffer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * 旧 Session 缓存来源的 canonical SHA-256 指纹。
 *
 * <p>指纹包含每个栈的序号、物品、数量及完整数据组件 NBT。Compound key 会排序，List 保持
 * 原顺序，因此同一来源跨重启稳定，栈顺序或任意 NBT 内容变化都会形成另一来源。该值不包含维度，
 * 避免玩家在 ACK 窗口切维时为同一批物品生成第二个迁移身份。</p>
 */
public record LegacyBufferSourceFingerprint(String sha256) {
    private static final String DOMAIN = "rtsbuilding:legacy-buffer-source";
    private static final int HASH_VERSION = 1;

    public LegacyBufferSourceFingerprint {
        Objects.requireNonNull(sha256, "sha256");
        if (sha256.length() != 64) throw new IllegalArgumentException("source fingerprint 必须是 SHA-256");
        for (int i = 0; i < sha256.length(); i++) {
            char value = sha256.charAt(i);
            if (!((value >= '0' && value <= '9') || (value >= 'a' && value <= 'f'))) {
                throw new IllegalArgumentException("source fingerprint 必须是 canonical 小写十六进制");
            }
        }
    }

    public static LegacyBufferSourceFingerprint freeze(
            RegistryAccess registryAccess, List<ItemStack> orderedStacks) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        Objects.requireNonNull(orderedStacks, "orderedStacks");
        List<Tag> encoded = new ArrayList<>(orderedStacks.size());
        for (ItemStack stack : orderedStacks) {
            if (stack == null || stack.isEmpty()) {
                throw new IllegalArgumentException("legacy buffer source 不能包含空物品栈");
            }
            encoded.add(stack.copy().save(registryAccess));
        }
        return freezeSerialized(encoded);
    }

    /** 包内测试与迁移 codec 可直接对已编码栈做 canonical 校验。 */
    static LegacyBufferSourceFingerprint freezeSerialized(List<? extends Tag> orderedStacks) {
        Objects.requireNonNull(orderedStacks, "orderedStacks");
        if (orderedStacks.isEmpty() || orderedStacks.size() > BufferEscrowState.MAX_STACKS) {
            throw new IllegalArgumentException("legacy buffer source 栈数量越界");
        }
        ListTag stacks = new ListTag();
        for (int ordinal = 0; ordinal < orderedStacks.size(); ordinal++) {
            Tag stack = Objects.requireNonNull(orderedStacks.get(ordinal), "encoded stack");
            if (stack.getId() == Tag.TAG_END
                    || (stack instanceof CompoundTag compound && compound.isEmpty())) {
                throw new IllegalArgumentException("encoded stack 不能为空");
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("ordinal", ordinal);
            entry.put("stack", stack.copy());
            stacks.add(entry);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema", HASH_VERSION);
        root.put("stacks", stacks);
        return new LegacyBufferSourceFingerprint(canonicalSha256(root));
    }

    private static String canonicalSha256(Tag root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putString(digest, DOMAIN, "hash domain");
            putInt(digest, HASH_VERSION);
            hashTag(digest, root);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", impossible);
        }
    }

    /** 与任务资产的 canonical NBT 规则一致，但保留原始浮点位以证明精确来源内容。 */
    private static void hashTag(MessageDigest digest, Tag tag) {
        Objects.requireNonNull(tag, "tag");
        digest.update(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE -> digest.update(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> putShort(digest, ((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> putInt(digest, ((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> putLong(digest, ((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> putInt(digest,
                    Float.floatToRawIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> putLong(digest,
                    Double.doubleToRawLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray();
                putInt(digest, values.length);
                digest.update(values);
            }
            case Tag.TAG_STRING -> putString(
                    digest, ((StringTag) tag).getAsString(), "NBT 字符串");
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                putInt(digest, list.size());
                for (Tag element : list) hashTag(digest, element);
            }
            case Tag.TAG_COMPOUND -> {
                CompoundTag compound = (CompoundTag) tag;
                List<String> keys = new ArrayList<>(compound.getAllKeys());
                keys.sort(String::compareTo);
                putInt(digest, keys.size());
                for (String key : keys) {
                    putString(digest, key, "Compound key");
                    Tag value = compound.get(key);
                    if (value == null) throw new IllegalArgumentException("Compound key 缺失值: " + key);
                    hashTag(digest, value);
                }
            }
            case Tag.TAG_INT_ARRAY -> {
                int[] values = ((IntArrayTag) tag).getAsIntArray();
                putInt(digest, values.length);
                for (int value : values) putInt(digest, value);
            }
            case Tag.TAG_LONG_ARRAY -> {
                long[] values = ((LongArrayTag) tag).getAsLongArray();
                putInt(digest, values.length);
                for (long value : values) putLong(digest, value);
            }
            default -> throw new IllegalArgumentException("不支持参与 source fingerprint 的 NBT 类型: " + tag.getId());
        }
    }

    private static void putString(MessageDigest digest, String value, String field) {
        requirePairedSurrogates(value, field);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        putInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void requirePairedSurrogates(String value, String field) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (i + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(i + 1))) {
                    throw new IllegalArgumentException(field + " 包含未配对的高代理项");
                }
                i++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(field + " 包含未配对的低代理项");
            }
        }
    }

    private static void putShort(MessageDigest digest, short value) {
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void putLong(MessageDigest digest, long value) {
        putInt(digest, (int) (value >>> 32));
        putInt(digest, (int) value);
    }
}
