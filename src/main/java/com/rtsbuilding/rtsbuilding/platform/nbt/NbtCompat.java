package com.rtsbuilding.rtsbuilding.platform.nbt;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 收口 1.12 与 1.7.10 的 NBT API 差异。
 *
 * <p>1.7.10 的 NBTTagList 没有公开通用索引器；这里集中使用一次经过 SRG 名保护的反射，
 * 避免蓝图、任务、历史记录各自复制脆弱反射。所有复制与合并都复制 tag，调用方不会
 * 意外共享可变 NBT 子树。</p>
 */
public final class NbtCompat {
    private static final Field LIST_FIELD = ReflectionHelper.findField(
            NBTTagList.class, "tagList", "field_74747_a");

    private NbtCompat() {}

    public static boolean isEmpty(NBTBase tag) {
        if (tag == null) return true;
        if (tag instanceof NBTTagCompound) return ((NBTTagCompound) tag).hasNoTags();
        if (tag instanceof NBTTagList) return ((NBTTagList) tag).tagCount() == 0;
        return false;
    }

    public static NBTBase listElement(NBTTagList list, int index) {
        if (list == null || index < 0 || index >= list.tagCount()) return null;
        try {
            @SuppressWarnings("unchecked")
            List<NBTBase> values = (List<NBTBase>) LIST_FIELD.get(list);
            return values.get(index);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("无法读取 1.7.10 NBTTagList 元素", failure);
        }
    }

    public static int listElementType(NBTTagList list) {
        NBTBase first = listElement(list, 0);
        return first == null ? 0 : first.getId();
    }

    public static int getIntAt(NBTTagList list, int index) {
        NBTBase value = listElement(list, index);
        return value instanceof NBTBase.NBTPrimitive
                ? ((NBTBase.NBTPrimitive) value).func_150287_d() : 0;
    }

    public static long getLongAt(NBTTagList list, int index) {
        NBTBase value = listElement(list, index);
        return value instanceof NBTBase.NBTPrimitive
                ? ((NBTBase.NBTPrimitive) value).func_150291_c() : 0L;
    }

    public static void merge(NBTTagCompound target, NBTTagCompound source) {
        if (target == null || source == null) return;
        @SuppressWarnings("unchecked")
        Set<String> keys = source.func_150296_c();
        for (String key : keys) {
            NBTBase value = source.getTag(key);
            if (value != null) target.setTag(key, value.copy());
        }
    }

    public static NBTTagCompound copyCompound(NBTTagCompound value) {
        return value == null ? new NBTTagCompound() : (NBTTagCompound) value.copy();
    }

    /** UUID 使用单字段四整数格式，和现代主线 schema 一致，也避免 1.12 的 Most/Least 展开。 */
    public static void setUuid(NBTTagCompound tag, String key, UUID value) {
        if (tag == null || key == null || value == null) return;
        long most = value.getMostSignificantBits();
        long least = value.getLeastSignificantBits();
        tag.setIntArray(key, new int[] {
                (int) (most >>> 32), (int) most, (int) (least >>> 32), (int) least
        });
    }

    public static boolean hasUuid(NBTTagCompound tag, String key) {
        return tag != null && tag.hasKey(key, 11) && tag.getIntArray(key).length == 4;
    }

    public static boolean containsUuidField(NBTTagCompound tag, String key) {
        return tag != null && (tag.hasKey(key) || tag.hasKey(key + "Most") || tag.hasKey(key + "Least"));
    }

    public static UUID getUuid(NBTTagCompound tag, String key) {
        if (!hasUuid(tag, key)) return null;
        int[] values = tag.getIntArray(key);
        long most = ((long) values[0] << 32) | (values[1] & 0xffffffffL);
        long least = ((long) values[2] << 32) | (values[3] & 0xffffffffL);
        return new UUID(most, least);
    }

    /** 方块状态在旧版以注册名 + metadata 保存，避免运行时数字 ID 漂移。 */
    public static NBTTagCompound writeBlockState(BlockState state) {
        NBTTagCompound tag = new NBTTagCompound();
        BlockState safe = state == null ? BlockState.defaultState(net.minecraft.init.Blocks.air) : state;
        ResourceLocation id = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS
                .getNameForObject(safe.getBlock());
        tag.setString("id", id == null ? "minecraft:air" : id.toString());
        tag.setInteger("meta", safe.getMetadata());
        return tag;
    }

    public static BlockState readBlockState(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("id", 8)) {
            return BlockState.defaultState(net.minecraft.init.Blocks.air);
        }
        Block block = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.BLOCKS
                .getValue(new ResourceLocation(tag.getString("id")));
        return BlockState.of(block, tag.getInteger("meta"));
    }
}
