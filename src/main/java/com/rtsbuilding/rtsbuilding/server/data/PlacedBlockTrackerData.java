package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;

import java.util.LinkedHashSet;
import java.util.Set;

/** 记录由 RTS 放置的方块位置；数据按维度存入 1.7.10 per-world MapStorage。 */
public final class PlacedBlockTrackerData extends WorldSavedData {
    private static final String DATA_NAME = "rtsbuilding_placed_blocks";
    private static final String KEY_PLACED = "placed";
    private static final String LEGACY_KEY_PLACED = "placed_positions";

    private final Set<Long> placedPositions = new LinkedHashSet<Long>();

    public PlacedBlockTrackerData() {
        this(DATA_NAME);
    }

    /** MapStorage 反射加载所需的 String 构造器。 */
    public PlacedBlockTrackerData(String name) {
        super(name);
    }

    public static PlacedBlockTrackerData get(WorldServer level) {
        MapStorage storage = level.perWorldStorage;
        PlacedBlockTrackerData data = (PlacedBlockTrackerData) storage.loadData(
                PlacedBlockTrackerData.class, DATA_NAME);
        if (data == null) {
            data = new PlacedBlockTrackerData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void mark(BlockPos pos) {
        if (pos != null && placedPositions.add(pos.toLong())) markDirty();
    }

    public void clear(BlockPos pos) {
        if (pos != null && placedPositions.remove(pos.toLong())) markDirty();
    }

    public boolean isPlaced(BlockPos pos) {
        return pos != null && placedPositions.contains(pos.toLong());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        placedPositions.clear();
        String key = tag.hasKey(KEY_PLACED) ? KEY_PLACED : LEGACY_KEY_PLACED;
        for (long value : NbtCompat.getLongArray(tag, key)) placedPositions.add(value);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        long[] encoded = new long[placedPositions.size()];
        int index = 0;
        for (Long value : placedPositions) encoded[index++] = value.longValue();
        NbtCompat.setLongArray(tag, KEY_PLACED, encoded);
        tag.removeTag(LEGACY_KEY_PLACED);
    }
}
