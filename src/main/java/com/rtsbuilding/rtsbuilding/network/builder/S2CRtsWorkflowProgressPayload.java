package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 单个工作流槽位的服务端权威进度快照。 */
public final class S2CRtsWorkflowProgressPayload implements IMessage {
    public static final int MAX_MISSING_ITEMS = 512;
    public static final int MAX_ITEM_ID_CHARS = 128;
    public static final int MAX_DETAIL_CHARS = 1024;
    private static final int MAX_BLOCK_COUNT = 100_000_000;

    private byte workflowIndex = -1;
    private byte workflowCount;
    private byte workflowType = -1;
    private byte priority = 1;
    private int totalBlocks;
    private int completedBlocks;
    private int failedBlocks;
    private List<String> missingItems = Collections.emptyList();
    private String detailMessage = "";
    private byte suspended;
    private byte paused;
    private byte protectedWorkflow;
    private int workflowEntryId = -1;

    public S2CRtsWorkflowProgressPayload() {}

    public S2CRtsWorkflowProgressPayload(byte workflowIndex, byte workflowCount, byte workflowType,
                                         byte priority, int totalBlocks, int completedBlocks, int failedBlocks,
                                         List<String> missingItems, String detailMessage, byte suspended,
                                         byte paused, byte protectedWorkflow, int workflowEntryId) {
        this.workflowIndex = boundedIndex(workflowIndex);
        this.workflowCount = boundedCountByte(workflowCount, "workflow count");
        this.workflowType = boundedType(workflowType);
        this.priority = boundedPriority(priority);
        this.totalBlocks = boundedBlocks(totalBlocks, "total blocks");
        this.completedBlocks = boundedBlocks(completedBlocks, "completed blocks");
        this.failedBlocks = boundedBlocks(failedBlocks, "failed blocks");
        this.missingItems = immutableMissingItems(missingItems);
        this.detailMessage = boundedText(detailMessage, MAX_DETAIL_CHARS, "workflow detail");
        this.suspended = boundedFlag(suspended, "suspended");
        this.paused = boundedFlag(paused, "paused");
        this.protectedWorkflow = boundedFlag(protectedWorkflow, "protected workflow");
        this.workflowEntryId = boundedEntryId(workflowIndex, workflowEntryId);
    }

    public byte workflowIndex() { return this.workflowIndex; }
    public byte workflowCount() { return this.workflowCount; }
    public byte workflowType() { return this.workflowType; }
    public byte priority() { return this.priority; }
    public int totalBlocks() { return this.totalBlocks; }
    public int completedBlocks() { return this.completedBlocks; }
    public int failedBlocks() { return this.failedBlocks; }
    public List<String> missingItems() { return this.missingItems; }
    public String detailMessage() { return this.detailMessage; }
    public byte suspended() { return this.suspended; }
    public byte paused() { return this.paused; }
    public byte protectedWorkflow() { return this.protectedWorkflow; }
    public int workflowEntryId() { return this.workflowEntryId; }

    @Override public void fromBytes(ByteBuf buffer) { readFrom(buffer, this); }
    @Override public void toBytes(ByteBuf buffer) { writeTo(buffer, this); }

    static void writeTo(ByteBuf buffer, S2CRtsWorkflowProgressPayload payload) {
        if (payload == null) throw new IllegalArgumentException("workflow payload");
        buffer.writeByte(boundedIndex(payload.workflowIndex));
        buffer.writeByte(boundedCountByte(payload.workflowCount, "workflow count"));
        buffer.writeByte(boundedType(payload.workflowType));
        buffer.writeByte(boundedPriority(payload.priority));
        RtsPacketBuffer.writeVarInt(buffer, boundedBlocks(payload.totalBlocks, "total blocks"));
        RtsPacketBuffer.writeVarInt(buffer, boundedBlocks(payload.completedBlocks, "completed blocks"));
        RtsPacketBuffer.writeVarInt(buffer, boundedBlocks(payload.failedBlocks, "failed blocks"));
        buffer.writeByte(boundedFlag(payload.suspended, "suspended"));
        buffer.writeByte(boundedFlag(payload.paused, "paused"));
        buffer.writeByte(boundedFlag(payload.protectedWorkflow, "protected workflow"));
        buffer.writeInt(boundedEntryId(payload.workflowIndex, payload.workflowEntryId));
        List<String> items = immutableMissingItems(payload.missingItems);
        RtsPacketBuffer.writeVarInt(buffer, items.size());
        for (String item : items) {
            RtsPacketBuffer.writeString(buffer, item, MAX_ITEM_ID_CHARS, "missing item id");
        }
        RtsPacketBuffer.writeString(buffer,
                boundedText(payload.detailMessage, MAX_DETAIL_CHARS, "workflow detail"),
                MAX_DETAIL_CHARS, "workflow detail");
    }

