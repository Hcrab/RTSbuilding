package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

import javax.annotation.Nullable;
import java.util.*;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

/**
 * Blueprint job scanning service — manages world state scanning and material queries for blueprint workflows.
 *
 * <p>Blueprint-specific responsibilities extracted from {@link RtsPendingPlacementService},
 * including scanning remaining blueprint blocks for placed/conflict status, aggregating material requirement lists,
 * and manually resuming pending blueprint workflows.</p>
 *
 * <p>All methods are static stateless methods. Blueprint active pipeline status is accessed through
 * {@link TickablePipelineRegistry}.</p>
 */
public final class RtsBlueprintJobService {

    private RtsBlueprintJobService() {
    }

    /**
     * Scans the remaining block world state for a pending blueprint workflow, returning scan results for the restart panel.
     *
     * <p>Iterates over remaining positions, scans actual block states in the world, counts placed and conflict blocks,
     * and calculates the most scarce material as the bottleneck indicator for available items.</p>
     *
     * @return Scan result, or null if not a BLUEPRINT_BUILD or pipeline context is unavailable
     */
    @Nullable
    public static RtsResumeScanResult scanBlueprintJob(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;

        PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
        if (!(pipeCtx instanceof BlueprintContext bctx)) {
            return null;
        }

        List<PlacementPlan> plans = bctx.getPlacementPlans();
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (plans == null || remaining == null || remaining.isEmpty()) {
            return null;
        }

        var level = player.serverLevel();
        int totalRemaining = remaining.size();
        int alreadyPlacedCount = 0;
        int conflictCount = 0;

        for (int idx : remaining) {
            PlacementPlan plan = plans.get(idx);
            if (plan == null) continue;
            if (!level.hasChunkAt(plan.target())) continue;

            var current = level.getBlockState(plan.target());
            if (current.getBlock() == plan.state().getBlock()) {
                alreadyPlacedCount++;
            } else if (!current.isAir() && !current.canBeReplaced()) {
                conflictCount++;
            }
        }

        int neededItems = totalRemaining - alreadyPlacedCount;
        long bottleneckAvailable;
        if (player.isCreative()) {
            bottleneckAvailable = Integer.MAX_VALUE;
        } else {
            Map<ResourceLocation, Integer> matReqs = new LinkedHashMap<>();
            for (int idx : remaining) {
                PlacementPlan plan = plans.get(idx);
                if (plan == null) continue;
                for (Item item : plan.items()) {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                    if (itemId != null) {
                        matReqs.merge(itemId, 1, Integer::sum);
                    }
                }
            }
            long minAvailable = Long.MAX_VALUE;
            for (Map.Entry<ResourceLocation, Integer> entry : matReqs.entrySet()) {
                int req = entry.getValue();
                if (req <= 0) continue;
                long avail = countMaterial(player, entry.getKey());
                long perBlock = (req + neededItems - 1) / neededItems;
                long blocksPossible = perBlock > 0 ? avail / perBlock : Long.MAX_VALUE;
                minAvailable = Math.min(minAvailable, blocksPossible);
            }
            bottleneckAvailable = minAvailable == Long.MAX_VALUE ? Integer.MAX_VALUE : minAvailable;
        }

        long missingItems = Math.max(0, neededItems - bottleneckAvailable);

        RtsResumeScanResult result = new RtsResumeScanResult(
                "blueprint", "蓝图",
                totalRemaining, alreadyPlacedCount, conflictCount,
                bottleneckAvailable, neededItems, missingItems, workflowEntryId);

        RtsPendingPlacementService.consumeScanResult(player); // clear old cache
        return result;
    }

    /**
     * Scans the remaining block material requirements for a pending blueprint workflow, returning four parallel lists.
     */
    @Nullable
    public static RtsBlueprintMaterialsScan scanBlueprintMaterials(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;

        PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
        if (!(pipeCtx instanceof BlueprintContext bctx)) {
            return null;
        }

        RtsBlueprint blueprint = bctx.getBlueprint();
        if (blueprint == null) return null;

        List<PlacementPlan> plans = bctx.getPlacementPlans();
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (plans == null || remaining == null) return null;

        int total = plans.size();
        int completed = bctx.getPlacedCount();

        Map<ResourceLocation, Integer> materialRequirements = new LinkedHashMap<>();
        for (int idx : remaining) {
            PlacementPlan plan = plans.get(idx);
            if (plan == null) continue;
            for (Item item : plan.items()) {
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
                if (itemId != null) {
                    materialRequirements.merge(itemId, 1, Integer::sum);
                }
            }
        }

        List<String> itemIds = new ArrayList<>(materialRequirements.size());
        List<String> itemLabels = new ArrayList<>(materialRequirements.size());
        List<Integer> required = new ArrayList<>(materialRequirements.size());
        List<Long> available = new ArrayList<>(materialRequirements.size());

        if (player.isCreative()) {
            for (Map.Entry<ResourceLocation, Integer> entry : materialRequirements.entrySet()) {
                ResourceLocation id = entry.getKey();
                itemIds.add(id.toString());
                itemLabels.add(itemLabel(id));
                required.add(entry.getValue());
                available.add((long) Integer.MAX_VALUE);
            }
        } else {
            for (Map.Entry<ResourceLocation, Integer> entry : materialRequirements.entrySet()) {
                ResourceLocation id = entry.getKey();
                int req = entry.getValue();
                long avail = countMaterial(player, id);

                itemIds.add(id.toString());
                itemLabels.add(itemLabel(id));
                required.add(req);
                available.add(avail);
            }
        }

        return new RtsBlueprintMaterialsScan(itemIds, itemLabels, required, available, completed, total);
    }

    /**
     * Resumes a pending blueprint workflow.
     *
     * @return true if successfully resumed
     */
    public static boolean resumeBlueprintWorkflow(ServerPlayer player, int workflowEntryId) {
        if (player == null) return false;
        var engine = RtsWorkflowEngine.getInstance();
        var opt = engine.from(player, workflowEntryId);
        if (opt.isEmpty()) return false;
        RtsWorkflowStatus status = engine.getProgress(player, workflowEntryId);
        if (!status.isActive() || status.type() != RtsWorkflowType.BLUEPRINT_BUILD) {
            return false;
        }
        opt.get().resume();
        RtsbuildingMod.LOGGER.info("[Blueprint] {} manually resumed blueprint job #{} ({} blocks remaining)",
                player.getName().getString(), workflowEntryId, status.remainingBlocks());
        return true;
    }

    // ======================================================================
    //  Helper methods
    // ======================================================================

    private static String itemLabel(ResourceLocation id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return id != null ? id.toString() : "unknown";
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    private static long countMaterial(ServerPlayer player, ResourceLocation itemId) {
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return 0;
        ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        long available = 0;
        available = RtsCountUtil.saturatedAdd(available,
                RtsServer.get().transfer().countLinkedItemsMatching(player,
                        stack -> ItemStack.isSameItemSameComponents(stack, template)));
        available = RtsCountUtil.saturatedAdd(available,
                RtsProgressRefresher.countItemsInPlayerInventory(player, template));
        return available;
    }

    /**
     * Scan result: four parallel lists + progress counts.
     */
    public record RtsBlueprintMaterialsScan(
            List<String> itemIds,
            List<String> itemLabels,
            List<Integer> required,
            List<Long> available,
            int completedCount,
            int totalCount) {
    }
}
