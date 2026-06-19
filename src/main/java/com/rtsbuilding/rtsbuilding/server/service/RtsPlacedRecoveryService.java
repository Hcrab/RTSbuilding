package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;

/**
 * 已放置方块恢复服务——管理 RTS 远程放置方块的破坏和掉落物回收。
 *
 * <p>此服务处理已放置方块（由 {@code PlacedBlockTrackerData} 追踪）的
 * 远程破坏流程，包括模拟精准采集、掉落物收集、入队回收和自动存储。
 * 所有方法均为 {@code static}，类本身为不可实例化的工具类。
 *
 * <p><b>核心流程：</b>
 * <ul>
 *   <li>{@link #breakPlaced(ServerPlayer, BlockPos, Direction, boolean)} —
 *       远程破坏已放置方块：检查权限和追踪状态、模拟下界合金镐+精准采集破坏、
 *       收集新增掉落物入队、从链接存储引用中移除已破坏方块、刷新工作流进度</li>
 *   <li>{@link #tick(ServerPlayer, RtsStorageSession)} —
 *       每 tick 处理恢复作业队列，将掉落物栈依次存入链接存储；
 *       每 tick 最多处理 {@code PLACED_RECOVERY_MAX_JOBS_PER_TICK} 个作业
 *       和 {@code PLACED_RECOVERY_MAX_STACKS_PER_TICK} 个栈</li>
 * </ul>
 *
 * <p><b>内部方法：</b>
 * <ul>
 *   <li>{@link #snapshotNearbyDropIds(ServerLevel, BlockPos)} — 破坏前快照附近掉落物 UUID 集合</li>
 *   <li>{@link #collectNewNearbyDrops(ServerLevel, BlockPos, Set)} — 破坏后收集新增掉落物</li>
 *   <li>{@link #breakWithSimulatedSilkTouch(ServerPlayer, ServerLevel, BlockPos)} —
 *       使用模拟精准采集工具破坏方块</li>
 *   <li>{@link #recoveryHandlersExcluding(List, BlockPos)} — 获取恢复用的处理器列表，排除刚破坏的方块自身</li>
 * </ul>
 *
 * <p><b>存储策略：</b>掉落物优先存入链接存储的同类型堆叠，
 * 溢出时存入玩家背包，再溢出则丢弃并提示玩家。
 * 使用 {@link RtsLinkedHandlerResolutionService#orderHandlersForInsert} 获取有序的插入处理器。
 */
public final class RtsPlacedRecoveryService {

    private RtsPlacedRecoveryService() {
    }

    /**
     * 远程破坏已放置的方块。
     */
    public static void breakPlaced(ServerPlayer player, BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        boolean undoRecovery = allowAdjacentFallback;
        if (!undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        if (!undoRecovery && !RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.immutable();
        if (!tracker.isPlaced(targetPos)) {
            if (!allowAdjacentFallback) {
                return;
            }
            Direction resolvedFace = face == null ? Direction.UP : face;
            BlockPos adjacent = targetPos.relative(resolvedFace);
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, adjacent) || !tracker.isPlaced(adjacent)) {
                return;
            }
            targetPos = adjacent;
        }

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            tracker.clear(targetPos);
            return;
        }

        if (!allowAdjacentFallback) {
            ServerHistoryManager.recordBreak(player, List.of(targetPos), face != null ? face : Direction.UP);
        }

        Set<UUID> dropIdsBeforeBreak = snapshotNearbyDropIds(level, targetPos);
        boolean removed = breakWithSimulatedSilkTouch(player, level, targetPos);
        if (!removed || !level.getBlockState(targetPos).isAir()) {
            return;
        }

        RtsPlacementSound.playRemoteBlockBreakSound(player, level, targetPos);
        tracker.clear(targetPos);
        List<ItemEntity> droppedEntities = collectNewNearbyDrops(level, targetPos, dropIdsBeforeBreak);
        enqueueRecoveryJob(player, session, targetPos, droppedEntities);

