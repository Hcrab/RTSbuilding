package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端返回玩家自己某个蓝图任务的材料扫描结果。 */
public final class S2CRtsBlueprintResumeScanPayload implements IMessage {
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_ITEM_ID_CHARS = 256;
    public static final int MAX_ITEM_LABEL_CHARS = 512;

    private List<String> itemIds = Collections.emptyList();
    private List<String> itemLabels = Collections.emptyList();
    private List<Integer> required = Collections.emptyList();
    private List<Long> available = Collections.emptyList();
    private int workflowEntryId;
    private int completedCount;
    private int totalCount;

    public S2CRtsBlueprintResumeScanPayload() {
    }

    public S2CRtsBlueprintResumeScanPayload(List<String> itemIds, List<String> itemLabels,
            List<Integer> required, List<Long> available, int workflowEntryId,
            int completedCount, int totalCount) {
        this.itemIds = immutableCopy(itemIds);
        this.itemLabels = immutableCopy(itemLabels);
        this.required = immutableCopy(required);
        this.available = immutableCopy(available);
        this.workflowEntryId = workflowEntryId;
        this.completedCount = completedCount;
        this.totalCount = totalCount;
        validate();
    }

    public List<String> itemIds() { return itemIds; }
    public List<String> itemLabels() { return itemLabels; }
    public List<Integer> required() { return required; }
    public List<Long> available() { return available; }
    public int workflowEntryId() { return workflowEntryId; }
    public int completedCount() { return completedCount; }
    public int totalCount() { return totalCount; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        int size = RtsPacketBuffer.readBoundedCount(buffer, MAX_ENTRIES, "blueprint material entries");
        List<String> decodedIds = new ArrayList<String>(size);
        List<String> decodedLabels = new ArrayList<String>(size);
        List<Integer> decodedRequired = new ArrayList<Integer>(size);
        List<Long> decodedAvailable = new ArrayList<Long>(size);
        for (int i = 0; i < size; i++) {
            decodedIds.add(RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "blueprint item id"));
            decodedLabels.add(RtsPacketBuffer.readString(buffer, MAX_ITEM_LABEL_CHARS, "blueprint item label"));
            decodedRequired.add(RtsPacketBuffer.readVarInt(buffer));
            decodedAvailable.add(buffer.readLong());
        }
        itemIds = Collections.unmodifiableList(decodedIds);
        itemLabels = Collections.unmodifiableList(decodedLabels);
        required = Collections.unmodifiableList(decodedRequired);
        available = Collections.unmodifiableList(decodedAvailable);
        workflowEntryId = RtsPacketBuffer.readVarInt(buffer);
        completedCount = RtsPacketBuffer.readVarInt(buffer);
        totalCount = RtsPacketBuffer.readVarInt(buffer);
        validate();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        RtsPacketBuffer.writeVarInt(buffer, itemIds.size());
        for (int i = 0; i < itemIds.size(); i++) {
            RtsPacketBuffer.writeString(buffer, itemIds.get(i), MAX_ITEM_ID_CHARS, "blueprint item id");
            RtsPacketBuffer.writeString(buffer, itemLabels.get(i), MAX_ITEM_LABEL_CHARS, "blueprint item label");
            RtsPacketBuffer.writeVarInt(buffer, required.get(i));
            buffer.writeLong(available.get(i));
        }
        RtsPacketBuffer.writeVarInt(buffer, workflowEntryId);
        RtsPacketBuffer.writeVarInt(buffer, completedCount);
        RtsPacketBuffer.writeVarInt(buffer, totalCount);
    }

    private void validate() {
        int size = itemIds.size();
        if (size > MAX_ENTRIES || itemLabels.size() != size || required.size() != size || available.size() != size) {
            throw new IllegalArgumentException("Blueprint material lists must be parallel and bounded");
        }
        if (workflowEntryId < 0 || completedCount < 0 || totalCount < completedCount) {
            throw new IllegalArgumentException("Invalid blueprint workflow progress");
        }
        for (int i = 0; i < size; i++) {
            if (itemIds.get(i) == null || itemIds.get(i).length() > MAX_ITEM_ID_CHARS
                    || itemLabels.get(i) == null || itemLabels.get(i).length() > MAX_ITEM_LABEL_CHARS
                    || required.get(i) == null || required.get(i) < 0
                    || available.get(i) == null || available.get(i) < 0L) {
                throw new IllegalArgumentException("Invalid blueprint material entry at index " + i);
            }
        }
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        if (source == null) throw new IllegalArgumentException("Blueprint material list must not be null");
        return Collections.unmodifiableList(new ArrayList<T>(source));
    }
}
