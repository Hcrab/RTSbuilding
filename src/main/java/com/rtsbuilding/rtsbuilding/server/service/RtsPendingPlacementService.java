package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementBatch;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.workflow.core.RtsWorkflowEngine;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理因物品不足而被挂起的放置作业（pendingJobs）。
 *
 * <p>当范围放置/快速建造因库存中缺少目标物品而中断时，剩余作业
 * 被挂起到 {@code RtsPlacementState.pendingJobs} 而非继续轮询。
 * 玩家可通过显式提交触发唤醒，或在下一次涉及物品流入的操作
 * （挖掘吸物、合成、物品传输）时自动检测并恢复。</p>
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
        ItemStack template = job.itemPrototype();
        if (template.isEmpty() && itemId != null && !itemId.isBlank()) {
            // 当 prototype 为空时从 itemId 构造 fallback 模板
            ResourceLocation fallbackId = ResourceLocation.tryParse(itemId);
            if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
                template = new ItemStack(BuiltInRegistries.ITEM.get(fallbackId));
            }
        }
        final ItemStack finalTemplate = template;
        long availableItems = 0;
        if (!finalTemplate.isEmpty()) {
            availableItems = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                    stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
            // 也统计玩家主背包中的物品（placement 可以从背包取物）
            boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
            if (includePlayerInventory) {
                int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
                int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
                for (int slot = start; slot < end; slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, finalTemplate)) {
                        availableItems = RtsCountUtil.saturatedAdd(availableItems, stack.getCount());
                    }
                }
            }
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
        ItemStack template = job.itemPrototype();
        if (template.isEmpty() && itemId != null && !itemId.isBlank()) {
            // 当 prototype 为空时从 itemId 构造 fallback 模板
            ResourceLocation fallbackId = ResourceLocation.tryParse(itemId);
            if (fallbackId != null && BuiltInRegistries.ITEM.containsKey(fallbackId)) {
                template = new ItemStack(BuiltInRegistries.ITEM.get(fallbackId));
            }
        }
        final ItemStack finalTemplate = template;
        if (finalTemplate.isEmpty()) {
            return false;
        }
        // 统计链接存储中该物品的数量
        long available = ServiceRegistry.getInstance().transfer().countLinkedItemsMatching(player,
                stack -> ItemStack.isSameItemSameComponents(stack, finalTemplate));
        // 也统计玩家主背包中的物品
        boolean includePlayerInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
        if (includePlayerInventory) {
            int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
            int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
            for (int slot = start; slot < end; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, finalTemplate)) {
                    available = RtsCountUtil.saturatedAdd(available, stack.getCount());
                }
            }
        }
        // 至少需要 1 个才能继续
        return available >= 1;
    }

    /**
     * 检查是否有挂起作业并尝试恢复。
     * 适合在外部操作（挖掘吸物、合成、传输）完成后调用。
     */
    public static void tryResumeAfterStorageChange(ServerPlayer player) {
        if (player == null) {
            return;
        }
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null || session.placement.pendingJobs.isEmpty()) {
            return;
        }
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }
        resumeAllPendingJobs(player, session);
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

}