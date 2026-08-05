package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsWorkflowProgressBatchPayload(
        List<S2CRtsWorkflowProgressPayload> entries) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressBatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_workflow_progress_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressBatchPayload> STREAM_CODEC =
            StreamCodec.of(
                    S2CRtsWorkflowProgressBatchPayload::encode,
                    S2CRtsWorkflowProgressBatchPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressBatchPayload payload) {
        List<S2CRtsWorkflowProgressPayload> entries = payload.entries();
        buf.writeVarInt(entries.size());
        for (S2CRtsWorkflowProgressPayload entry : entries) {
            buf.writeByte(entry.workflowIndex());
            buf.writeByte(entry.workflowCount());
            buf.writeByte(entry.workflowType());
            buf.writeByte(entry.priority());
            buf.writeInt(entry.totalBlocks());
            buf.writeInt(entry.completedBlocks());
            buf.writeInt(entry.failedBlocks());
            buf.writeByte(entry.onHold());
            buf.writeInt(entry.workflowEntryId());
            List<String> items = entry.missingItems();
            buf.writeInt(items.size());
            for (String item : items) {
                buf.writeUtf(item);
            }
            buf.writeUtf(entry.detailMessage() != null ? entry.detailMessage() : "");
        }
    }

    private static S2CRtsWorkflowProgressBatchPayload decode(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
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
            for (int j = 0; j < missingCount; j++) {
                missingItems.add(buf.readUtf());
            }
            String detailMessage = buf.readUtf();
            entries.add(new S2CRtsWorkflowProgressPayload(
                    workflowIndex, workflowCount, workflowType, priority,
                    totalBlocks, completedBlocks, failedBlocks,
                    missingItems, detailMessage, onHold, workflowEntryId));
        }
        return new S2CRtsWorkflowProgressBatchPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
