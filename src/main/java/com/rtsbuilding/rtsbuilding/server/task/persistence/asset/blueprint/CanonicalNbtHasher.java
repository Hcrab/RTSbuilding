package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 只负责把 NBT 树转换为跨重启稳定的 canonical SHA-256。
 *
 * <p>本类不理解蓝图 schema，不检查业务限额，也不负责压缩或磁盘 I/O。Compound key 按
 * {@link String#compareTo(String)} 排序，List 保持原顺序，所有数字使用显式大端编码。</p>
 */
final class CanonicalNbtHasher {
    private CanonicalNbtHasher() { }

    static String sha256(String domain, int hashVersion, Tag root) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putCanonicalString(digest, domain, "hash domain");
            putInt(digest, hashVersion);
            hashTag(digest, root);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", impossible);
        }
    }

    private static void hashTag(MessageDigest digest, Tag tag) {
        digest.update(tag.getId());
        switch (tag.getId()) {
            case Tag.TAG_END -> { }
            case Tag.TAG_BYTE -> digest.update(((NumericTag) tag).getAsByte());
            case Tag.TAG_SHORT -> putShort(digest, ((NumericTag) tag).getAsShort());
            case Tag.TAG_INT -> putInt(digest, ((NumericTag) tag).getAsInt());
            case Tag.TAG_LONG -> putLong(digest, ((NumericTag) tag).getAsLong());
            case Tag.TAG_FLOAT -> putInt(digest, Float.floatToIntBits(((NumericTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE -> putLong(digest, Double.doubleToLongBits(((NumericTag) tag).getAsDouble()));
            case Tag.TAG_BYTE_ARRAY -> {
                byte[] values = ((ByteArrayTag) tag).getAsByteArray();
                putInt(digest, values.length);
                digest.update(values);
            }
            case Tag.TAG_STRING -> putCanonicalString(
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
                    putCanonicalString(digest, key, "Compound key");
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
            default -> throw new IllegalArgumentException("不支持参与 canonical hash 的 NBT 类型: " + tag.getId());
        }
    }

    private static void putBytes(MessageDigest digest, byte[] values) {
        putInt(digest, values.length);
        digest.update(values);
    }

    private static void putCanonicalString(MessageDigest digest, String value, String field) {
        requirePairedSurrogates(value, field);
        putBytes(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    /** UTF-8 编码器会把孤立代理项替换成同一个字符，必须在哈希前拒绝这种歧义输入。 */
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
