package com.rtsbuilding.rtsbuilding.server.pipeline.blueprint;

import com.rtsbuilding.rtsbuilding.common.blueprint.rule.BlueprintReplaceRules;
import com.rtsbuilding.rtsbuilding.common.blueprint.sanitize.BlueprintBlockEntitySanitizer;
import com.rtsbuilding.rtsbuilding.compat.create.BlueprintCreatePlacementCompat;
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
import com.rtsbuilding.rtsbuilding.server.task.BlueprintTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.TaskBudget;
import com.rtsbuilding.rtsbuilding.server.task.TaskStepResult;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
        EntityPlayerMP player = payload.player();
        WorldServer level = player.getServerWorld();
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
                exhaustedMissingCycle = payload.recordDeferredPlacement();
                if (exhaustedMissingCycle) break;
                continue;
            }

            switch (attemptPlaceOne(player, level, plan)) {
                case PLACED:
                    context.setPlacedCount(context.getPlacedCount() + 1);
                    cursor++;
                    succeeded++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                    break;
                case MISSING_MATERIALS:
                    deferred.addLast(index);
                    exhaustedMissingCycle = payload.recordDeferredPlacement();
                    break;
                case UNSUPPORTED:
                    context.setSkippedUnsupported(context.getSkippedUnsupported() + 1);
                    cursor++;
                    failed++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                    break;
                case BLOCKED:
                    context.setSkippedBlocked(context.getSkippedBlocked() + 1);
                    cursor++;
                    failed++;
                    payload.recordPlacementProgress(remaining.size() + deferred.size());
                    break;
                default:
                    throw new IllegalStateException("Unknown blueprint placement result");
            }
            if (exhaustedMissingCycle) break;
        }
        remaining.addAll(deferred);
        if (exhaustedMissingCycle) context.setSkippedMissing(context.getSkippedMissing() + 1);

        int delta = context.getPlacedCount() - placedBefore;
        if (delta > 0 && context.hasData(PipelineContext.KEY_WORKFLOW_ENTRY_ID)) {
            int entryId = context.getData(PipelineContext.KEY_WORKFLOW_ENTRY_ID);
            RtsWorkflowEngine.getInstance().from(player, entryId)
                    .ifPresent(token -> token.updateProgress(delta, com.rtsbuilding.rtsbuilding.server.task.Java8Collections.listOf()));
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

    private static void finish(BlueprintContext context, EntityPlayerMP player, int total) {
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

    private static PlaceResult attemptPlaceOne(EntityPlayerMP player, WorldServer level, PlacementPlan plan) {
        List<ItemStack> extracted = new ArrayList<ItemStack>(plan.items().size());
        BlueprintService service = blueprint();
        if (!player.isCreative()) {
            if (plan.items().isEmpty()) {
                if (plan.fluidCost() == FluidRegistry.WATER) {
                    if (!hasReusableWater(player)) return PlaceResult.UNSUPPORTED;
                } else if (plan.fluidCost() == FluidRegistry.LAVA) {
                    if (service.countFluidMb(player, FluidRegistry.LAVA) < Fluid.BUCKET_VOLUME) {
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

        IBlockState replacedState = level.getBlockState(plan.target());
        if (!BlockPlacer.setBlueprintBlock(level, plan.target(), plan.state())) {
            if (!player.isCreative()) refund(player, extracted);
            return PlaceResult.BLOCKED;
        }
        if (!player.isCreative() && plan.fluidCost() == FluidRegistry.LAVA
                && !service.extractFluid(player, FluidRegistry.LAVA, Fluid.BUCKET_VOLUME)) {
            // 模拟计数与真实提取之间可能被其他任务抢先消耗；失败时恢复被替换的原状态。
            level.setBlockState(plan.target(), replacedState, 3);
            refund(player, extracted);
            return PlaceResult.UNSUPPORTED;
        }
        BlockPlacer.applyBlueprintBlockEntity(level, plan.target(), blockEntityTag(player, level, plan));
        BlockPlacer.finishBlueprintPlacement(
                level, plan.target(), plan.state(),
                extracted.isEmpty() ? ItemStack.EMPTY : extracted.get(0));
        BlockPlacer.trackPlaced(level, plan.target());
        for (Item item : plan.items()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null) service.noteBlockPlaced(player, plan.target(), id.toString());
        }
        return PlaceResult.PLACED;
    }

    private static NBTTagCompound blockEntityTag(
            EntityPlayerMP player, WorldServer level, PlacementPlan plan) {
        if (plan.blockEntityTag() == null || plan.blockEntityTag().isEmpty()) return null;
        NBTTagCompound tag = player.isCreative() && player.canUseCommandBlock()
                ? plan.blockEntityTag()
                : BlueprintBlockEntitySanitizer.sanitizeForSurvivalPlacement(plan.blockEntityTag());
        return BlueprintCreatePlacementCompat.prepareBlockEntityTag(
                level, plan.target(), plan.state(), tag);
    }

    private static boolean canStillPlace(
            EntityPlayerMP player, WorldServer level, BlockPos target, IBlockState state) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) return false;
        if (!RtsClaimProtectionService.canPlaceBlock(player, target)) return false;
        if (level.getTileEntity(target) != null) return false;
        if (!BlueprintReplaceRules.canBlueprintReplace(level.getBlockState(target))) return false;
        if (!state.getBlock().canPlaceBlockAt(level, target)) return false;
        AxisAlignedBB collisionBox = state.getCollisionBoundingBox(level, target);
        return collisionBox == null || collisionBox == Block.NULL_AABB
                || level.checkNoEntityCollision(collisionBox.offset(target), player);
    }

    private static boolean isAlreadyPlaced(WorldServer level, PlacementPlan plan) {
        return level.getBlockState(plan.target()).getBlock() == plan.state().getBlock();
    }

    private static boolean hasAllMaterialsForPlan(EntityPlayerMP player, PlacementPlan plan) {
        BlueprintService service = blueprint();
        if (plan.items().isEmpty()) {
            if (plan.fluidCost() == FluidRegistry.WATER) return hasReusableWater(player);
            if (plan.fluidCost() == FluidRegistry.LAVA) {
                return service.countFluidMb(player, FluidRegistry.LAVA) >= Fluid.BUCKET_VOLUME;
            }
            return false;
        }
        for (Item item : plan.items()) if (service.countMaterial(player, item) <= 0) return false;
        return true;
    }

    private static boolean hasReusableWater(EntityPlayerMP player) {
        BlueprintService service = blueprint();
        return service.countMaterial(player, Items.WATER_BUCKET)
                + service.countFluidMb(player, FluidRegistry.WATER) / Fluid.BUCKET_VOLUME >= 2L;
    }

    private static void refund(EntityPlayerMP player, List<ItemStack> stacks) {
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
