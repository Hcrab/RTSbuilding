package com.rtsbuilding.rtsbuilding.server.workflow.service;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEntry;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理工作流状态从服务端到客户端的网络同步。
 *
 * <p>本服务封装了所有 {@link S2CRtsWorkflowProgressPayload}
 * 创建和分发逻辑，引擎无需直接处理有线格式细节。</p>
 *
 * <p>对于负载使用位置索引（客户端期望的格式），
 * 但引擎始终通过不可变的条目 ID 来标识条目。</p>
 */
public final class RtsWorkflowSyncService {

    private static final int MAX_WORKFLOWS = 8;

    /**
     * 将所有已占用的工作流条目作为独立负载发送给客户端。
     *
     * @param player 要通知的服务端玩家
     * @param slots  该玩家的槽位管理器
     */
    public void notifyPlayer(ServerPlayer player, RtsWorkflowSlotManager slots) {
        if (player == null || slots == null) return;

        int totalCount = slots.occupiedCount();
        byte totalCountByte = (byte) Math.min(totalCount, 255);

        if (totalCount == 0) {
            PacketDistributor.sendToPlayer(player, S2CRtsWorkflowProgressPayload.idle());
            return;
        }

        // 收集所有条目，作为单个批次包发送
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
                    status.suspended() ? (byte) 1 : (byte) 0,
                    status.paused() ? (byte) 1 : (byte) 0,
                    entry.id()));
        }
        PacketDistributor.sendToPlayer(player, new S2CRtsWorkflowProgressBatchPayload(entries));
    }

    /**
     * 向客户端发送单个条目的最终快照，然后通知更新后的状态。
     * 在工作流完成时调用。
     */
    public void notifyCompletion(ServerPlayer player, RtsWorkflowSlotManager slots,
                                  RtsWorkflowEntry entry, int removedAtIndex) {
        if (player == null || entry == null) return;

        // 在移除前发送此条目的最终快照
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
                (byte) 0,
                entry.id()));

        // 如果还有剩余条目，通知更新后的状态
        notifyPlayer(player, slots);
    }

    /**
     * 向客户端发送空闲（无活动工作流）负载。
     */
    public void sendIdle(ServerPlayer player) {
        if (player != null) {
            PacketDistributor.sendToPlayer(player, S2CRtsWorkflowProgressPayload.idle());
        }
    }
}
