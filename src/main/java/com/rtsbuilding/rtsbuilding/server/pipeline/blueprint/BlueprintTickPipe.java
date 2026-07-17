package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.rule.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.common.blueprint.sanitize.BlueprintBlockEntitySanitizer;
import com.rtsbuilding.rtsbuilding.network.blueprint.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.BlueprintService;
import com.rtsbuilding.rtsbuilding.server.service.placement.BlockPlacer;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import com.rtsbuilding.rtsbuilding.server.task.BlueprintTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.TaskBudget;
import com.rtsbuilding.rtsbuilding.server.task.TaskStepResult;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 蓝图 Task Executor 的领域实现。
 *
 * <p>类名暂时保留以降低迁移 diff，但它不再是第二套 Tick runtime。每个目标（包括缺失、
 * 已存在、碰撞和缺料检查）都消费统一调度器的 work unit，并在每次副作用前检查纳秒预算。</p>
 */
public final class BlueprintTickPipe {
    private BlueprintTickPipe() {
    }

    public static TaskStepResult execute(BlueprintTaskPayload payload, TaskBudget budget) {
        BlueprintContext context = payload.context();
        ServerPlayer player = payload.player();
        ServerLevel level = player.level();
        List<PlacementPlan> plans = context.getPlacementPlans();
        LinkedList<Integer> remaining = context.getRemainingQueue();
        if (plans == null || remaining == null) {
            return TaskStepResult.fail("rtsbuilding.task.error.blueprint_plan_missing");
        }
        if (remaining.isEmpty()) {
            finish(context, player, plans.size());
            return TaskStepResult.complete(0, 0, 0, 0);
        }

        int placedBefore = context.getPlacedCount();
        int processed = 0;
        int cursor = 0;
        int succeeded = 0;
        int failed = 0;
        boolean missing = false;
        boolean exhaustedMissingCycle = false;
        LinkedList<Integer> deferred = new LinkedList<>();
        payload.beginPlacementCycle(remaining.size());

        while (!remaining.isEmpty() && processed < budget.maxUnits() && budget.hasTime()) {
            int index = remaining.removeFirst();
            processed++;
            PlacementPlan plan = index >= 0 && index < plans.size() ? plans.get(index) : null;
            if (plan == null) {
                context.setSkippedMissingBlocks(context.getSkippedMissingBlocks() + 1);
                cursor++;
                failed++;
                payload.recordPlacementProgress(remaining.size() + deferred.size());
                continue;
            }
            if (isAlreadyPlaced(level, plan)) {
                context.setPlacedCount(context.getPlacedCount() + 1);
                cursor++;
                succeeded++;
                payload.recordPlacementProgress(remaining.size() + deferred.size());
                continue;
            }
            if (!canStillPlace(player, level, plan.target(), plan.state())) {
                context.setSkippedBlocked(context.getSkippedBlocked() + 1);
                cursor++;
                failed++;
                payload.recordPlacementProgress(remaining.size() + deferred.size());
                continue;
            }
            if (!player.isCreative() && !hasAllMaterialsForPlan(player, plan)) {
                deferred.addLast(index);
                missing = true;
                exhaustedMissingCycle = payload.recordDeferredPlacement();
                if (exhaustedMissingCycle) break;
                continue;
            }

            switch (attemptPlaceOne(player, level, plan)) {
                case PLACED -> {
                    context.setPlacedCount(context.getPlacedCount() + 1);
                    cursor++;
                    succeeded++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                }
                case MISSING_MATERIALS -> {
                    deferred.addLast(index);
                    missing = true;
                    exhaustedMissingCycle = payload.recordDeferredPlacement();
                }
                case UNSUPPORTED -> {
                    context.setSkippedUnsupported(context.getSkippedUnsupported() + 1);
                    cursor++;
                    failed++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                }
                case BLOCKED -> {
                    context.setSkippedBlocked(context.getSkippedBlocked() + 1);
                    cursor++;
                    failed++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                }
            }
            if (exhaustedMissingCycle) break;
        }
        remaining.addAll(deferred);
        if (exhaustedMissingCycle) context.setSkippedMissing(context.getSkippedMissing() + 1);

        int delta = context.getPlacedCount() - placedBefore;
        if (delta > 0 && context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(player, entryId)
                    .ifPresent(token -> token.updateProgress(delta, List.of()));
        }

        if (remaining.isEmpty()) {
            finish(context, player, plans.size());
            return TaskStepResult.complete(processed, cursor, succeeded, failed);
        }
        if (exhaustedMissingCycle) {
            checkpoint(payload, true);
            return TaskStepResult.waitForResource(processed, cursor, succeeded, failed);
        }
        checkpoint(payload, false);
        return TaskStepResult.nextTick(processed, cursor, succeeded, failed);
    }

