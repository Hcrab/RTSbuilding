package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.blueprint.RtsBlueprint;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlockPlacementPlanner.PlacementPlan;
import com.rtsbuilding.rtsbuilding.server.pipeline.blueprint.BlueprintPersistence;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.PipelineContext;
import com.rtsbuilding.rtsbuilding.server.pipeline.core.TickablePipelineRegistry;
import com.rtsbuilding.rtsbuilding.server.pipeline.context.BlueprintContext;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 挂起放置作业管理服务——管理因物品不足而被暂挂的放置作业。
 *
 * <p>当远程范围放置或快速建造因存储系统中缺少目标物品而中断时，
 * 剩余的批处理作业会被挂起到 {@code RtsPlacementState.pendingJobs}
 * 队列中，而非丢弃或持续空轮询。玩家可通过显式提交触发扫描和恢复，
 * 或在下一次涉及物品流入的操作（挖掘吸物、合成、物品传输）时自动检测。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li>{@link #scanPendingJob(ServerPlayer, RtsStorageSession, int)} —
 *       按工作流条目 ID 找到对应的挂起作业，扫描所有剩余位置的世界状态
 *       （已放置/冲突/可放置），统计存储系统中可用物品数，返回 {@link RtsResumeScanResult}
 *       并缓存到 {@link #SCAN_CACHE}</li>
 *   <li>{@link #resumeAllPendingJobs(ServerPlayer, RtsStorageSession)} —
 *       遍历挂起作业队列，对物品足够的作业依次移回活跃队列并唤醒对应工作流</li>
 *   <li>{@link #resumeWithStrategy(ServerPlayer, RtsStorageSession, int, int)} —
 *       按指定策略重启特定挂起作业：策略 0=跳过冲突格，策略 1=覆盖破坏冲突方块</li>
 *   <li>{@link #tryResumeAfterStorageChange(ServerPlayer)} —
 *       外部操作（挖掘、合成、传输）完成后的自动检测入口</li>
 *   <li>{@link #refreshWorkflowProgress(ServerPlayer, RtsStorageSession)} —
 *       重新扫描所有挂起和活跃作业的实际放置进度，更新工作流进度条</li>
 * </ul>
 *
 * <p><b>设计特点：</b>
 * <ul>
 *   <li>扫描结果缓存于 {@link #SCAN_CACHE}（ConcurrentHashMap），由客户端消费后清除</li>
 *   <li>创造模式下可用物品数视为 {@code Integer.MAX_VALUE}，永不挂起</li>
 *   <li>支持跳过（skip）和覆盖（overwrite）两种重启策略</li>
 *   <li>使用 {@link RtsWorkflowEngine} 管理每个作业的独立工作流生命周期</li>
 * </ul>
 */
public final class RtsPendingPlacementService {

    /** Per-player cached scan results, cleared after resume/cancel. */
    private static final Map<UUID, RtsResumeScanResult> SCAN_CACHE = new ConcurrentHashMap<>();

    private RtsPendingPlacementService() {
    }

    /**
     * 获取并清除缓存中指定玩家的搁置扫描结果。
     */
    public static RtsResumeScanResult consumeScanResult(ServerPlayer player) {
        if (player == null) return null;
        return SCAN_CACHE.remove(player.getUUID());
    }

    /**
     * 根据工作流条目 ID 在挂起队列中找到对应的作业。
     */
    private static RtsPlacementBatch.PlaceBatchJob findPendingJobByEntryId(RtsStorageSession session, int workflowEntryId) {
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return null;
        }
        for (RtsPlacementBatch.PlaceBatchJob job : session.placement.pendingJobs) {
            if (job.workflowEntryId() == workflowEntryId) {
                return job;
            }
        }
        return null;
    }

    /**
     * 扫描指定玩家的挂起作业的剩余位置，返回扫描结果。
     * 根据 workflowEntryId 找到对应的作业。
     * 结果会被缓存到 SCAN_CACHE 中。
     *
     * @param workflowEntryId 目标工作流条目 ID
     * @return 扫描结果，如果没有匹配的挂起作业则返回 null
     */
    public static RtsResumeScanResult scanPendingJob(ServerPlayer player, RtsStorageSession session, int workflowEntryId) {
        if (player == null || session == null) {
            return null;
        }
        RtsPlacementBatch.PlaceBatchJob job = findPendingJobByEntryId(session, workflowEntryId);
        if (job == null) {
            return null;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }

        // 获取物品的显示名称
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        String itemLabel = itemId;
        Block expectedBlock = null;
        if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
            ItemStack stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id));
            itemLabel = stack.getHoverName().getString();
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem) {
                expectedBlock = blockItem.getBlock();
            }
        }

        List<BlockPos> remaining = job.remainingPositions();
        int totalRemaining = remaining.size();
        int alreadyPlacedCount = 0;
        int conflictCount = 0;

        if (expectedBlock != null && expectedBlock != Blocks.AIR) {
            for (BlockPos pos : remaining) {
                if (!player.serverLevel().hasChunkAt(pos)) {
                    continue;
                }
                BlockState currentState = player.serverLevel().getBlockState(pos);
                Block currentBlock = currentState.getBlock();

                if (currentBlock == expectedBlock) {
                    // 已存在同种方块（用户手动放置）
                    alreadyPlacedCount++;
                } else if (!currentState.isAir() && !currentState.canBeReplaced()) {
                    // 存在不同方块且不可替换 → 冲突
                    conflictCount++;
                }
                // 空气或可替换 → 正常需要放置
            }
        }

        // 统计存储系统中可用物品数
        ItemStack template = resolveTemplate(job.itemPrototype(), itemId);
        final ItemStack finalTemplate = template;
        long availableItems = 0;
        if (!finalTemplate.isEmpty()) {
            availableItems = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                    stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
            availableItems = RtsCountUtil.saturatedAdd(availableItems,
                    countItemsInPlayerInventory(player, finalTemplate));
        }

        // 创造模式下无限物品
        if (player.isCreative()) {
            availableItems = Integer.MAX_VALUE;
        }

        int neededItems = totalRemaining - alreadyPlacedCount;
        long missingItems = Math.max(0, neededItems - availableItems);

        RtsResumeScanResult result = new RtsResumeScanResult(
                itemId, itemLabel,
                totalRemaining, alreadyPlacedCount, conflictCount,
                availableItems, neededItems, missingItems, workflowEntryId);

        // 缓存结果
        SCAN_CACHE.put(player.getUUID(), result);

        return result;
    }

    /**
     * 扫描挂起的蓝图工作流的剩余方块世界状态，返回扫描结果供重启面板展示。
     *
     * <p>对齐范围放置 {@link #scanPendingJob} 的模式：遍历剩余位置，
     * 扫描世界中的实际方块状态，统计已放置数和冲突数，
     * 并计算最紧缺材料作为可用物品数的瓶颈指标。
     * 多物品的详细材料清单由 {@link #scanBlueprintMaterials} 提供。</p>
     *
     * @return 扫描结果，如果不是 BLUEPRINT_BUILD 或管道上下文不可用时返回 null
     */
    @Nullable
    public static RtsResumeScanResult scanBlueprintJob(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;

        // 从 TickablePipelineRegistry 获取活跃管道上下文
        PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, workflowEntryId);
        if (!(pipeCtx instanceof BlueprintContext bctx)) {
            return null;
        }

        List<PlacementPlan> plans = bctx.getPlacementPlans();
        LinkedList<Integer> remaining = bctx.getRemainingQueue();
        if (plans == null || remaining == null || remaining.isEmpty()) {
            return null;
        }

        ServerLevel level = player.serverLevel();
        int totalRemaining = remaining.size();
        int alreadyPlacedCount = 0;
        int conflictCount = 0;

        // ── 扫描世界状态：已放置 / 冲突 / 可放置 ────────────────
        for (int idx : remaining) {
            PlacementPlan plan = plans.get(idx);
            if (plan == null) continue;
            if (!level.hasChunkAt(plan.target())) continue;

            BlockState current = level.getBlockState(plan.target());
            if (current.getBlock() == plan.state().getBlock()) {
                alreadyPlacedCount++;
            } else if (!current.isAir() && !current.canBeReplaced()) {
                conflictCount++;
            }
        }

        // ── 计算材料瓶颈 ────────────────────────────────────────────
        int neededItems = totalRemaining - alreadyPlacedCount;
        long bottleneckAvailable;
        if (player.isCreative()) {
            bottleneckAvailable = Integer.MAX_VALUE;
        } else {
            // 聚合所有剩余方块的材料需求，取最小可用数作为瓶颈
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
                ResourceLocation id = entry.getKey();
                int req = entry.getValue();
                if (req <= 0) continue;
                long avail = countMaterial(player, id);
                // 每个方块需要多份不同材料，以完成度最低的为瓶颈
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

        SCAN_CACHE.put(player.getUUID(), result);
        return result;
    }

    /**
     * 扫描挂起的蓝图工作流的剩余方块材料需求，返回四项平行列表。
     *
     * <p>从活跃管道的 {@link BlueprintContext} 中读取蓝图数据，
     * 遍历 {@code nextIndex} 之后的每个方块，聚合每种材料的需求总量，
     * 并查询链接存储和玩家背包中的可用数量。</p>
     *
     * @param player          服务器端玩家
     * @param workflowEntryId 目标工作流条目 ID
     * @return 包含四项列表的数组 [itemIds, itemLabels, required, available]，
     *         或 null（蓝图不可用/没有管道上下文）
     */
    @Nullable
    public static RtsBlueprintMaterialsScan scanBlueprintMaterials(ServerPlayer player, int workflowEntryId) {
        if (player == null) return null;

        // 从 TickablePipelineRegistry 获取活跃管道上下文
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

        // 聚合环形队列中剩余方块的材料需求
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

        // 构建结果列表
        List<String> itemIds = new ArrayList<>(materialRequirements.size());
        List<String> itemLabels = new ArrayList<>(materialRequirements.size());
        List<Integer> required = new ArrayList<>(materialRequirements.size());
        List<Long> available = new ArrayList<>(materialRequirements.size());

        if (player.isCreative()) {
            // 创造模式：所有材料充足
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
                countItemsInPlayerInventory(player, template));
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

    /**
     * 恢复挂起的蓝图工作流（无需策略处理，直接取消挂起状态）。
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

    /**
     * 尝试恢复指定玩家的所有挂起放置作业。
     * 遍历 {@code pendingJobs}，若对应物品在当前库存中足够，
     * 则将作业移回 {@code placeBatchJobs} 继续执行。
     *
     * @return 恢复的作业数量
     */
    public static int resumeAllPendingJobs(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return 0;
        }
        if (session.placement.pendingJobs.isEmpty()) {
            return 0;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return 0;
        }

        List<RtsPlacementBatch.PlaceBatchJob> resumed = new java.util.ArrayList<>();
        int count = 0;
        while (!session.placement.pendingJobs.isEmpty()) {
            RtsPlacementBatch.PlaceBatchJob job = session.placement.pendingJobs.peekFirst();
            if (!canResumeJob(player, session, job)) {
                // 物品还不够，保留在挂起队列中
                break;
            }
            // 物品够了，移到活跃队列
            session.placement.pendingJobs.removeFirst();
            session.placement.placeBatchJobs.addLast(job);
            resumed.add(job);
            count++;
        }

        if (count > 0) {
            RtsbuildingMod.LOGGER.info("[PendingPlacement] {} 恢复了 {} 个挂起放置作业",
                    player.getName().getString(), count);
            // 恢复每个独立 job 对应的搁置工作流
            for (RtsPlacementBatch.PlaceBatchJob rj : resumed) {
                RtsWorkflowEngine.getInstance().from(player, rj.workflowEntryId()).ifPresent(token -> token.resume());
            }
            // 通知客户端刷新页面
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }
        return count;
    }

    /**
     * 检查一个挂起作业当前是否有足够的物品继续执行。
     * 只检查第一个未放置位置所需的物品在库存中是否还剩至少 1 个。
     */
    private static boolean canResumeJob(ServerPlayer player, RtsStorageSession session,
                                         RtsPlacementBatch.PlaceBatchJob job) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        ItemStack template = resolveTemplate(job.itemPrototype(), itemId);
        final ItemStack finalTemplate = template;
        if (finalTemplate.isEmpty()) {
            return false;
        }
        // 统计链接存储中该物品的数量
        long available = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
        available = RtsCountUtil.saturatedAdd(available,
                countItemsInPlayerInventory(player, finalTemplate));
        // 至少需要 1 个才能继续
        return available >= 1;
    }

    /**
     * 检查是否有挂起作业并尝试恢复。
     * 适合在外部操作（挖掘吸物、合成、传输）完成后调用。
     * 同时检查并恢复挂起的蓝图工作流。
     */
    public static void tryResumeAfterStorageChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null) {
            return;
        }
        // 恢复挂起放置作业
        if (!session.placement.pendingJobs.isEmpty()
                && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            resumeAllPendingJobs(player, session);
        }
        // 恢复挂起的蓝图作业
        resumeSuspendedBlueprints(player);
    }

    /**
     * 尝试恢复所有挂起的蓝图工作流。
     * 遵循与范围放置一致的原则：不在自动恢复中扫描材料，
     * 蓝图工作流只通过用户显式操作（UI 恢复按钮）重新激活。
     * 蓝图自身的 {@code hasAllMaterialsForPlan} 预检会在 tick 中自然处理材料检查。
     */
    private static void resumeSuspendedBlueprints(ServerPlayer player) {
        // 空操作——蓝图不自动恢复，与范围放置保持一致的严格策略。
        // 恢复必须由用户通过 UI 按钮手动触发。
    }

    /**
     * 使用指定的策略重启指定搁置作业。
     *
     * @param strategy 重启策略：0=正常重启（失败项跳过），1=覆盖放置
     * @param workflowEntryId 目标工作流条目 ID
     */
    public static boolean resumeWithStrategy(ServerPlayer player, RtsStorageSession session, int strategy, int workflowEntryId) {
        if (player == null || session == null) {
            return false;
        }
        RtsPlacementBatch.PlaceBatchJob job = findPendingJobByEntryId(session, workflowEntryId);
        if (job == null) {
            return false;
        }

        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        if (strategy == 0) {
            // 跳过冲突格
            skipConflictPositions(player, job);
        } else if (strategy == 1) {
            // 覆盖放置：破坏冲突方块并存入存储
            overwriteConflictPositions(player, job, session);
        }

        // 移到活跃队列（按 entry ID 删除正确的 job，而不是删第一个）
        session.placement.pendingJobs.remove(job);
        session.placement.placeBatchJobs.addLast(job);
        RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.resume());
        // 覆盖策略不刷新界面，由 tick 自然推进放置无需刷新页面
        if (strategy == 0) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        }

        RtsbuildingMod.LOGGER.info("[PendingPlacement] {} 使用策略 {} 重启了搁置放置作业",
                player.getName().getString(), strategy == 0 ? "SKIP" : "OVERWRITE");
        return true;
    }

    /**
     * 跳过 pending job 中所有冲突的格位（直接推进 index）。
     */
    /**
     * Refreshes the workflow progress for the current placement batch
     * (active or suspended) by scanning all clicked positions in the
     * first pending/active job and counting how many blocks are actually
     * still placed in the world.
     *
     * <p>Call this after blocks are broken externally so that the progress
     * bar and the needed-items count reflect reality.</p>
     */
    public static void refreshWorkflowProgress(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) return;

        // 遍历所有 job（先 pending 再 active），逐个检测实际放置情况
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
            if (expectedBlock == Blocks.AIR) continue;

            // Scan ALL positions (not just remaining) to count actually placed blocks.
            // Blocks may be placed at clickedPos OR at clickedPos.relative(face) —
            // the same logic as RtsPlacementHelper.detectPlacedPos.
            List<BlockPos> allPositions = new ArrayList<>(job.clickedPositions());
            Direction face = job.face();
            int actualPlaced = 0;
            for (BlockPos pos : allPositions) {
                boolean found = false;
                // Check clicked position
                if (player.serverLevel().hasChunkAt(pos)) {
                    BlockState state = player.serverLevel().getBlockState(pos);
                    if (state.getBlock() == expectedBlock) {
                        found = true;
                    }
                }
                // Check adjacent position (block may be placed on the clicked face)
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

            // Update the workflow entry using the job's specific workflow entry ID
            int finalActPlaced = actualPlaced;
            RtsWorkflowEngine.getInstance().from(player, job.workflowEntryId()).ifPresent(token -> token.setCompletedBlocks(finalActPlaced));
        }

        // ── 蓝图工作流：扫描所有蓝图方块的已放置数，并回收被挖掉的 ──
        var engine = RtsWorkflowEngine.getInstance();
        for (var status : engine.getAllProgress(player)) {
            if (!status.isActive() || status.type() != RtsWorkflowType.BLUEPRINT_BUILD) continue;
            int entryId = status.entryId();
            PipelineContext pipeCtx = TickablePipelineRegistry.findContextByWorkflowEntry(player, entryId);
            if (!(pipeCtx instanceof BlueprintContext bctx)) continue;

            List<PlacementPlan> plans = bctx.getPlacementPlans();
            LinkedList<Integer> remaining = bctx.getRemainingQueue();
            if (plans == null || remaining == null || plans.isEmpty()) continue;

            ServerLevel level = player.serverLevel();
            int total = plans.size();
            Set<Integer> remainingSet = new HashSet<>(remaining);
            LinkedList<Integer> backToQueue = new LinkedList<>();
            int actualPlaced = 0;

            for (int idx = 0; idx < total; idx++) {
                PlacementPlan plan = plans.get(idx);
                if (plan == null) continue;
                if (remainingSet.contains(idx)) continue; // 还没轮到，跳过

                if (!level.hasChunkAt(plan.target())) continue;
                BlockState current = level.getBlockState(plan.target());
                if (current.getBlock() == plan.state().getBlock()) {
                    actualPlaced++;
                } else {
                    // 之前已放置/跳过，但现在挖掉了 → 加回队列
                    backToQueue.add(idx);
                }
            }

            // 把被挖掉的放回队列尾部
            remaining.addAll(backToQueue);

            // 另外把 remaining 中已经又被人手动放好的移除（减少重复工作）
            remaining.removeIf(idx -> {
                PlacementPlan plan = plans.get(idx);
                if (plan == null) return false;
                if (!level.hasChunkAt(plan.target())) return false;
                return level.getBlockState(plan.target()).getBlock() == plan.state().getBlock();
            });

            // 更新上下文和 workflow entry
            bctx.setPlacedCount(actualPlaced);
            bctx.setRemainingQueue(remaining);
            BlueprintPersistence.saveToEntry(player, entryId, bctx);
            int finalPlaced = actualPlaced;
            engine.from(player, entryId).ifPresent(token -> token.setCompletedBlocks(finalPlaced));
        }
    }

    private static void skipConflictPositions(ServerPlayer player, RtsPlacementBatch.PlaceBatchJob job) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return;
        if (!(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem)) return;
        Block expectedBlock = blockItem.getBlock();
        if (expectedBlock == Blocks.AIR) return;

        List<BlockPos> remaining = job.remainingPositions();
        for (BlockPos pos : remaining) {
            if (!player.serverLevel().hasChunkAt(pos)) continue;
            BlockState currentState = player.serverLevel().getBlockState(pos);
            Block currentBlock = currentState.getBlock();
            if (currentBlock != expectedBlock && !currentState.isAir() && !currentState.canBeReplaced()) {
                // 遇到冲突格，消费掉它（推进 index）
                job.skipOne();
            } else if (currentBlock == expectedBlock) {
                // 已手动放置，也消费掉
                job.skipOne();
            } else {
                // 空气或可替换 → 正常需要放置，停止跳过
                break;
            }
        }
    }

    /**
     * 覆盖冲突格位：破坏冲突方块后重启线程。
     *
     * <p>遍历剩余位置，遇到冲突格（不同方块且不可替换）直接破坏并收集掉落。
     * 不预检测材料、不回退跳过、不放置方块——破坏完成后由调用方重启线程，
     * tick 会正常放置所有剩余位置。</p>
     */
    private static void overwriteConflictPositions(ServerPlayer player, RtsPlacementBatch.PlaceBatchJob job,
                                                    RtsStorageSession session) {
        String itemId = job.itemId();
        if (itemId == null || itemId.isBlank()) return;
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return;
        if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) return;
        if (!(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id) instanceof net.minecraft.world.item.BlockItem blockItem)) return;
        Block expectedBlock = blockItem.getBlock();
        if (expectedBlock == Blocks.AIR) return;

        var level = player.serverLevel();
        var linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        var insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);

        // ── 仅破坏冲突格，不预检、不回退、不放置 ──
        for (BlockPos pos : job.remainingPositions()) {
            if (!level.hasChunkAt(pos)) continue;
            BlockState currentState = level.getBlockState(pos);
            Block currentBlock = currentState.getBlock();

            if (currentBlock == expectedBlock) continue;           // 已放置 → 不动
            if (currentState.isAir() || currentState.canBeReplaced()) continue; // 空气 → 不动

            // 冲突方块：破坏并收集掉落
            java.util.List<ItemStack> drops = Block.getDrops(currentState, level, pos, level.getBlockEntity(pos));
            level.destroyBlock(pos, false);
            if (!currentState.requiresCorrectToolForDrops() || player.isCreative()) {
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        RtsTransferInserter.storeToLinkedWithFallback(insertHandlers, player, drop);
                    }
                }
            } else {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§e警告：" + currentBlock.getName() + " 需要合适的工具才能掉落！"),
                        true);
            }
        }
    }

    // ======================================================================
    //  辅助方法
    // ======================================================================

    /**
     * 当模板 ItemStack 为空时，从 itemId 构造 fallback 模板。
     */
    @Nullable
    private static ItemStack resolveTemplate(ItemStack template, String itemId) {
        if (!template.isEmpty() || itemId == null || itemId.isBlank()) {
            return template;
        }
        ResourceLocation fallbackId = ResourceLocation.tryParse(itemId);
        if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(fallbackId));
        }
        return template;
    }

    /**
     * 统计玩家主背包中与模板匹配的物品总量。
     */
    private static long countItemsInPlayerInventory(ServerPlayer player, ItemStack template) {
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