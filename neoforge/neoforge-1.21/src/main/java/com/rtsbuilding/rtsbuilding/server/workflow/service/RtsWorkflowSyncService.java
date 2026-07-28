package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class RtsWorkflowSyncService {

    private static final int MAX_WORKFLOWS = 8;

    public void notifyPlayer(ServerPlayer player, RtsWorkflowSlotManager slots) {
        if (player == null || slots == null) return;

        int totalCount = slots.occupiedCount();
        byte totalCountByte = (byte) Math.min(totalCount, 255);

        if (totalCount == 0) {
            PacketDistributor.sendToPlayer(player, S2CRtsWorkflowProgressPayload.idle());
            return;
        }

        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(totalCount);
        int entryCount = Math.min(slots.size(), MAX_WORKFLOWS);
        for (int i = 0; i < entryCount; i++) {
            RtsWorkflowEntry entry = slots.getEntry(i);
            if (entry == null || !entry.isOccupied()) continue;

            RtsWorkflowStatus status = entry.snapshot();
            entries.add(new S2CRtsWorkflowProgressPayload(
                    (byte) i,
                    totalCountByte,
                    status.type() != null ? (byte) status.type().ordinal() : (byte) -1,
                    (byte) status.priority().rank(),
                    status.totalBlocks(),
                    status.completedBlocks(),
                    status.failedBlocks(),
                    status.missingItems(),
                    status.detailMessage(),
                    (byte) (status.onHold() ? 1 : 0),
                    entry.id()));
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsWorkflowProgressBatchPayload(entries));
    }

    public void notifyCompletion(ServerPlayer player, RtsWorkflowSlotManager slots,
                                  RtsWorkflowEntry entry, int removedAtIndex) {
        if (player == null || entry == null) return;

        RtsWorkflowStatus status = entry.snapshot();
        int remainingCount = slots.occupiedCount() - 1;
        PacketDistributor.sendToPlayer(player, new S2CRtsWorkflowProgressPayload(
                (byte) removedAtIndex,
                (byte) remainingCount,
                status.type() != null ? (byte) status.type().ordinal() : (byte) -1,
                (byte) status.priority().rank(),
                status.totalBlocks(),
                status.completedBlocks(),
                status.failedBlocks(),
                status.missingItems(),
                status.detailMessage(),
                (byte) 0,
                entry.id()));

        notifyPlayer(player, slots);
    }

    public void sendIdle(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, S2CRtsWorkflowProgressPayload.idle());
        }
    }
}
