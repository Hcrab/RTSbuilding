package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 挂起放置作业的材料和冲突扫描结果。 */
public final class S2CRtsResumePlacementScanPayload implements IMessage {
    private static final int MAX_ITEM_ID_CHARS = 128;
    private static final int MAX_LABEL_CHARS = 256;
    private static final int MAX_COUNT = 100_000_000;
    private String itemId = "";
    private String itemLabel = "";
    private int totalRemaining;
    private int alreadyPlacedCount;
    private int conflictCount;
    private long availableItems;
    private int neededItems;
    private long missingItems;
    private int workflowEntryId;

    public S2CRtsResumePlacementScanPayload() {}
    public S2CRtsResumePlacementScanPayload(String itemId, String itemLabel, int totalRemaining,
                                            int alreadyPlacedCount, int conflictCount, long availableItems,
                                            int neededItems, long missingItems, int workflowEntryId) {
        this.itemId = boundedText(itemId, MAX_ITEM_ID_CHARS, "item id");
        this.itemLabel = boundedText(itemLabel, MAX_LABEL_CHARS, "item label");
        this.totalRemaining = boundedCount(totalRemaining, "total remaining");
        this.alreadyPlacedCount = boundedCount(alreadyPlacedCount, "already placed count");
        this.conflictCount = boundedCount(conflictCount, "conflict count");
        this.availableItems = boundedLong(availableItems, "available items");
        this.neededItems = boundedCount(neededItems, "needed items");
        this.missingItems = boundedLong(missingItems, "missing items");
        this.workflowEntryId = boundedEntryId(workflowEntryId);
    }
    public String itemId() { return this.itemId; }
    public String itemLabel() { return this.itemLabel; }
    public int totalRemaining() { return this.totalRemaining; }
    public int alreadyPlacedCount() { return this.alreadyPlacedCount; }
    public int conflictCount() { return this.conflictCount; }
    public long availableItems() { return this.availableItems; }
    public int neededItems() { return this.neededItems; }
    public long missingItems() { return this.missingItems; }
    public int workflowEntryId() { return this.workflowEntryId; }
    @Override public void fromBytes(ByteBuf buffer) {
        this.itemId = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "item id");
        this.itemLabel = RtsPacketBuffer.readString(buffer, MAX_LABEL_CHARS, "item label");
        this.totalRemaining = RtsPacketBuffer.readBoundedCount(buffer, MAX_COUNT, "total remaining");
        this.alreadyPlacedCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_COUNT, "already placed count");
        this.conflictCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_COUNT, "conflict count");
        this.availableItems = boundedLong(buffer.readLong(), "available items");
        this.neededItems = RtsPacketBuffer.readBoundedCount(buffer, MAX_COUNT, "needed items");
        this.missingItems = boundedLong(buffer.readLong(), "missing items");
        this.workflowEntryId = boundedEntryId(buffer.readInt());
    }
    @Override public void toBytes(ByteBuf buffer) {
        RtsPacketBuffer.writeString(buffer, boundedText(this.itemId, MAX_ITEM_ID_CHARS, "item id"),
                MAX_ITEM_ID_CHARS, "item id");
        RtsPacketBuffer.writeString(buffer, boundedText(this.itemLabel, MAX_LABEL_CHARS, "item label"),
                MAX_LABEL_CHARS, "item label");
        RtsPacketBuffer.writeVarInt(buffer, boundedCount(this.totalRemaining, "total remaining"));
        RtsPacketBuffer.writeVarInt(buffer, boundedCount(this.alreadyPlacedCount, "already placed count"));
        RtsPacketBuffer.writeVarInt(buffer, boundedCount(this.conflictCount, "conflict count"));
        buffer.writeLong(boundedLong(this.availableItems, "available items"));
        RtsPacketBuffer.writeVarInt(buffer, boundedCount(this.neededItems, "needed items"));
        buffer.writeLong(boundedLong(this.missingItems, "missing items"));
        buffer.writeInt(boundedEntryId(this.workflowEntryId));
    }
    private static String boundedText(String value, int maximum, String name) {
        String safe = value == null ? "" : value;
        if (safe.length() > maximum) throw new IllegalArgumentException(name + " is too long");
        return safe;
    }
    private static int boundedCount(int value, String name) {
        if (value < 0 || value > MAX_COUNT) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
    private static long boundedLong(long value, String name) {
        if (value < 0L) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
    private static int boundedEntryId(int value) {
        if (value < 0) throw new IllegalArgumentException("workflow entry id out of range: " + value);
        return value;
    }
}
