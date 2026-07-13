package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.state.PendingItemIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/**
 * 远程放置与已放置方块回收状态。
 *
 * <p>这里只保存队列本身，不负责消耗材料、回滚失败作业或写历史。服务端 tick
 * 处理器读取这里的队列执行实际业务。</p>
 */
public class RtsPlacementState {

    /** 待处理的放置批次作业。 */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> placeBatchJobs = new ArrayDeque<>();
    /** 因材料不足或冲突而挂起、等待玩家恢复的放置作业。 */
    public final Deque<RtsPlacementBatch.PlaceBatchJob> pendingJobs = new ArrayDeque<>();
    private final PendingItemIndex<RtsPlacementBatch.PlaceBatchJob> pendingJobsByItem = new PendingItemIndex<>();

    /** 添加挂起作业，并同步维护按物品 ID 查询的索引。 */
    public void addPendingJob(RtsPlacementBatch.PlaceBatchJob job) {
        if (job == null) return;
        pendingJobs.addLast(job);
        pendingJobsByItem.add(job.itemId(), job);
    }

    /** 删除挂起作业，并同步移除索引，避免后续错误唤醒。 */
    public boolean removePendingJob(RtsPlacementBatch.PlaceBatchJob job) {
        if (job == null || !pendingJobs.remove(job)) return false;
        pendingJobsByItem.remove(job.itemId(), job);
        return true;
    }

    /** 取出队首挂起作业，并同步移除索引。 */
    public RtsPlacementBatch.PlaceBatchJob removeFirstPendingJob() {
        RtsPlacementBatch.PlaceBatchJob job = pendingJobs.pollFirst();
        if (job != null) pendingJobsByItem.remove(job.itemId(), job);
        return job;
    }

    /** 返回与本次发生变化的物品相关的挂起作业快照。 */
    public List<RtsPlacementBatch.PlaceBatchJob> pendingJobsForItems(Collection<String> itemIds) {
        return pendingJobsByItem.valuesFor(itemIds);
    }

    /** 清空挂起队列及其查询索引。 */
    public void clearPendingJobs() {
        pendingJobs.clear();
        pendingJobsByItem.clear();
    }
    /** 已放置方块被破坏后的掉落物回收作业。 */
    public final Deque<PlacedRecoveryJob> recoveryJobs = new ArrayDeque<>();

    /**
     * 已放置方块被破坏后的掉落物回收作业。
     *
     * @param targetPos 原始方块坐标
     * @param stacks    待回收的掉落物堆栈
     */
    public record PlacedRecoveryJob(BlockPos targetPos, Deque<ItemStack> stacks) {}
}
