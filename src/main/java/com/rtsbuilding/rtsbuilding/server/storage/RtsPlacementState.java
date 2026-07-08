package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

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
