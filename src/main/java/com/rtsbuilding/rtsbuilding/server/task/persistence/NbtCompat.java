package com.rtsbuilding.rtsbuilding.server.task.persistence;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** 任务持久化保留的窄门面；实际旧版 NBT 差异由 platform 层统一实现。 */
public final class NbtCompat {
    private NbtCompat() {}

    public static void setUuid(NBTTagCompound tag, String key, UUID value) {
        com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.setUuid(tag, key, value);
    }
    public static boolean hasUuid(NBTTagCompound tag, String key) {
        return com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.hasUuid(tag, key);
    }
    public static boolean containsUuidField(NBTTagCompound tag, String key) {
        return com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.containsUuidField(tag, key);
    }
    public static UUID getUuid(NBTTagCompound tag, String key) {
        return com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.getUuid(tag, key);
    }
    public static boolean hasType(NBTTagCompound tag, String key, int type) {
        return tag != null && tag.hasKey(key, type);
    }

    /** long[] 在 1.7.10 中编码成高低 32 位对；避免不存在的 NBTTagLongArray。 */
    public static void setLongArray(NBTTagCompound tag, String key, long[] values) {
        long[] safe = values == null ? new long[0] : values;
        int[] encoded = new int[safe.length * 2];
        for (int i = 0; i < safe.length; i++) {
            encoded[i * 2] = (int) (safe[i] >>> 32);
            encoded[i * 2 + 1] = (int) safe[i];
        }
        tag.setIntArray(key, encoded);
    }

    public static long[] getLongArray(NBTTagCompound tag, String key) {
        if (tag == null || !tag.hasKey(key, 11)) return new long[0];
        int[] encoded = tag.getIntArray(key);
        if ((encoded.length & 1) != 0) {
            throw new IllegalArgumentException("NBT long array 长度不是偶数: " + key);
        }
        long[] values = new long[encoded.length / 2];
        for (int i = 0; i < values.length; i++) {
            values[i] = ((long) encoded[i * 2] << 32) | (encoded[i * 2 + 1] & 0xffffffffL);
        }
        return values;
    }
}
