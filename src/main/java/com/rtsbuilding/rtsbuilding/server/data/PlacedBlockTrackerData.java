package com.rtsbuilding.rtsbuilding.server.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlacedBlockTrackerData extends SavedData {
    private static final String DATA_NAME = "rtsbuilding_placed_blocks";
    private static final String KEY_PLACED = "placed";

    private final LongOpenHashSet placedPositions;

    private PlacedBlockTrackerData() {
        this.placedPositions = new LongOpenHashSet();
    }

    private static PlacedBlockTrackerData load(CompoundTag tag) {
        PlacedBlockTrackerData data = new PlacedBlockTrackerData();
        long[] packed = tag.getLongArray(KEY_PLACED);
        for (long value : packed) {
            data.placedPositions.add(value);
        }
        return data;
    }

    public static PlacedBlockTrackerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PlacedBlockTrackerData::load, PlacedBlockTrackerData::new, DATA_NAME);
    }

    public void mark(BlockPos pos) {
        if (this.placedPositions.add(pos.asLong())) {
            setDirty();
        }
    }

    public void clear(BlockPos pos) {
        if (this.placedPositions.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean isPlaced(BlockPos pos) {
        return this.placedPositions.contains(pos.asLong());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putLongArray(KEY_PLACED, this.placedPositions.toLongArray());
        return tag;
    }
}
