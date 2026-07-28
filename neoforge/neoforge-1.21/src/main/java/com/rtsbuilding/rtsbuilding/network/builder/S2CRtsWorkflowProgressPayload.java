package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsWorkflowProgressPayload(
        byte workflowIndex,
        byte workflowCount,
        byte workflowType,
        byte priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        List<String> missingItems,
        String detailMessage,
        byte onHold,
        int workflowEntryId) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_workflow_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressPayload> STREAM_CODEC = StreamCodec.of(
            S2CRtsWorkflowProgressPayload::encode,
            S2CRtsWorkflowProgressPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressPayload payload) {
        buf.writeByte(payload.workflowIndex());
        buf.writeByte(payload.workflowCount());
        buf.writeByte(payload.workflowType());
        buf.writeByte(payload.priority());
        buf.writeInt(payload.totalBlocks());
        buf.writeInt(payload.completedBlocks());
        buf.writeInt(payload.failedBlocks());
        buf.writeByte(payload.onHold());
        buf.writeInt(payload.workflowEntryId());
        List<String> items = payload.missingItems();
        buf.writeInt(items.size());
        for (String item : items) {
            buf.writeUtf(item);
        }
        buf.writeUtf(payload.detailMessage() != null ? payload.detailMessage() : "");
    }

    private static S2CRtsWorkflowProgressPayload decode(RegistryFriendlyByteBuf buf) {
        byte workflowIndex = buf.readByte();
        byte workflowCount = buf.readByte();
        byte workflowType = buf.readByte();
        byte priority = buf.readByte();
        int totalBlocks = buf.readInt();
        int completedBlocks = buf.readInt();
        int failedBlocks = buf.readInt();
        byte onHold = buf.readByte();
        int workflowEntryId = buf.readInt();
        int missingCount = buf.readInt();
        List<String> missingItems = new ArrayList<>(missingCount);
        for (int i = 0; i < missingCount; i++) {
            missingItems.add(buf.readUtf());
        }
        String detailMessage = buf.readUtf();
        return new S2CRtsWorkflowProgressPayload(
                workflowIndex, workflowCount, workflowType, priority,
                totalBlocks, completedBlocks, failedBlocks,
                missingItems, detailMessage, onHold, workflowEntryId);
    }

    public static S2CRtsWorkflowProgressPayload idle() {
        return new S2CRtsWorkflowProgressPayload(
                (byte) -1, (byte) 0, (byte) -1, (byte) 1,
                0, 0, 0, List.of(), "", (byte) 0, -1);
    }

    public boolean isIdle() {
        return this.workflowIndex < 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
