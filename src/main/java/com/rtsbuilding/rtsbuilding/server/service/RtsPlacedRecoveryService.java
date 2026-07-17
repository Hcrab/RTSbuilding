package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob;
import com.rtsbuilding.rtsbuilding.server.task.BoundedQueueSelector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
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
 *   <li>{@link #snapshotNearbyDrops(ServerLevel, BlockPos)} — 有界快照破坏前的附近掉落物</li>
 *   <li>{@link #collectNewNearbyDrops(ServerLevel, BlockPos, Set)} — 有界收集破坏后的新增掉落物</li>
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
        ServerLevel level = player.level();
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
        if (!RtsClaimProtectionService.canBreakBlock(player, targetPos, face != null ? face : Direction.UP)) {
            return;
        }

        NearbyDropSnapshot beforeBreak = snapshotNearbyDrops(level, targetPos);
        if (beforeBreak.saturated()) {
            // 无法完整区分旧实体与新掉落时拒绝破坏，避免错误 claim 周围物品。
            return;
        }
        if (!allowAdjacentFallback) {
            ServerHistoryManager.recordBreak(player, List.of(targetPos), face != null ? face : Direction.UP);
        }
        boolean removed = breakWithSimulatedSilkTouch(player, level, targetPos);
        if (!removed || !level.getBlockState(targetPos).isAir()) {
            return;
        }

        RtsPlacementSound.playRemoteBlockBreakSound(player, level, targetPos, state);
        tracker.clear(targetPos);
        NearbyDropCollection afterBreak = collectNewNearbyDrops(level, targetPos, beforeBreak.entityIds());
        PlacedRecoveryJob queuedRecovery = afterBreak.saturated() ? null
                : enqueueRecoveryJob(player, session, targetPos, afterBreak.entities());

        LinkedStorageRef targetRef = new LinkedStorageRef(player.level().dimension(), targetPos);
        boolean removedLinkedRef = session.linkedStorageInfo.remove(targetRef);
        if (removedLinkedRef) {
            // linkedStorageInfo 与 recovery claim 属于不同组件；两者同时变化时只做一次完整冻结。
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
            if (queuedRecovery != null) {
                queuedRecovery.requirePersistedRevision(
                        ServiceRegistry.getInstance().session().placementRevision(player));
            }
        } else if (queuedRecovery != null) {
            long requiredRevision = ServiceRegistry.getInstance().session()
                    .savePlacementToPlayerNbt(player, session);
            queuedRecovery.requirePersistedRevision(requiredRevision);
        }
        ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        // 破坏已放置方块后刷新放置工作流进度（更新进度条和重启所需方块数）
        RtsProgressRefresher.refreshWorkflowProgress(player, session);
    }

    /**
     * Tick 处理恢复作业。
     */
    public static void tick(ServerPlayer player, RtsStorageSession session) {
        tickBudgeted(player, session,
                RtsServiceConstants.PLACED_RECOVERY_MAX_STACKS_PER_TICK, Long.MAX_VALUE);
    }

    /**
     * 在统一 Task Engine 的调度片内处理回收实体。
     *
     * <p>队列保存实体 UUID 与创建时的精确物品快照；真正物品在成功插入或 fallback 物化前
     * 始终由世界实体持有。实体缺失或物品身份变化时保留 claim，不静默吸走其他物品。</p>
     */
    public static RecoveryTickResult tickBudgeted(
            ServerPlayer player, RtsStorageSession session, int maxUnits, long deadlineNanos) {
        if (player == null || session == null) {
            return new RecoveryTickResult(0, true);
        }
        Deque<PlacedRecoveryJob> jobs = session.placement.recoveryJobs;
        if (jobs == null || jobs.isEmpty()) {
            return new RecoveryTickResult(0, true);
        }

        List<LinkedHandler> orderedLinked = null;
        OverflowOutcome overflow = OverflowOutcome.EMPTY;
        boolean hasLinkedRecoveryTarget = false;
        boolean processedAny = false;
        Set<PlacedRecoveryJob> mutatedJobs = Collections.newSetFromMap(new IdentityHashMap<>());
        int inspectedJobs = 0;
        int processedStacks = 0;
        long persistedPlacementRevision = ServiceRegistry.getInstance().session()
                .persistedPlacementRevision(player);

        while (!jobs.isEmpty()
                && inspectedJobs < RtsServiceConstants.PLACED_RECOVERY_MAX_JOBS_PER_TICK
                && processedStacks < Math.max(1, maxUnits)
                && System.nanoTime() < deadlineNanos) {
            int inspectionBudget = RtsServiceConstants.PLACED_RECOVERY_MAX_JOBS_PER_TICK - inspectedJobs;
            var selection = BoundedQueueSelector.rotateToRunnable(
                    jobs,
                    candidate -> candidate.claims().isEmpty()
                            || (candidate.requiredPersistedRevision() <= persistedPlacementRevision
                            && player.level().dimension().equals(candidate.dimension())
                            && player.level().hasChunkAt(candidate.targetPos())),
                    inspectionBudget);
            inspectedJobs += selection.inspected();
            if (!selection.found()) {
                break;
            }
            PlacedRecoveryJob job = selection.value();
            if (job.claims().isEmpty()) {
                jobs.removeFirst();
                continue;
            }
            ServerLevel jobLevel = player.level();

            // durability ACK、维度和区块门禁通过后才解析外部网络，避免等待落盘期间每 tick 探测 AE/RS。
            if (orderedLinked == null) {
                orderedLinked = RtsLinkedHandlerResolutionService.orderHandlersForInsert(
                        RtsLinkedStorageResolver.resolveLinkedHandlers(player, session));
            }
            List<IItemHandler> handlers = recoveryHandlersExcluding(orderedLinked, job.targetPos());
            hasLinkedRecoveryTarget |= !handlers.isEmpty();
            boolean claimBlocked = false;
            while (!job.claims().isEmpty()
                    && processedStacks < Math.max(1, maxUnits)
                    && System.nanoTime() < deadlineNanos) {
                PlacedRecoveryClaim claim = job.claims().peekFirst();
                net.minecraft.world.entity.Entity entity = jobLevel.getEntity(claim.entityId());
                if (!(entity instanceof ItemEntity droppedEntity) || !droppedEntity.isAlive()) {
                    claimBlocked = true;
                    break;
                }
                ItemStack droppedStack = droppedEntity.getItem();
                if (!claim.matches(droppedStack)) {
                    claimBlocked = true;
                    break;
                }
                ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, droppedStack);
                if (!remain.isEmpty()) {
                    overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain));
                }
                // 单个实体的插入与源实体释放在同一服务端主线程调度片内完成。
                droppedEntity.discard();
                job.claims().removeFirst();
                mutatedJobs.add(job);
                processedStacks++;
                processedAny = true;
            }

            if (job.claims().isEmpty()) {
                jobs.removeFirst();
            } else if (claimBlocked) {
                // 暂时无法核对的 claim 移到队尾；每 tick 仍只检查固定数量的 job。
                jobs.addLast(jobs.removeFirst());
            }
        }

        if (overflow.hasOverflow()) {
            if (hasLinkedRecoveryTarget) {
                RtsTransferInserter.sendStorageOverflowHint(player, "Absorb", overflow);
            } else if (overflow.dropped() > 0) {
                player.sendSystemMessage(
                        Component.literal("Inventory full, dropped " + overflow.dropped() + "."), true);
            }
        }
        if (processedAny) {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
            QuestService.runQuestDetect(player, session, false);
        }
        if (processedAny || jobs.isEmpty()) {
            long requiredRevision = ServiceRegistry.getInstance().session()
                    .savePlacementToPlayerNbt(player, session);
            for (PlacedRecoveryJob mutated : mutatedJobs) {
                if (jobs.contains(mutated)) mutated.requirePersistedRevision(requiredRevision);
            }
        }
        return new RecoveryTickResult(processedStacks, jobs.isEmpty());
    }

    public record RecoveryTickResult(int processedUnits, boolean complete) {
    }

    // ---- 内部方法 ----

    static NearbyDropSnapshot snapshotNearbyDrops(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return new NearbyDropSnapshot(Set.of(), false);
        AABB box = new AABB(pos).inflate(0.5D);
        int safeLimit = RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB;
        List<ItemEntity> nearby = new ArrayList<>(safeLimit + 1);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty(), nearby, safeLimit + 1);
        if (nearby.size() > safeLimit) {
            return new NearbyDropSnapshot(Set.of(), true);
        }
        Set<UUID> ids = new HashSet<>(nearby.size());
        for (ItemEntity e : nearby) {
            ids.add(e.getUUID());
        }
        return new NearbyDropSnapshot(Set.copyOf(ids), false);
    }

    static NearbyDropCollection collectNewNearbyDrops(
            ServerLevel level, BlockPos pos, Set<UUID> existingIds) {
        if (level == null || pos == null) return new NearbyDropCollection(List.of(), false);
        Set<UUID> safeExistingIds = existingIds == null ? Set.of() : existingIds;
        AABB box = new AABB(pos).inflate(0.5D);
        int maxNewDrops = RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB;
        int queryLimit = safeExistingIds.size() + maxNewDrops + 1;
        List<ItemEntity> all = new ArrayList<>(queryLimit);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                e -> e != null && e.isAlive() && !e.getItem().isEmpty(), all, queryLimit);
        List<ItemEntity> fresh = new ArrayList<>();
        for (ItemEntity e : all) {
            if (!safeExistingIds.contains(e.getUUID())) {
                fresh.add(e);
                if (fresh.size() > maxNewDrops) {
                    return new NearbyDropCollection(List.of(), true);
                }
            }
        }
        // 查询结果达到上限时可能仍有未枚举实体；不能声称已经完整区分新旧掉落。
        if (all.size() >= queryLimit) return new NearbyDropCollection(List.of(), true);
        return new NearbyDropCollection(List.copyOf(fresh), false);
    }

    record NearbyDropSnapshot(Set<UUID> entityIds, boolean saturated) {
    }

    record NearbyDropCollection(List<ItemEntity> entities, boolean saturated) {
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

    private static PlacedRecoveryJob enqueueRecoveryJob(
            ServerPlayer player, RtsStorageSession session, BlockPos targetPos,
            List<ItemEntity> droppedEntities) {
        if (player == null || droppedEntities == null || droppedEntities.isEmpty()) {
            return null;
        }
        if (session.placement.recoveryJobs.size()
                >= RtsServiceConstants.PLACED_RECOVERY_MAX_QUEUED_JOBS) {
            return null;
        }
        int claimed = 0;
        for (PlacedRecoveryJob job : session.placement.recoveryJobs) {
            claimed += job.claims().size();
            if (claimed >= RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS) return null;
        }
        int availableClaims = Math.min(
                RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB,
                RtsServiceConstants.PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS - claimed);
        Deque<PlacedRecoveryClaim> claims = new ArrayDeque<>();
        int ordinal = 0;
        for (ItemEntity droppedEntity : droppedEntities) {
            if (claims.size() >= availableClaims) break;
            if (droppedEntity == null) continue;
            ItemStack droppedStack = droppedEntity.getItem();
            if (droppedStack.isEmpty()) continue;
            droppedEntity.setUnlimitedLifetime();
            claims.addLast(new PlacedRecoveryClaim(
                    droppedEntity.getUUID(), ordinal++, droppedStack));
        }
        if (claims.isEmpty()) return null;
        PlacedRecoveryJob job = new PlacedRecoveryJob(
                UUID.randomUUID(), player.level().dimension(), targetPos.immutable(), claims);
        session.placement.recoveryJobs.addLast(job);
        return job;
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
