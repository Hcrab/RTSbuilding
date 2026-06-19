package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.model.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.*;

/**
 * 蓝图作业扫描服务——管理蓝图工作流的世界状态扫描和材料查询。
 *
 * <p>从 {@link RtsPendingPlacementService} 提取的蓝图特定职责，
 * 包括扫描蓝图剩余方块的已放置/冲突状态、聚合材料需求清单、
 * 以及手动恢复挂起的蓝图工作流。</p>
 *
 * <p>所有方法均为静态无状态方法。蓝图活跃管道状态通过
 * {@link TickablePipelineRegistry} 访问。</p>
 */
public final class RtsBlueprintJobService {

    private RtsBlueprintJobService() {
    }

    /**
     * 扫描挂起的蓝图工作流的剩余方块世界状态，返回扫描结果供重启面板展示。
     *
     * <p>遍历剩余位置，扫描世界中的实际方块状态，统计已放置数和冲突数，
     * 并计算最紧缺材料作为可用物品数的瓶颈指标。</p>
     *
     * @return 扫描结果，如果不是 BLUEPRINT_BUILD 或管道上下文不可用时返回 null
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
     * 扫描挂起的蓝图工作流的剩余方块材料需求，返回四项平行列表。
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
     * 恢复挂起的蓝图工作流。
     *
     * @return true 表示成功恢复
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
        RtsbuildingMod.LOGGER.info("[Blueprint] {} 手动恢复了蓝图作业 #{} (剩余 {} 方块)",
                player.getName().getString(), workflowEntryId, status.remainingBlocks());
        return true;
    }

    // ======================================================================
    //  辅助方法
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
                ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                        stack -> ItemStack.isSameItemSameComponents(stack, template)));
        available = RtsCountUtil.saturatedAdd(available,
                RtsProgressRefresher.countItemsInPlayerInventory(player, template));
        return available;
    }

    /**
     * 扫描结果：四项平行列表 + 进度计数。
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
