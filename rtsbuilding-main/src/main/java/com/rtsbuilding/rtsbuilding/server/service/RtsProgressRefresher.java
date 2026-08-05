package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Placement and blueprint progress refresh service — manages progress detection for in-game placement jobs and blueprint workflows.
 *
 * <p>Progress refresh responsibilities extracted from {@link RtsPendingPlacementService},
 * including scanning world actual block states for active/pending placement jobs, refreshing workflow progress bars,
 * and recovery detection for mined blueprint blocks.</p>
 *
 * <p>Blueprint progress scanning uses per-player throttling (max once per 20 ticks),
 * avoiding the performance cost of O(n) world queries every tick.</p>
 */
public final class RtsProgressRefresher {

    /**
     * Blueprint progress refresh throttle: records the tick count of the last refresh per player.
     */
    private static final Map<UUID, Long> BLUEPRINT_REFRESH_TICK = new HashMap<>();

    /** Blueprint progress refresh throttle interval (ticks). */
    private static final long BLUEPRINT_REFRESH_INTERVAL = 20;

    private RtsProgressRefresher() {
    }

    /**
     * Clears blueprint refresh throttle cache, preventing memory leaks after player disconnect.
     */
    public static void clearPlayerCache(UUID playerUuid) {
        if (playerUuid != null) {
            BLUEPRINT_REFRESH_TICK.remove(playerUuid);
        }
    }

    /**
     * Refreshes placement and blueprint workflow progress.
     *
     * <p>Iterates over all jobs (pending first, then active), checking actual placement status one by one.
     * Blueprint workflow section uses throttling (at most once per 20 ticks).</p>
     */
    public static void refreshWorkflowProgress(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;

        // ── Area placement workflow progress refresh ──────────────────────────────
        refreshPlacementProgress(player, session);

        // ── Blueprint workflow progress refresh (throttled) ──────────────────────────
        refreshBlueprintProgress(player);
    }

    // ======================================================================
    //  Area placement progress
    // ======================================================================

    /**
     * Iterates over all placement jobs, scans the actual placed block count in the world, updates workflow progress.
     */
    private static void refreshPlacementProgress(ServerPlayer player, RtsStorageSession session) {
        List<RtsPlacementBatch.PlaceBatchJob> allJobs = new ArrayList<>();
        allJobs.addAll(session.placement.pendingJobs);
        allJobs.addAll(session.placement.placeBatchJobs);

        for (RtsPlacementBatch.PlaceBatchJob job : allJobs) {
            String itemId = job.itemId();
            if (itemId == null || itemId.isBlank()) continue;

            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            if (!(BuiltInRegistries.ITEM.get(id) instanceof BlockItem blockItem)) continue;
            Block expectedBlock = blockItem.getBlock();
            if (expectedBlock == net.minecraft.world.level.block.Blocks.AIR) continue;

            List<BlockPos> allPositions = new ArrayList<>(job.clickedPositions());
            Direction face = job.face();
            int actualPlaced = 0;
            for (BlockPos pos : allPositions) {
                boolean found = false;
                if (player.serverLevel().hasChunkAt(pos)) {
                    BlockState state = player.serverLevel().getBlockState(pos);
                    if (state.getBlock() == expectedBlock) {
                        found = true;
                    }
                }
                if (!found) {
                    BlockPos adjPos = pos.relative(face);
                    if (player.serverLevel().hasChunkAt(adjPos)) {
                        BlockState adjState = player.serverLevel().getBlockState(adjPos);
                        if (adjState.getBlock() == expectedBlock) {
                            found = true;
                        }
                    }
                }
                if (found) {
                    actualPlaced++;
                }
            }

            int finalActPlaced = actualPlaced;
            RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.setCompletedBlocks(finalActPlaced));
        }
    }

    // ======================================================================
    //  Blueprint progress (throttled)
    // ======================================================================

    /**
     * Scans placed but mined-out blueprint blocks, returns them to the queue for re-placement.
     * Throttle: only scans once every 20 ticks.
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
            PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, entryId);
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
            BlueprintPersistence.saveToEntry(player, entryId, bctx);
            int refreshPlacedCount = actualPlaced;
            engine.from(player, entryId).ifPresent(token -> token.setCompletedBlocks(refreshPlacedCount));
        }
    }

    // ======================================================================
    //  Shared helper methods
    // ======================================================================

    /**
     * Counts the total amount of template-matching items in the player's main inventory.
     */
    public static long countItemsInPlayerInventory(ServerPlayer player, ItemStack template) {
        if (player == null || template == null || template.isEmpty()) return 0;
        boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player,
                RtsServer.get().session().getIfPresent(player));
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
