package com.rtsbuilding.rtsbuilding.server.data;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/** 记录由 RTS 放置的方块位置；数据按维度存入 1.12.2 的 per-world MapStorage。 */
public final class PlacedBlockTrackerData extends WorldSavedData {
    private static final String DATA_NAME = "rtsbuilding_placed_blocks";
    private static final String KEY_PLACED = "placed";
    private static final String LEGACY_KEY_PLACED = "placed_positions";

    private final LongOpenHashSet placedPositions = new LongOpenHashSet();

    public PlacedBlockTrackerData() {
        this(DATA_NAME);
    }

    /** MapStorage 反射加载所需的 String 构造器。 */
    public PlacedBlockTrackerData(String name) {
        super(name);
    }

    public static PlacedBlockTrackerData get(WorldServer level) {
        MapStorage storage = level.getPerWorldStorage();
        PlacedBlockTrackerData data = (PlacedBlockTrackerData) storage.getOrLoadData(
                PlacedBlockTrackerData.class, DATA_NAME);
        if (data == null) {
            data = new PlacedBlockTrackerData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public void mark(BlockPos pos) {
        if (placedPositions.add(pos.toLong())) {
            markDirty();
        }
    }

    public void clear(BlockPos pos) {
        if (placedPositions.remove(pos.toLong())) {
            markDirty();
        }
    }

    public boolean isPlaced(BlockPos pos) {
        return placedPositions.contains(pos.toLong());
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        placedPositions.clear();
        String key = tag.hasKey(KEY_PLACED, Constants.NBT.TAG_LIST)
                ? KEY_PLACED : LEGACY_KEY_PLACED;
        for (long value : readLongList(tag, key)) {
            placedPositions.add(value);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        NBTTagList encoded = new NBTTagList();
        for (long value : placedPositions.toLongArray()) {
            encoded.appendTag(new NBTTagLong(value));
        }
        tag.setTag(KEY_PLACED, encoded);
        tag.removeTag(LEGACY_KEY_PLACED);
        return tag;
    }

    /** 缺失键或非 long 列表按空集合处理；列表内部类型异常则显式拒绝损坏数据。 */
    private static long[] readLongList(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key, Constants.NBT.TAG_LIST)) return new long[0];
        NBTTagList encoded = (NBTTagList) tag.getTag(key);
        long[] values = new long[encoded.tagCount()];
        for (int i = 0; i < values.length; i++) {
            NBTBase element = encoded.get(i);
            if (!(element instanceof NBTTagLong)) {
                throw new IllegalArgumentException("方块位置列表包含非 long 元素: " + key);
            }
            values[i] = ((NBTTagLong) element).getLong();
        }
        return values;
    }
}