    private static void checkpoint(BlueprintTaskPayload payload, boolean force) {
        BlueprintContext context = payload.context();
        if (!context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)
                || !payload.shouldCheckpoint(force)) return;
        if (com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .isDurableBlueprintContext(context)) return;
        BlueprintPersistence.saveToEntry(
                payload.player(), context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID), context);
    }

    private static void finish(BlueprintContext context, ServerPlayer player, int total) {
        if (context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(player, entryId).ifPresent(token -> {
                token.setCompletedBlocks(context.getPlacedCount());
                int failures = context.getSkippedMissingBlocks()
                        + context.getSkippedBlocked() + context.getSkippedUnsupported();
                token.recordFailures(failures);
            });
            if (!com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                    .isDurableBlueprintContext(context)) {
                BlueprintPersistence.clearFromEntry(player, entryId);
            }
        }
        blueprint().refreshPage(player);
        BlueprintNetworkHandlers.send(player, S2CBlueprintStatusPayload.SUCCESS,
                "screen.rtsbuilding.blueprints.status.complete_partial",
                completionSummary(context, total));
    }

    private enum PlaceResult { PLACED, MISSING_MATERIALS, UNSUPPORTED, BLOCKED }

    private static PlaceResult attemptPlaceOne(ServerPlayer player, ServerLevel level, PlacementPlan plan) {
        List<ItemStack> extracted = new ArrayList<>(plan.items().size());
        BlueprintService service = blueprint();
        if (!player.isCreative()) {
            if (plan.items().isEmpty()) {
                if (plan.fluidCost() == Fluids.WATER) {
                    if (!hasReusableWater(player)) return PlaceResult.UNSUPPORTED;
                } else if (plan.fluidCost() == Fluids.LAVA) {
                    if (service.countFluidMb(player, Fluids.LAVA) < RtsFluidVolume.BUCKET_AMOUNT) {
                        return PlaceResult.UNSUPPORTED;
                    }
                } else {
                    return PlaceResult.UNSUPPORTED;
                }
            } else {
                for (Item item : plan.items()) {
                    ItemStack stack = service.extractMaterial(player, item, 1);
                    if (stack.isEmpty()) {
                        refund(player, extracted);
                        return PlaceResult.MISSING_MATERIALS;
                    }
                    extracted.add(stack);
                }
            }
        }

        if (!BlockPlacer.setBlock(level, plan.target(), plan.state())) {
            if (!player.isCreative()) refund(player, extracted);
            return PlaceResult.BLOCKED;
        }
        if (!player.isCreative() && plan.fluidCost() == Fluids.LAVA
                && !service.extractFluid(player, Fluids.LAVA, RtsFluidVolume.BUCKET_AMOUNT)) {
            level.removeBlock(plan.target(), false);
            refund(player, extracted);
            return PlaceResult.UNSUPPORTED;
        }
        BlockPlacer.applyBlueprintBlockEntity(level, plan.target(), blockEntityTag(player, plan));
        BlockPlacer.trackPlaced(level, plan.target());
        for (Item item : plan.items()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) service.noteBlockPlaced(player, plan.target(), id.toString());
        }
        return PlaceResult.PLACED;
    }

    private static CompoundTag blockEntityTag(ServerPlayer player, PlacementPlan plan) {
        if (plan.blockEntityTag() == null || plan.blockEntityTag().isEmpty()) return null;
        if (player.isCreative() && player.canUseGameMasterBlocks()) return plan.blockEntityTag();
        return BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(plan.blockEntityTag());
    }

    private static boolean canStillPlace(
            ServerPlayer player, ServerLevel level, BlockPos target, BlockState state) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) return false;
        if (!RtsClaimProtectionService.canPlaceBlock(player, target)) return false;
        if (level.getBlockEntity(target) != null) return false;
        if (!BlueprintReplaceRules.canBlueprintReplace(level.getBlockState(target))) return false;
        CollisionContext collision = CollisionContext.of(player);
        return state.canSurvive(level, target) && level.isUnobstructed(state, target, collision);
    }

    private static boolean isAlreadyPlaced(ServerLevel level, PlacementPlan plan) {
        return level.getBlockState(plan.target()).getBlock() == plan.state().getBlock();
    }

    private static boolean hasAllMaterialsForPlan(ServerPlayer player, PlacementPlan plan) {
        BlueprintService service = blueprint();
        if (plan.items().isEmpty()) {
            if (plan.fluidCost() == Fluids.WATER) return hasReusableWater(player);
            if (plan.fluidCost() == Fluids.LAVA) {
                return service.countFluidMb(player, Fluids.LAVA) >= RtsFluidVolume.BUCKET_AMOUNT;
            }
            return false;
        }
        for (Item item : plan.items()) if (service.countMaterial(player, item) <= 0) return false;
        return true;
    }

    private static boolean hasReusableWater(ServerPlayer player) {
        BlueprintService service = blueprint();
        return service.countMaterial(player, Items.WATER_BUCKET)
                + service.countFluidMb(player, Fluids.WATER) / RtsFluidVolume.BUCKET_AMOUNT >= 2L;
    }

    private static void refund(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) if (!stack.isEmpty()) blueprint().refundMaterial(player, stack);
    }

    private static BlueprintService blueprint() {
        return ServiceRegistry.getInstance().blueprint();
    }

    private static String completionSummary(BlueprintContext context, int total) {
        int skipped = Math.max(0, context.getSkippedMissing())
                + Math.max(0, context.getSkippedUnsupported())
                + Math.max(0, context.getSkippedMissingBlocks())
                + Math.max(0, context.getSkippedBlocked());
        return context.getPlacedCount() + "/" + total + " placed, " + skipped + " skipped"
                + " (missing " + context.getSkippedMissing()
                + ", unsupported " + context.getSkippedUnsupported()
                + ", missing blocks " + context.getSkippedMissingBlocks()
                + ", blocked " + context.getSkippedBlocked() + ")";
    }
}
