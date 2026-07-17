package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload that batches multiple workflow progress updates
 * into a single network packet, avoiding per-entry packet overhead.
 *
 * <p><b>Wire format:</b>
 * <ul>
 *   <li>{@code varInt count} — number of entries in this batch</li>
 *   <li>{@code (entry fields) × count} — each entry encoded exactly as
 *       {@link S2CRtsWorkflowProgressPayload}</li>
 * </ul>
 *
 * @param entries the list of workflow progress payloads to apply at once
 */
public record S2CRtsWorkflowProgressBatchPayload(
        List<S2CRtsWorkflowProgressPayload> entries) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressBatchPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_workflow_progress_batch"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsWorkflowProgressBatchPayload> STREAM_CODEC =
            StreamCodec.of(
                    S2CRtsWorkflowProgressBatchPayload::encode,
                    S2CRtsWorkflowProgressBatchPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CRtsWorkflowProgressBatchPayload payload) {
        List<S2CRtsWorkflowProgressPayload> entries = payload.entries();
        buf.writeInt(entries.size());
        for (S2CRtsWorkflowProgressPayload entry : entries) {
            buf.writeByte(entry.workflowIndex());
            buf.writeByte(entry.workflowCount());
            buf.writeByte(entry.workflowType());
            buf.writeByte(entry.priority());
            buf.writeInt(entry.totalBlocks());
            buf.writeInt(entry.completedBlocks());
            buf.writeInt(entry.failedBlocks());
            buf.writeByte(entry.suspended());
            buf.writeByte(entry.paused());
            buf.writeByte(entry.protectedWorkflow());
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
        int count = buf.readInt();
        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte workflowIndex = buf.readByte();
            byte workflowCount = buf.readByte();
            byte workflowType = buf.readByte();
            byte priority = buf.readByte();
            int totalBlocks = buf.readInt();
            int completedBlocks = buf.readInt();
            int failedBlocks = buf.readInt();
            byte suspended = buf.readByte();
            byte paused = buf.readByte();
            byte protectedWorkflow = buf.readByte();
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
                    missingItems, detailMessage, suspended, paused, protectedWorkflow, workflowEntryId));
        }
        return new S2CRtsWorkflowProgressBatchPayload(entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
