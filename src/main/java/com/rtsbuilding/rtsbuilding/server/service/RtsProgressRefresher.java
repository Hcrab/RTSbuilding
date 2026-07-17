package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 放置与蓝图进度刷新服务——管理游戏内放置作业和蓝图工作流的进度检测。
 *
 * <p>从 {@link RtsPendingPlacementService} 提取的进度刷新职责，
 * 包括扫描活跃/挂起放置作业的世界实际方块状态、刷新工作流进度条、
 * 以及蓝图方块的被挖后恢复检测。</p>
 *
 * <p>蓝图进度扫描使用每玩家节流（每 20 tick 最多一次），
 * 避免每 tick O(n) 世界查询造成的性能开销。</p>
 */
public final class RtsProgressRefresher {

    /**
     * 蓝图进度刷新节流：每玩家记录上次刷新时的 tick 计数。
     */
    private static final Map<UUID, Long> BLUEPRINT_REFRESH_TICK = new HashMap<>();

    /** 蓝图进度刷新节流间隔（tick 数）。 */
    private static final long BLUEPRINT_REFRESH_INTERVAL = 20;

    private RtsProgressRefresher() {
    }

    /**
     * 清除蓝图刷新节流缓存，防止玩家断线后内存泄漏。
     */
    public static void clearPlayerCache(UUID playerUuid) {
        if (playerUuid != null) {
            BLUEPRINT_REFRESH_TICK.remove(playerUuid);
        }
    }

    /**
     * 刷新放置与蓝图工作流的进度。
     *
     * <p>遍历所有 job（先 pending 再 active），逐个检测实际放置情况。
     * 蓝图工作流部分使用节流控制（每 20 tick 最多扫描一次）。</p>
     */
    public static void refreshWorkflowProgress(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;

        // ── 蓝图工作流进度刷新（节流） ──────────────────────────
        refreshBlueprintProgress(player);
    }

    // ======================================================================
    //  范围放置进度
    // ======================================================================

    /**
     * 遍历所有放置作业，扫描世界中的实际已放置方块数，更新工作流进度。
     */
    private static void refreshBlueprintProgress(ServerPlayer player) {
        UUID puid = player.getUUID();
        long currentTick = player.serverLevel().getGameTime();
        Long lastRefresh = BLUEPRINT_REFRESH_TICK.get(puid);
        boolean shouldScan = lastRefresh == null || (currentTick - lastRefresh) >= BLUEPRINT_REFRESH_INTERVAL;
        if (!shouldScan) return;
        BLUEPRINT_REFRESH_TICK.put(puid, currentTick);

        var engine = RtsWorkflowEngine.getInstance();
        for (var status : engine.getAllProgress(player)) {
            if (!status.isActive() || status.type() != RtsWorkflowType.BLUEPRINT_BUILD) continue;
            int entryId = status.entryId();
            PipelineContext pipeCtx = com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                    .findBlueprintContext(player, entryId);
            if (!(pipeCtx instanceof BlueprintContext bctx)) continue;

            List<BlockPlacementPlanner.PlacementPlan> plans = bctx.getPlacementPlans();
            LinkedList<Integer> remaining = bctx.getRemainingQueue();
            if (plans == null || remaining == null || plans.isEmpty()) continue;

            ServerLevel level = player.serverLevel();
            int total = plans.size();
            Set<Integer> remainingSet = new HashSet<>(remaining);
            LinkedList<Integer> backToQueue = new LinkedList<>();
            int actualPlaced = 0;

            for (int idx = 0; idx < total; idx++) {
                BlockPlacementPlanner.PlacementPlan plan = plans.get(idx);
                if (plan == null) continue;
                if (remainingSet.contains(idx)) continue;
                if (!level.hasChunkAt(plan.target())) continue;

                BlockState current = level.getBlockState(plan.target());
                if (current.getBlock() == plan.state().getBlock()) {
                    actualPlaced++;
                } else {
                    backToQueue.add(idx);
                }
            }

            remaining.addAll(backToQueue);
            remaining.removeIf(idx -> {
                BlockPlacementPlanner.PlacementPlan plan = plans.get(idx);
                if (plan == null) return false;
                if (!level.hasChunkAt(plan.target())) return false;
                return level.getBlockState(plan.target()).getBlock() == plan.state().getBlock();
            });

            bctx.setPlacedCount(actualPlaced);
            bctx.setRemainingQueue(remaining);
            if (!com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                    .isDurableBlueprintContext(bctx)) {
                BlueprintPersistence.saveToEntry(player, entryId, bctx);
            }
            int refreshPlacedCount = actualPlaced;
            engine.from(player, entryId).ifPresent(token -> token.setCompletedBlocks(refreshPlacedCount));
        }
    }

    // ======================================================================
    //  共享辅助方法
    // ======================================================================

    /**
     * 统计玩家主背包中与模板匹配的物品总量。
     */
    public static long countItemsInPlayerInventory(ServerPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) return 0;
        boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player,
                ServiceRegistry.getInstance().session().getIfPresent(player));
        if (!includePlayerInventory) return 0;

        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        long count = 0;
        for (int slot = start; slot < end; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                count = RtsCountUtil.saturatedAdd(count, stack.getCount());
            }
        }
        return count;
    }
}
