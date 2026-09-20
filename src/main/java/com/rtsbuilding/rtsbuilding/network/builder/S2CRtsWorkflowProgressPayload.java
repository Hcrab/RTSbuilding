package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 单个工作流槽位的服务端到客户端同步负载。 */
public record S2CRtsWorkflowProgressPayload(
        int workflowIndex,
        int workflowCount,
        byte workflowType,
        byte priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        List<String> missingItems,
        String detailMessage,
        byte suspended,
        byte paused,
        byte protectedWorkflow,
        int workflowEntryId,
        int reasonId) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "s2c_rts_workflow_progress"),
            S2CRtsWorkflowProgressPayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressPayload> STREAM_CODEC =
            StreamCodec.of(S2CRtsWorkflowProgressPayload::encode, S2CRtsWorkflowProgressPayload::decode);

    /** 兼容旧调用方；旧负载没有原因字段，按 UNKNOWN 处理。 */
    public S2CRtsWorkflowProgressPayload(
            byte workflowIndex, byte workflowCount, byte workflowType, byte priority,
            int totalBlocks, int completedBlocks, int failedBlocks, List<String> missingItems,
            String detailMessage, byte suspended, byte paused, byte protectedWorkflow, int workflowEntryId) {
        this(workflowIndex, workflowCount, legacyWorkflowTypeWireId(workflowType), priority, totalBlocks, completedBlocks,
                failedBlocks, missingItems, detailMessage, suspended, paused, protectedWorkflow,
                workflowEntryId, 0);
    }

    /** 旧 Java 调用方传入的是 enum ordinal；只在兼容构造器中转换为稳定 wire ID。 */
    private static byte legacyWorkflowTypeWireId(byte ordinal) {
        int index = ordinal;
        RtsWorkflowType[] values = RtsWorkflowType.values();
        return index >= 0 && index < values.length ? (byte) values[index].wireId() : ordinal;
    }

    public S2CRtsWorkflowProgressPayload {
        if (workflowIndex < -1 || workflowIndex > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT) {
            throw new IllegalArgumentException("workflow index 超出协议预算");
        }
        if (workflowCount < 0 || workflowCount > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT) {
            throw new IllegalArgumentException("workflow count 超出协议预算");
        }
        List<String> safeItems = missingItems == null ? List.of() : missingItems;
        if (safeItems.size() > RtsWorkflowWireLimits.MAX_MISSING_ITEMS) {
            throw new IllegalArgumentException("missing item 数量超出协议预算");
        }
        List<String> copied = new ArrayList<>(safeItems.size());
        for (String item : safeItems) {
            String value = item == null ? "" : item;
            if (value.length() > RtsWorkflowWireLimits.MAX_ITEM_ID_CHARS) {
                throw new IllegalArgumentException("missing item 字符串超出协议预算");
            }
            copied.add(value);
        }
        missingItems = List.copyOf(copied);
        detailMessage = detailMessage == null ? "" : detailMessage;
        if (detailMessage.length() > RtsWorkflowWireLimits.MAX_DETAIL_CHARS) {
            throw new IllegalArgumentException("workflow detail 超出协议预算");
        }
    }

    static void writeFields(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressPayload payload) {
        buf.writeVarInt(payload.workflowIndex());
        buf.writeVarInt(payload.workflowCount());
        buf.writeByte(payload.workflowType());
        buf.writeByte(payload.priority());
        buf.writeInt(payload.totalBlocks());
        buf.writeInt(payload.completedBlocks());
        buf.writeInt(payload.failedBlocks());
        buf.writeVarInt(payload.reasonId());
        buf.writeByte(payload.suspended());
        buf.writeByte(payload.paused());
        buf.writeByte(payload.protectedWorkflow());
        buf.writeInt(payload.workflowEntryId());
        buf.writeVarInt(payload.missingItems().size());
        for (String item : payload.missingItems()) buf.writeUtf(item);
        buf.writeUtf(payload.detailMessage());
    }

    static S2CRtsWorkflowProgressPayload readFields(RegistryFriendlyByteBuf buf) {
        int workflowIndex = buf.readVarInt();
        int workflowCount = buf.readVarInt();
        if (workflowIndex < -1 || workflowIndex > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT
                || workflowCount < 0 || workflowCount > RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT) {
            throw new IllegalArgumentException("workflow index/count 超出协议预算");
        }
        byte workflowType = buf.readByte();
        byte priority = buf.readByte();
        int totalBlocks = buf.readInt();
        int completedBlocks = buf.readInt();
        int failedBlocks = buf.readInt();
        int reasonId = buf.readVarInt();
        byte suspended = buf.readByte();
        byte paused = buf.readByte();
        byte protectedWorkflow = buf.readByte();
        int workflowEntryId = buf.readInt();
        int missingCount = buf.readVarInt();
        if (missingCount < 0 || missingCount > RtsWorkflowWireLimits.MAX_MISSING_ITEMS) {
            throw new IllegalArgumentException("missing item 数量超出协议预算");
        }
        List<String> missingItems = new ArrayList<>(missingCount);
        for (int i = 0; i < missingCount; i++) {
            missingItems.add(buf.readUtf(RtsWorkflowWireLimits.MAX_ITEM_ID_CHARS));
        }
        String detailMessage = buf.readUtf(RtsWorkflowWireLimits.MAX_DETAIL_CHARS);
        return new S2CRtsWorkflowProgressPayload(workflowIndex, workflowCount, workflowType, priority,
                totalBlocks, completedBlocks, failedBlocks, missingItems, detailMessage, suspended,
                paused, protectedWorkflow, workflowEntryId, reasonId);
    }

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressPayload payload) {
        writeFields(buf, payload);
    }

    private static S2CRtsWorkflowProgressPayload decode(RegistryFriendlyByteBuf buf) {
        return readFields(buf);
    }

    public static S2CRtsWorkflowProgressPayload idle() {
        return new S2CRtsWorkflowProgressPayload(-1, 0, (byte) -1, (byte) 1,
                0, 0, 0, List.of(), "", (byte) 0, (byte) 0, (byte) 0, -1, 0);
    }

    public boolean isIdle() {
        return workflowIndex < 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
