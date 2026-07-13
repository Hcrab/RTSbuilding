package com.rtsbuilding.rtsbuilding.server.storage.state;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * 远程放置与已放置方块回收的可变状态容器。
 *
 * <p>从 RtsStorageSession 提取，按 "玩家如何执行远程放置和回收"
 * 的职责聚合。包含放置批次作业队列和已放置方块被破坏后的掉落物回收队列。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li><b>状态容器</b>——业务判断仍在服务层；这里只维护队列与其物品 ID 索引的一致性</li>
 *   <li><b>可独立实例化</b>——便于测试放置状态切换而无需完整 session</li>
 * </ul>
 */
public class RtsPlacementState {

    // ======================================================================
    // 放置队列
    //      尚未执行的方块放置批次。
    //      PlaceBatchJob 类型定义在 RtsPlacementBatch 中。
    // ======================================================================

    /** 待处理的放置批次作业队列 */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();

    /**
     * 因物品不足而被挂起的放置作业队列。
     * 库存满足条件后可通过 {@code RtsPendingPlacementService.resumeAllPendingJobs}
     * 将其移回 placeBatchJobs 继续执行。
     */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> pendingJobs = new ArrayDeque<>();
    private final PendingItemIndex<RtsPlacementBatch.PlaceBatchJob> pendingJobsByItem = new PendingItemIndex<>();

    public void addPendingJob(RtsPlacementBatch.PlaceBatchJob job) {
        if (job == null) return;
        pendingJobs.addLast(job);
        pendingJobsByItem.add(job.itemId(), job);
    }

    public boolean removePendingJob(RtsPlacementBatch.PlaceBatchJob job) {
        if (job == null || !pendingJobs.remove(job)) return false;
        pendingJobsByItem.remove(job.itemId(), job);
        return true;
    }

    public RtsPlacementBatch.PlaceBatchJob removeFirstPendingJob() {
        RtsPlacementBatch.PlaceBatchJob job = pendingJobs.pollFirst();
        if (job != null) pendingJobsByItem.remove(job.itemId(), job);
        return job;
    }

    public List<RtsPlacementBatch.PlaceBatchJob> pendingJobsForItems(Collection<String> itemIds) {
        return pendingJobsByItem.valuesFor(itemIds);
    }

    public void clearPendingJobs() {
        pendingJobs.clear();
        pendingJobsByItem.clear();
    }

    /** 已放置方块被破坏后的掉落物回收作业队列 */
    public final Deque<PlacedRecoveryJob> recoveryJobs = new ArrayDeque<>();

    /**
     * 已放置方块被破坏后的掉落物回收作业。
     *
     * @param targetPos 原始方块坐标
     * @param stacks    待回收的掉落物堆栈队列
     */
    public record PlacedRecoveryJob(BlockPos targetPos, Deque<ItemStack> stacks) {}
}
