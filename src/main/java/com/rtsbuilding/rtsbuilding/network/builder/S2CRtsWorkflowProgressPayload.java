package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-client payload for unified workflow progress updates.
 *
 * <p>Each payload carries progress for a single workflow slot, identified
 * by {@code workflowIndex} (0-based slot within the player's workflow list).
 * The {@code workflowCount} field tells the client the total number of
 * active workflow slots so it can size its UI accordingly.</p>
 *
 * <p><b>Wire format:</b>
 * <ul>
 *   <li>{@code byte workflowIndex} — 0-based slot index; -1 = idle/clear-all</li>
 *   <li>{@code byte workflowCount} — total active workflow count</li>
 *   <li>{@code byte workflowType} — workflow type ordinal; -1 = slot idle</li>
 *   <li>{@code byte priority} — priority rank (0-3)</li>
 *   <li>{@code int totalBlocks} — total blocks to process</li>
 *   <li>{@code int completedBlocks} — blocks successfully processed</li>
 *   <li>{@code int failedBlocks} — blocks that failed</li>
 *   <li>{@code int missingItemCount} — number of missing item IDs</li>
 *   <li>{@code String[] missingItems} — UTF-8 encoded item IDs</li>
 *   <li>{@code String detailMessage} — optional human-readable detail</li>
 * </ul>
 *
 * @param workflowIndex  0-based slot index; -1 = clear-all (no active workflows)
 * @param workflowCount  total number of active workflow slots
 * @param workflowType   workflow type ordinal; -1 = this slot idle
 * @param priority       priority rank (0 = LOW, 1 = NORMAL, 2 = HIGH, 3 = CRITICAL)
 * @param totalBlocks    total blocks to process (0 if unknown)
 * @param completedBlocks blocks successfully processed so far
 * @param failedBlocks   blocks that failed to process
 * @param missingItems   item IDs needed but unavailable; empty list if none
 * @param detailMessage  optional human-readable detail
 * @param suspended      1 if this workflow slot is suspended (waiting for items), 0 otherwise
 * @param paused         1 if this workflow slot is paused by the user, 0 otherwise
 * @param workflowEntryId immutable workflow entry ID for linking with pending jobs
 */
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
        byte suspended,
        byte paused,
        byte protectedWorkflow,
        int workflowEntryId) implements CustomPacketPayload {

    public static final Type<S2CRtsWorkflowProgressPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_workflow_progress"));

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
        buf.writeByte(payload.suspended());
        buf.writeByte(payload.paused());
        buf.writeByte(payload.protectedWorkflow());
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
        byte suspended = buf.readByte();
        byte paused = buf.readByte();
        byte protectedWorkflow = buf.readByte();
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
                missingItems, detailMessage, suspended, paused, protectedWorkflow, workflowEntryId);
    }

    /**
     * Creates a clear-all (no active workflows) payload.
     */
    public static S2CRtsWorkflowProgressPayload idle() {
        return new S2CRtsWorkflowProgressPayload(
                (byte) -1, (byte) 0, (byte) -1, (byte) 1,
                0, 0, 0, List.of(), "", (byte) 0, (byte) 0, (byte) 0, -1);
    }

    /**
     * Returns {@code true} if this payload indicates all workflows cleared.
     */
    public boolean isIdle() {
        return this.workflowIndex < 0;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
