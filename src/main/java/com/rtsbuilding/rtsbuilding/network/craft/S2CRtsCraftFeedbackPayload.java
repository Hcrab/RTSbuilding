package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class S2CRtsCraftFeedbackPayload implements IMessage {
    private static final int MAX_ITEM_ID_CHARS = 128;
    private static final int MAX_CONSUMED_ENTRIES = 512;
    private static final int MAX_ITEM_COUNT = 1_000_000;

    private String itemId = "";
    private int craftedCount;
    private List<String> consumedItemIds = Collections.emptyList();
    private List<Integer> consumedCounts = Collections.emptyList();

    public S2CRtsCraftFeedbackPayload() {
    }
    public S2CRtsCraftFeedbackPayload(String itemId, int craftedCount,
                                      List<String> consumedItemIds,
                                      List<Integer> consumedCounts) {
        this.itemId = itemId == null ? "" : itemId;
        this.craftedCount = craftedCount;
        this.consumedItemIds = immutable(consumedItemIds);
        this.consumedCounts = immutable(consumedCounts);
    }
    public String itemId() { return itemId; }
    public int craftedCount() { return craftedCount; }
    public List<String> consumedItemIds() { return consumedItemIds; }
    public List<Integer> consumedCounts() { return consumedCounts; }

    @Override public void fromBytes(ByteBuf buffer) {
        itemId = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "crafted item id");
        craftedCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_ITEM_COUNT, "crafted count");
        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_CONSUMED_ENTRIES,
                "consumed ingredient count");
        List<String> ids = new ArrayList<>(size);
        List<Integer> counts = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "consumed item id"));
            counts.add(RtsPacketBuffer.readBoundedCount(buffer, MAX_ITEM_COUNT,
                    "consumed item count"));
        }
        consumedItemIds = immutable(ids);
        consumedCounts = immutable(counts);
    }

    @Override public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeString(buffer, itemId, MAX_ITEM_ID_CHARS, "crafted item id");
        RtsPacketBuffer.writeVarInt(buffer, bounded(craftedCount, "crafted count"));
        int size = Math.min(MAX_CONSUMED_ENTRIES,
                Math.min(consumedItemIds.size(), consumedCounts.size()));
        RtsPacketBuffer.writeVarInt(buffer, size);
        for (int i = 0; i < size; i++) {
            RtsPacketBuffer.writeString(buffer,
                    consumedItemIds.get(i) == null ? "" : consumedItemIds.get(i),
                    MAX_ITEM_ID_CHARS, "consumed item id");
            RtsPacketBuffer.writeVarInt(buffer,
                    bounded(consumedCounts.get(i) == null ? 0 : consumedCounts.get(i),
                            "consumed item count"));
        }
    }

    private static int bounded(int value, String name) {
        if (value < 0 || value > MAX_ITEM_COUNT) throw new IllegalArgumentException(name + " out of range");
        return value;
    }
    private static <T> List<T> immutable(List<T> values) {
        return values == null ? Collections.<T>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