        LinkedStorageRef targetRef = new LinkedStorageRef(player.serverLevel().dimension(), targetPos);
        if (session.linkedStorageInfo.remove(targetRef)) {
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
        }
        ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        // 破坏已放置方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        RtsProgressRefresher.refreshWorkflowProgress(player, session);
    }

    /**
     * Tick 处理恢复作业。
     */
    public static void tick(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        Deque<PlacedRecoveryJob> jobs = session.placement.recoveryJobs;
        if (jobs == null || jobs.isEmpty()) {
            return;
        }

        List<LinkedHandler> orderedLinked = RtsLinkedHandlerResolutionService.orderHandlersForInsert(
                RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        boolean hasLinkedRecoveryTarget = false;
        boolean processedAny = false;
        int processedJobs = 0;
        int processedStacks = 0;

        while (!jobs.isEmpty()
                && processedJobs < RtsServiceConstants.PLACED_RECOVERY_MAX_JOBS_PER_TICK
                && processedStacks < RtsServiceConstants.PLACED_RECOVERY_MAX_STACKS_PER_TICK) {
            PlacedRecoveryJob job = jobs.peekFirst();
            if (job == null || job.stacks().isEmpty()) {
                jobs.removeFirst();
                processedJobs++;
                continue;
            }

            List<IItemHandler> handlers = recoveryHandlersExcluding(orderedLinked, job.targetPos());
            hasLinkedRecoveryTarget |= !handlers.isEmpty();
            while (!job.stacks().isEmpty() && processedStacks < RtsServiceConstants.PLACED_RECOVERY_MAX_STACKS_PER_TICK) {
                ItemStack droppedStack = job.stacks().removeFirst();
                if (droppedStack == null || droppedStack.isEmpty()) {
                    continue;
                }
                ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, droppedStack);
                if (!remain.isEmpty()) {
                    overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain));
                }
                processedStacks++;
                processedAny = true;
            }

            if (job.stacks().isEmpty()) {
                jobs.removeFirst();
                processedJobs++;
            }
        }

        if (overflow.hasOverflow()) {
            if (hasLinkedRecoveryTarget) {
                RtsTransferInserter.sendStorageOverflowHint(player, "Absorb", overflow);
            } else if (overflow.dropped() > 0) {
                player.displayClientMessage(
                        Component.literal("Inventory full, dropped " + overflow.dropped() + "."), true);
            }
        }
        if (processedAny) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- 内部方法 ----

    static Set<UUID> snapshotNearbyDropIds(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return Set.of();
        AABB box = new AABB(pos).inflate(0.5D);
        List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty());
        Set<UUID> ids = new HashSet<>(nearby.size());
        for (ItemEntity e : nearby) {
            ids.add(e.getUUID());
        }
        return ids;
    }

    static List<ItemEntity> collectNewNearbyDrops(ServerLevel level, BlockPos pos, Set<UUID> existingIds) {
        if (level == null || pos == null) return List.of();
        AABB box = new AABB(pos).inflate(0.5D);
        List<ItemEntity> all = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty());
        List<ItemEntity> fresh = new ArrayList<>();
        for (ItemEntity e : all) {
            if (!existingIds.contains(e.getUUID())) {
                fresh.add(e);
            }
        }
        return fresh;
    }

    static boolean breakWithSimulatedSilkTouch(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return true;

        ItemStack fakeTool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (Enchantments.SILK_TOUCH != null) {
            var reg = level.holderLookup(Registries.ENCHANTMENT);
            var enchHolder = reg.get(Enchantments.SILK_TOUCH);
            enchHolder.ifPresent(holder ->
                    fakeTool.enchant(holder, 1));
        }

        boolean removed = player.gameMode.destroyBlock(pos);
        if (!removed) return false;

        level.levelEvent(null, 2001, pos, net.minecraft.world.level.block.Block.getId(state));
        return true;
    }

    static boolean breakPlacedWithSimulatedSilkTool(ServerPlayer player, ServerLevel level, BlockPos pos) {
        return breakWithSimulatedSilkTouch(player, level, pos);
    }

    private static void enqueueRecoveryJob(ServerPlayer player, RtsStorageSession session, BlockPos targetPos, List<ItemEntity> droppedEntities) {
        if (player == null || droppedEntities == null || droppedEntities.isEmpty()) {
            return;
        }
        Deque<ItemStack> stacks = new ArrayDeque<>();
        for (ItemEntity droppedEntity : droppedEntities) {
            if (droppedEntity == null) continue;
            ItemStack droppedStack = droppedEntity.getItem();
            if (droppedStack.isEmpty()) continue;
            stacks.addLast(droppedStack.copy());
            droppedEntity.discard();
        }
        if (!stacks.isEmpty()) {
            session.placement.recoveryJobs.addLast(new PlacedRecoveryJob(targetPos.immutable(), stacks));
        }
    }

    /**
     * Returns the list of recovery item handler, excluding the handler whose
     * linked-storage position matches the recovery target position (avoids
     * re-storing into the same block that was just broken).
     */
    private static List<IItemHandler> recoveryHandlersExcluding(List<LinkedHandler> orderedLinked, BlockPos targetPos) {
        if (orderedLinked == null || orderedLinked.isEmpty()) return List.of();
        List<IItemHandler> handlers = new ArrayList<>(orderedLinked.size());
        for (LinkedHandler lh : orderedLinked) {
            if (lh == null || lh.pos() == null || lh.pos().equals(targetPos)) continue;
            IItemHandler h = lh.handler();
            if (h != null) handlers.add(h);
        }
        return handlers;
    }

}
