package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.rule.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.common.blueprint.sanitize.BlueprintBlockEntitySanitizer;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickResult;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipe;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BlueprintService;
import com.rtsbuilding.rtsbuilding.server.service.placement.BlockPlacer;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Tickable pipe that processes blueprint block placement across server ticks,
 * completely aligned with the range placement pattern
 * ({@link com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch#tickPlaceBatchJobs}).
 *
 * <p>Each tick processes up to a dynamic limit of blocks from the queue.
 * No upfront world scan is performed — blocks that are already placed or obstructed
 * are naturally handled at placement time via {@link #isAlreadyPlaced} and
 * {@link #canStillPlace}, exactly matching how range placement processes each
 * position individually. When materials are insufficient, the current position is
 * left in the queue, the workflow is suspended immediately, and progress is
 * persisted — the same eager-suspend pattern as range placement.</p>
 *
 * <p>Preconditions: The pipeline context must contain precomputed placement
 * plans ({@link BlueprintContext#getPlacementPlans()}), a remaining queue
 * ({@link BlueprintContext#getRemainingQueue()}), a resolved session, and a
 * workflow entry ID.</p>
 */
public final class BlueprintTickPipe implements TickablePipe {

    /**
     * 懒加载 BLUEPRINT 服务引用，避免类加载时 ServiceRegistry 尚未初始化导致 IllegalStateException。
     */
    private static BlueprintService getBlueprint() {
        return ServiceRegistry.getInstance().blueprint();
    }

    @Override
    public TickResult tick(PipelineContext ctx) {
        BlueprintContext bctx = BlueprintContext.require(ctx);
        ServerPlayer player = bctx.player();
        ServerLevel level = player.serverLevel();

        // ── 读取预计算放置计划和环形队列 ─────────────────────────
        List<PlacementPlan> plans = bctx.getPlacementPlans();
        if (plans == null) {
            return TickResult.error("Placement plans not initialized in context");
        }

        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (remaining == null || remaining.isEmpty()) {
            return handleComplete(bctx, player, plans.size());
        }

        // 对齐范围放置：不进行全量世界扫描，
        // 已在放置/冲突的方块由 isAlreadyPlaced / canStillPlace 自然处理

        int total = plans.size();
        int placed = bctx.getPlacedCount();
        int skippedMissing = bctx.getSkippedMissing();
        int skippedUnsupported = bctx.getSkippedUnsupported();
        int skippedMissingBlocks = bctx.getSkippedMissingBlocks();
        int skippedBlocked = bctx.getSkippedBlocked();
        int placedBeforeTick = placed;

        // ── 每 tick 尝试放置最多 limit 个方块，缺材料的跳过不计入配额 ──
        int limit = Math.min(64, Math.max(1, total / 10));
        int attemptedThisTick = 0;
        boolean hadMissingThisTick = false;
        LinkedList<Integer> deferredMissing = new LinkedList<>();

        while (!remaining.isEmpty() && attemptedThisTick < limit) {
            int idx = remaining.pollFirst();
            PlacementPlan plan = plans.get(idx);

            if (plan == null) {
                skippedMissingBlocks++;
                continue;
            }

            // 目标位置已存在同种方块 → 视为已放置，不消耗材料
            if (isAlreadyPlaced(level, plan)) {
                placed++;
                continue;
            }

            // 碰撞检测 + 生存性检查
            if (!canStillPlace(player, level, plan.target(), plan.state())) {
                skippedBlocked++;
                continue;
            }

            // ── 材料不足：不占用 limit 配额，延迟到队尾继续扫描后续方块 ──
            if (!player.isCreative() && !hasAllMaterialsForPlan(player, plan)) {
                deferredMissing.add(idx);
                hadMissingThisTick = true;
                continue;
            }

            // ── 材料充足，尝试放置，计入 limit 配额 ──
            attemptedThisTick++;
            switch (attemptPlaceOne(player, level, bctx, plan)) {
                case PLACED -> placed++;
                case MISSING_MATERIALS -> {
                    deferredMissing.add(idx);
                    hadMissingThisTick = true;
                }
                case UNSUPPORTED -> skippedUnsupported++;
                case BLOCKED -> skippedBlocked++;
            }
        }

        // ── 将缺材料的放回队列尾部（环形队列） ──
        remaining.addAll(deferredMissing);

        // ── 保存进度 ─────────────────────────────────────────────
        bctx.setPlacedCount(placed);
        bctx.setSkippedMissing(hadMissingThisTick ? skippedMissing + 1 : skippedMissing);
        bctx.setSkippedUnsupported(skippedUnsupported);
        bctx.setSkippedMissingBlocks(skippedMissingBlocks);
        bctx.setSkippedBlocked(skippedBlocked);
        bctx.setRemainingQueue(remaining);

        // ── 持久化当前状态到工作流条目 ─────────────────────────────
        if (bctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = bctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            BlueprintPersistence.saveToEntry(player, entryId, bctx);
        }

        // ── 报告进度到工作流引擎 ─────────────────────────────────
        int delta = placed - placedBeforeTick;
        if (delta > 0 && bctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = bctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(player, entryId)
                    .ifPresent(token -> token.updateProgress(delta, List.of()));
        }

        // ── 判断是否需要挂起：先放完所有可放置的方块，只剩缺材料的再挂起 ──
        // 旧逻辑：只要本轮有缺材料就立刻挂起，导致材料充足的方块也没机会放
        // 新逻辑：只有本轮没有任何放置进展（没放任何方块）时，说明队列已卡死，才挂起等待补料
        if (hadMissingThisTick && delta == 0
                && bctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = bctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(player, entryId)
                    .ifPresent(token -> token.suspend());
            return TickResult.running();
        }

        // ── 全部放完 ─────────────────────────────────────────────
        if (remaining.isEmpty()) {
            return handleComplete(bctx, player, total);
        }

        return TickResult.running();
    }

    /**
     * 对齐范围放置的完成处理：清除持久化、刷新页面、发送完成消息。
     */
    private static TickResult handleComplete(BlueprintContext bctx, ServerPlayer player, int total) {
        if (bctx.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = bctx.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);

            // 对齐范围放置：先汇报最终进度，再 complete 工作流释放槽位
            RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(token -> {
                token.setCompletedBlocks(bctx.getPlacedCount());

                // 报告跳过方块为失败（blocked/unsupported/missing blocks）
                int failed = bctx.getSkippedMissingBlocks()
                        + bctx.getSkippedBlocked()
                        + bctx.getSkippedUnsupported();
                for (int i = 0; i < failed; i++) {
                    token.recordFailure();
                }

                token.complete();
            });

            BlueprintPersistence.clearFromEntry(player, entryId);
        }
        getBlueprint().refreshPage(player);
        send(player, S2CBlueprintStatusPayload.SUCCESS,
                "screen.rtsbuilding.blueprints.status.complete_partial",
                completionSummary(
                        bctx.getPlacedCount(), total,
                        bctx.getSkippedMissing(), bctx.getSkippedUnsupported(),
                        bctx.getSkippedMissingBlocks(), bctx.getSkippedBlocked()));
        return TickResult.done();
    }

    // ──────────────────────────────────────────────────────────────────
    //  辅助方法
    // ──────────────────────────────────────────────────────────────────

    private enum PlaceResult {
        PLACED, MISSING_MATERIALS, UNSUPPORTED, BLOCKED
    }

    private static PlaceResult attemptPlaceOne(ServerPlayer player, ServerLevel level,
                                                BlueprintContext bctx, PlacementPlan plan) {
        List<ItemStack> extractedMaterials = new ArrayList<>(plan.items().size());

        var bp = getBlueprint();
        if (!player.isCreative()) {
            if (plan.items().isEmpty()) {
                if (plan.fluidCost() == Fluids.WATER) {
                    if (!hasReusableWater(player)) return PlaceResult.UNSUPPORTED;
                } else if (plan.fluidCost() == Fluids.LAVA) {
                    if (bp.countFluidMb(player, Fluids.LAVA)
                            < FluidType.BUCKET_VOLUME) return PlaceResult.UNSUPPORTED;
                } else {
                    return PlaceResult.UNSUPPORTED;
                }
            } else {
                for (Item item : plan.items()) {
                    ItemStack extracted = bp.extractMaterial(player, item, 1);
                    if (extracted.isEmpty()) {
                        refundExtractedMaterials(player, extractedMaterials);
                        return PlaceResult.MISSING_MATERIALS;
                    }
                    extractedMaterials.add(extracted);
                }
            }
        }

        boolean placed = BlockPlacer.setBlock(level, plan.target(), plan.state());
        if (!placed) {
            if (!player.isCreative()) refundExtractedMaterials(player, extractedMaterials);
            return PlaceResult.BLOCKED;
        }

        if (!player.isCreative() && plan.fluidCost() == Fluids.LAVA
                && !bp.extractFluid(player, Fluids.LAVA,
                        FluidType.BUCKET_VOLUME)) {
            level.removeBlock(plan.target(), false);
            refundExtractedMaterials(player, extractedMaterials);
            return PlaceResult.UNSUPPORTED;
        }

        BlockPlacer.applyBlueprintBlockEntity(level, plan.target(), blueprintBlockEntityTagForPlacement(player, plan));
        BlockPlacer.trackPlaced(level, plan.target());
        for (Item item : plan.items()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            if (itemId != null) {
                bp.noteBlockPlaced(player, plan.target(), itemId.toString());
            }
        }
        return PlaceResult.PLACED;
    }

    private static CompoundTag blueprintBlockEntityTagForPlacement(ServerPlayer player, PlacementPlan plan) {
        if (plan == null || plan.blockEntityTag() == null || plan.blockEntityTag().isEmpty()) {
            return null;
        }
        if (player != null && player.isCreative()) {
            return plan.blockEntityTag();
        }
        return BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(plan.blockEntityTag());
    }

    private static void refundExtractedMaterials(ServerPlayer player, List<ItemStack> stacks) {
        if (player == null || stacks == null || stacks.isEmpty()) return;
        var bp = getBlueprint();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) bp.refundMaterial(player, stack);
        }
    }

    private static boolean canStillPlace(ServerPlayer player, ServerLevel level,
                                           BlockPos target, BlockState state) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) return false;
        if (!RtsClaimProtectionService.canPlaceBlock(player, target)) return false;
        if (level.getBlockEntity(target) != null) return false;
        BlockState current = level.getBlockState(target);
        if (!BlueprintReplaceRules.canBlueprintReplace(current)) {
            return false;
        }
        // 对齐范围放置：碰撞检测 + 方块能否存活
        CollisionContext collision = CollisionContext.of(player);
        return state.canSurvive(level, target) && level.isUnobstructed(state, target, collision);
    }

    /**
     * 检查目标位置是否已经存在同种方块。
     * 如果已存在（玩家手动放置或其他来源），不应再次放置或计入 BLOCKED。
     */
    private static boolean isAlreadyPlaced(ServerLevel level, BlockPlacementPlanner.PlacementPlan plan) {
        BlockState current = level.getBlockState(plan.target());
        return current.getBlock() == plan.state().getBlock();
    }

    /**
     * 预检指定放置计划的所有材料是否足够（不实际提取）。
     * 流体方块的特殊检查也在此处理。
     *
     * @return true 如果所有材料都充足
     */
    private static boolean hasAllMaterialsForPlan(ServerPlayer player, PlacementPlan plan) {
        var bp = getBlueprint();
        if (plan.items().isEmpty()) {
            if (plan.fluidCost() == Fluids.WATER) {
                return hasReusableWater(player);
            } else if (plan.fluidCost() == Fluids.LAVA) {
                return bp.countFluidMb(player, Fluids.LAVA) >= FluidType.BUCKET_VOLUME;
            }
            return false;
        }
        for (Item item : plan.items()) {
            if (bp.countMaterial(player, item) <= 0) return false;
        }
        return true;
    }

    private static boolean hasReusableWater(ServerPlayer player) {
        var bp = getBlueprint();
        long waterBuckets = bp.countMaterial(player, Items.WATER_BUCKET);
        long storedWaterBuckets = bp.countFluidMb(player, Fluids.WATER)
                / FluidType.BUCKET_VOLUME;
        return waterBuckets + storedWaterBuckets >= 2L;
    }

    private static void send(ServerPlayer player, byte status, String messageKey, String detail) {
        BlueprintNetworkHandlers.send(player, status, messageKey, detail);
    }

    private static String completionSummary(int placed, int total, int skippedMissing, int skippedUnsupported,
            int skippedMissingBlocks, int skippedBlocked) {
        int skipped = Math.max(0, skippedMissing) + Math.max(0, skippedUnsupported)
                + Math.max(0, skippedMissingBlocks) + Math.max(0, skippedBlocked);
        return placed + "/" + total + " placed, " + skipped + " skipped"
                + " (missing " + skippedMissing + ", unsupported " + skippedUnsupported
                + ", missing blocks " + skippedMissingBlocks + ", blocked " + skippedBlocked + ")";
    }
}
