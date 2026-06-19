package com.rtsbuilding.rtsbuilding.server.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 追踪已放置方块位置的世界存档数据。
 * 用于记录玩家在建筑模式下放置的方块，以便在撤销或清除时恢复。
 */
public final class PlacedBlockTrackerData extends SavedData {
    /** 存档数据在 SavedData 中的注册名称 */
    private static final String DATA_NAME = "rtsbuilding_placed_blocks";
    /** NBT 键名：存储已放置方块位置的长整型数组 */
    private static final String KEY_PLACED = "placed";

    /** 已放置方块追踪数据的工厂实例，用于创建和加载数据 */
    private static final Factory<PlacedBlockTrackerData> FACTORY = new Factory<>(
            PlacedBlockTrackerData::new,
            PlacedBlockTrackerData::load);

    /** 已放置方块位置集合（使用 long 编码的 BlockPos） */
    private final LongOpenHashSet placedPositions;

    /** 创建新的空追踪数据实例 */
    private PlacedBlockTrackerData() {
        this.placedPositions = new LongOpenHashSet();
    }

    /** 从 NBT 中加载已放置方块追踪数据 */
    private static PlacedBlockTrackerData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        long[] packed = tag.getLongArray(KEY_PLACED);
        for (long value : packed) {
            data.placedPositions.add(value);
        }
        return data;
    }

    /**
     * 获取指定世界的已放置方块追踪数据。
     * 如果不存在则创建新的实例。
     */
    public static PlacedBlockTrackerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** 标记指定位置的方块为已放置状态 */
    public void mark(BlockPos pos) {
        if (this.placedPositions.add(pos.asLong())) {
            setDirty(); // 标记数据已变更，下次保存时写入磁盘
        }
    }

    /** 清除指定位置的已放置标记 */
    public void clear(BlockPos pos) {
        if (this.placedPositions.remove(pos.asLong())) {
            setDirty(); // 标记数据已变更，下次保存时写入磁盘
        }
    }

    /** 检查指定位置是否已被标记为已放置 */
    public boolean isPlaced(BlockPos pos) {
        return this.placedPositions.contains(pos.asLong());
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        tag.putLongArray(KEY_PLACED, this.placedPositions.toLongArray()); // 将所有已放置方块位置写入 NBT
        return tag;
    }
}