    static S2CRtsWorkflowProgressPayload readNew(ByteBuf buffer) {
        S2CRtsWorkflowProgressPayload payload = new S2CRtsWorkflowProgressPayload();
        readFrom(buffer, payload);
        return payload;
    }

    private static void readFrom(ByteBuf buffer, S2CRtsWorkflowProgressPayload target) {
        target.workflowIndex = boundedIndex(buffer.readByte());
        target.workflowCount = boundedCountByte(buffer.readByte(), "workflow count");
        target.workflowType = boundedType(buffer.readByte());
        target.priority = boundedPriority(buffer.readByte());
        target.totalBlocks = RtsPacketBuffer.readBoundedCount(buffer, MAX_BLOCK_COUNT, "total blocks");
        target.completedBlocks = RtsPacketBuffer.readBoundedCount(buffer, MAX_BLOCK_COUNT, "completed blocks");
        target.failedBlocks = RtsPacketBuffer.readBoundedCount(buffer, MAX_BLOCK_COUNT, "failed blocks");
        target.suspended = boundedFlag(buffer.readByte(), "suspended");
        target.paused = boundedFlag(buffer.readByte(), "paused");
        target.protectedWorkflow = boundedFlag(buffer.readByte(), "protected workflow");
        target.workflowEntryId = boundedEntryId(target.workflowIndex, buffer.readInt());
        int missingCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_MISSING_ITEMS, "missing item count");
        List<String> items = new ArrayList<String>(missingCount);
        for (int i = 0; i < missingCount; i++) {
            items.add(RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "missing item id"));
        }
        target.missingItems = Collections.unmodifiableList(items);
        target.detailMessage = RtsPacketBuffer.readString(buffer, MAX_DETAIL_CHARS, "workflow detail");
    }

    public static S2CRtsWorkflowProgressPayload idle() {
        return new S2CRtsWorkflowProgressPayload((byte) -1, (byte) 0, (byte) -1, (byte) 1,
                0, 0, 0, Collections.<String>emptyList(), "", (byte) 0, (byte) 0, (byte) 0, -1);
    }

    public boolean isIdle() { return this.workflowIndex < 0; }

    private static byte boundedIndex(byte value) {
        if (value < -1) throw new IllegalArgumentException("workflow index out of range: " + value);
        return value;
    }
    private static byte boundedCountByte(byte value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
    private static byte boundedType(byte value) {
        if (value < -1) throw new IllegalArgumentException("workflow type out of range: " + value);
        return value;
    }
    private static byte boundedPriority(byte value) {
        if (value < 0 || value > 3) throw new IllegalArgumentException("priority out of range: " + value);
        return value;
    }
    private static byte boundedFlag(byte value, String name) {
        if (value != 0 && value != 1) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
    private static int boundedBlocks(int value, String name) {
        if (value < 0 || value > MAX_BLOCK_COUNT) throw new IllegalArgumentException(name + " out of range: " + value);
        return value;
    }
    private static int boundedEntryId(byte workflowIndex, int value) {
        if (workflowIndex < 0) {
            if (value != -1) throw new IllegalArgumentException("idle workflow entry id must be -1");
        } else if (value < 0) {
            throw new IllegalArgumentException("workflow entry id out of range: " + value);
        }
        return value;
    }
    private static String boundedText(String value, int maximum, String name) {
        String safe = value == null ? "" : value;
        if (safe.length() > maximum) throw new IllegalArgumentException(name + " is too long");
        return safe;
    }
    private static List<String> immutableMissingItems(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        if (values.size() > MAX_MISSING_ITEMS) throw new IllegalArgumentException("too many missing item ids");
        List<String> copy = new ArrayList<String>(values.size());
        for (String value : values) copy.add(boundedText(value, MAX_ITEM_ID_CHARS, "missing item id"));
        return Collections.unmodifiableList(copy);
    }
}
