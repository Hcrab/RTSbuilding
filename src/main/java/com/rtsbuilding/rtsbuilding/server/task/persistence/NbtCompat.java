package com.rtsbuilding.rtsbuilding.server.task.persistence;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraftforge.common.util.Constants;

import java.util.UUID;

/** 1.12.2 NBT 对现代 UUID 与 long-array 便捷 API 的等价实现。 */
public final class NbtCompat {
    private NbtCompat() {
    }

    public static void setUuid(NBTTagCompound tag, String key, UUID value) {
        tag.setUniqueId(key, value);
    }

    public static boolean hasUuid(NBTTagCompound tag, String key) {
        return tag.hasUniqueId(key);
    }

    public static UUID getUuid(NBTTagCompound tag, String key) {
        return tag.getUniqueId(key);
    }

    public static boolean hasType(NBTTagCompound tag, String key, int type) {
        return tag.hasKey(key, type);
    }

    public static void setLongArray(NBTTagCompound tag, String key, long[] values) {
        NBTTagList encoded = new NBTTagList();
        for (long value : values) encoded.appendTag(new NBTTagLong(value));
        tag.setTag(key, encoded);
    }

    public static long[] getLongArray(NBTTagCompound tag, String key) {
        NBTTagList encoded = tag.getTagList(key, Constants.NBT.TAG_LONG);
        long[] values = new long[encoded.tagCount()];
        for (int i = 0; i < values.length; i++) {
            NBTBase value = encoded.get(i);
            if (!(value instanceof NBTTagLong)) {
                throw new IllegalArgumentException("NBT long array 元素类型无效: " + key);
            }
            values[i] = ((NBTTagLong) value).getLong();
        }
        return values;
    }
}
