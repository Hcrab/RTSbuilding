package com.rtsbuilding.rtsbuilding.server.task.persistence.asset.blueprint;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 把 1.12.2 NBT 树转换为跨重启稳定的 canonical SHA-256。
 * Compound key 排序，List 保序，数字使用显式大端编码；不理解蓝图业务 schema。
 */
final class CanonicalNbtHasher {
    private CanonicalNbtHasher() { }

    static String sha256(String domain, int hashVersion, NBTBase root) {
        if (root == null) throw new IllegalArgumentException("NBT 根不能为空");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            putCanonicalString(digest, domain, "hash domain");
            putInt(digest, hashVersion);
            hashTag(digest, root);
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", impossible);
        }
    }

    private static void hashTag(MessageDigest digest, NBTBase tag) {
        byte id = tag.getId();
        digest.update(id);
        switch (id) {
            case 0:
                return;
            case 1:
                digest.update(((NBTBase.NBTPrimitive) tag).func_150290_f());
                return;
            case 2:
                putShort(digest, ((NBTBase.NBTPrimitive) tag).func_150289_e());
                return;
            case 3:
                putInt(digest, ((NBTBase.NBTPrimitive) tag).func_150287_d());
                return;
            case 4:
                putLong(digest, ((NBTTagLong) tag).func_150291_c());
                return;
            case 5:
                putInt(digest, Float.floatToIntBits(((NBTBase.NBTPrimitive) tag).func_150288_h()));
                return;
            case 6:
                putLong(digest, Double.doubleToLongBits(((NBTBase.NBTPrimitive) tag).func_150286_g()));
                return;
            case 7: {
                byte[] values = ((NBTTagByteArray) tag).func_150292_c();
                putInt(digest, values.length);
                digest.update(values);
                return;
            }
            case 8:
                putCanonicalString(digest, ((NBTTagString) tag).func_150285_a_(), "NBT 字符串");
                return;
            case 9: {
                NBTTagList list = (NBTTagList) tag;
                putInt(digest, list.tagCount());
                for (int i = 0; i < list.tagCount(); i++) {
                    hashTag(digest,
                            com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.listElement(list, i));
                }
                return;
            }
            case 10: {
                NBTTagCompound compound = (NBTTagCompound) tag;
                List<String> keys = new ArrayList<String>(compound.func_150296_c());
                Collections.sort(keys);
                putInt(digest, keys.size());
                for (String key : keys) {
                    putCanonicalString(digest, key, "Compound key");
                    NBTBase value = compound.getTag(key);
                    if (value == null) throw new IllegalArgumentException("Compound key 缺失值: " + key);
                    hashTag(digest, value);
                }
                return;
            }
            case 11: {
                int[] values = ((NBTTagIntArray) tag).func_150302_c();
                putInt(digest, values.length);
                for (int value : values) putInt(digest, value);
                return;
            }
            default:
                // 1.12.2 原生 NBT 没有 long-array tag；未知扩展不能静默产生不稳定哈希。
                throw new IllegalArgumentException("不支持参与 canonical hash 的 NBT 类型: " + id);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = digits[value >>> 4];
            out[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(out);
    }

    private static void putBytes(MessageDigest digest, byte[] values) {
        putInt(digest, values.length);
        digest.update(values);
    }

    private static void putCanonicalString(MessageDigest digest, String value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " 不能为空");
        requirePairedSurrogates(value, field);
        putBytes(digest, value.getBytes(StandardCharsets.UTF_8));
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
