package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端工作流状态同步出口。引擎不直接关心具体网络包字段。
 */
public final class RtsWorkflowSyncService {
    private static final int MAX_WORKFLOWS = 8;

    public void notifyPlayer(ServerPlayer player, RtsWorkflowSlotManager slots) {
        if (player == null || slots == null) {
            return;
        }
        int totalCount = slots.occupiedCount();
        if (totalCount <= 0) {
            sendIdle(player);
            return;
        }

        List<S2CRtsWorkflowProgressPayload> entries = new ArrayList<>(totalCount);
        int entryCount = Math.min(slots.size(), MAX_WORKFLOWS);
        byte totalCountByte = (byte) Math.min(totalCount, 255);
        for (int i = 0; i < entryCount; i++) {
            RtsWorkflowEntry entry = slots.getEntry(i);
            if (entry == null || !entry.isOccupied()) {
                continue;
            }
            entries.add(toPayload(i, totalCountByte, entry));
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsWorkflowProgressBatchPayload(entries));
    }

    public void sendIdle(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, S2CRtsWorkflowProgressPayload.idle());
        }
    }

    private static S2CRtsWorkflowProgressPayload toPayload(int index, byte totalCount, RtsWorkflowEntry entry) {
        RtsWorkflowStatus status = entry.snapshot();
        return new S2CRtsWorkflowProgressPayload(
                (byte) index,
                totalCount,
                status.type() == null ? (byte) -1 : (byte) status.type().ordinal(),
                (byte) status.priority().rank(),
                status.totalBlocks(),
                status.completedBlocks(),
                status.failedBlocks(),
                status.missingItems(),
                status.detailMessage(),
                status.suspended() ? (byte) 1 : (byte) 0,
                status.paused() ? (byte) 1 : (byte) 0,
                status.entryId());
    }
}
