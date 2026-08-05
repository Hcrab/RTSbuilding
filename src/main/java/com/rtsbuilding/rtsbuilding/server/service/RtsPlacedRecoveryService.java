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
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.block.Block;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

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
 *   <li>{@link #breakPlaced(EntityPlayerMP, BlockPos, EnumFacing, boolean)} —
 *       远程破坏已放置方块：检查权限和追踪状态、冻结方块物品表示并触发 Forge 破坏门禁、
 *       收集新增掉落物入队、从链接存储引用中移除已破坏方块、刷新工作流进度</li>
 *   <li>{@link #tick(EntityPlayerMP, RtsStorageSession)} —
 *       每 tick 处理恢复作业队列，将掉落物栈依次存入链接存储；
 *       每 tick 最多处理 {@code PLACED_RECOVERY_MAX_JOBS_PER_TICK} 个作业
 *       和 {@code PLACED_RECOVERY_MAX_STACKS_PER_TICK} 个栈</li>
 * </ul>
 *
 * <p><b>内部方法：</b>
 * <ul>
 *   <li>{@link #snapshotNearbyDrops(WorldServer, BlockPos)} — 有界快照破坏前的附近掉落物</li>
 *   <li>{@link #collectNewNearbyDrops(WorldServer, BlockPos, Set)} — 有界收集破坏后的新增掉落物</li>
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
    public static void breakPlaced(EntityPlayerMP player, BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) {
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
        WorldServer level = player.getServerForPlayer();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.toImmutable();
        if (!tracker.isPlaced(targetPos)) {
            if (!allowAdjacentFallback) {
                return;
            }
            EnumFacing resolvedFace = face == null ? EnumFacing.UP : face;
            BlockPos adjacent = targetPos.offset(resolvedFace);
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, adjacent) || !tracker.isPlaced(adjacent)) {
                return;
            }
            targetPos = adjacent;
        }

        BlockState state = BlockState.fromWorld(level, targetPos);
        if (isAir(state)) {
            tracker.clear(targetPos);
            return;
        }
        if (!RtsClaimProtectionService.canBreakBlock(player, targetPos, face != null ? face : EnumFacing.UP)) {
            return;
        }

        NearbyDropSnapshot beforeBreak = snapshotNearbyDrops(level, targetPos);
        if (beforeBreak.saturated()) {
            return;
        }
        if (!allowAdjacentFallback) {
            ServerHistoryManager.recordBreak(player, Collections.singletonList(targetPos),
                    face != null ? face : EnumFacing.UP);
        }

        ItemStack recoveredBlock = recoveryStack(level, targetPos, state);
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(recoveredBlock)) {
            return;
        }
        boolean removed = recoverTrackedBlock(player, level, targetPos, state);
        if (!removed || !isAir(BlockState.fromWorld(level, targetPos))) {
            tracker.mark(targetPos);
            return;
        }

        RtsPlacementSound.playRemoteBlockBreakSound(player, level, targetPos, state);
        tracker.clear(targetPos);
        EntityItem recoveredEntity = materializeRecoveredBlock(level, targetPos, recoveredBlock);
        NearbyDropCollection afterBreak = collectNewNearbyDrops(level, targetPos, beforeBreak.entityIds());
        PlacedRecoveryJob queuedRecovery = afterBreak.saturated() ? null
                : enqueueRecoveryJob(player, session, targetPos, afterBreak.entities());
        if (recoveredEntity == null) {
            ItemStack remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, recoveredBlock.copy());
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remainder)) {
                player.dropPlayerItemWithRandomChoice(remainder, false);
            }
        }

        LinkedStorageRef targetRef = new LinkedStorageRef(player.dimension, targetPos);
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
    public static void tick(EntityPlayerMP player, RtsStorageSession session) {
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
            EntityPlayerMP player, RtsStorageSession session, int maxUnits, long deadlineNanos) {
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
            BoundedQueueSelector.Selection<PlacedRecoveryJob> selection = BoundedQueueSelector.rotateToRunnable(
                    jobs,
                    candidate -> candidate.claims().isEmpty()
                            || (candidate.requiredPersistedRevision() <= persistedPlacementRevision
                            && player.dimension == candidate.dimension()
                            && com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(player.getServerForPlayer(), candidate.targetPos())),
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
            WorldServer jobLevel = player.getServerForPlayer();

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
                Entity entity = com.rtsbuilding.rtsbuilding.platform.entity.EntityCompat.findByUuid(
                        jobLevel, claim.entityId());
                if (!(entity instanceof EntityItem) || entity.isDead) {
                    claimBlocked = true;
                    break;
                }
                EntityItem droppedEntity = (EntityItem) entity;
                ItemStack droppedStack = droppedEntity.getEntityItem();
                if (!claim.matches(droppedStack)) {
                    claimBlocked = true;
                    break;
                }
                ItemStack remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, droppedStack);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(remain)) {
                    overflow = overflow.merge(RtsTransferInserter.storeToLinkedWithFallback(handlers, player, remain));
                }
                // 单个实体的插入与源实体释放在同一服务端主线程调度片内完成。
                droppedEntity.setDead();
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
                com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(player,
                        new ChatComponentText("Inventory full, dropped " + overflow.dropped() + "."), true);
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

    public static final class RecoveryTickResult {
        private final int processedUnits;
        private final boolean complete;

        public RecoveryTickResult(int processedUnits, boolean complete) {
            this.processedUnits = processedUnits;
            this.complete = complete;
        }

        public int processedUnits() {
            return processedUnits;
        }

        public boolean complete() {
            return complete;
        }
    }

    // ---- 内部方法 ----

    static NearbyDropSnapshot snapshotNearbyDrops(WorldServer level, BlockPos pos) {
        if (level == null || pos == null) return new NearbyDropSnapshot(Collections.<UUID>emptySet(), false);
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.5D);
        int safeLimit = RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB;
        RtsBoundedItemEntityQuery.Result query = RtsBoundedItemEntityQuery.query(level, box, safeLimit,
                entity -> entity != null && !entity.isDead && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(entity.getEntityItem()));
        if (query.saturated()) {
            return new NearbyDropSnapshot(Collections.<UUID>emptySet(), true);
        }
        List<EntityItem> nearby = query.entities();
        Set<UUID> ids = new HashSet<>(nearby.size());
        for (EntityItem entity : nearby) {
            ids.add(entity.getUniqueID());
        }
        return new NearbyDropSnapshot(Collections.unmodifiableSet(new HashSet<>(ids)), false);
    }

    static NearbyDropCollection collectNewNearbyDrops(
            WorldServer level, BlockPos pos, Set<UUID> existingIds) {
        if (level == null || pos == null) return new NearbyDropCollection(Collections.<EntityItem>emptyList(), false);
        Set<UUID> safeExistingIds = existingIds == null ? Collections.<UUID>emptySet() : existingIds;
        AxisAlignedBB box = new AxisAlignedBB(pos).grow(0.5D);
        int maxNewDrops = RtsServiceConstants.PLACED_RECOVERY_MAX_ENTITIES_PER_JOB;
        RtsBoundedItemEntityQuery.Result query = RtsBoundedItemEntityQuery.query(level, box, maxNewDrops,
                entity -> entity != null && !entity.isDead && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(entity.getEntityItem())
                        && !safeExistingIds.contains(entity.getUniqueID()));
        if (query.saturated()) {
            return new NearbyDropCollection(Collections.<EntityItem>emptyList(), true);
        }
        return new NearbyDropCollection(query.entities(), false);
    }

    static final class NearbyDropSnapshot {
        private final Set<UUID> entityIds;
        private final boolean saturated;

        NearbyDropSnapshot(Set<UUID> entityIds, boolean saturated) {
            this.entityIds = entityIds;
            this.saturated = saturated;
        }

        Set<UUID> entityIds() {
            return entityIds;
        }

        boolean saturated() {
            return saturated;
        }
    }

    static final class NearbyDropCollection {
        private final List<EntityItem> entities;
        private final boolean saturated;

        NearbyDropCollection(List<EntityItem> entities, boolean saturated) {
            this.entities = entities;
            this.saturated = saturated;
        }

        List<EntityItem> entities() {
            return entities;
        }

        boolean saturated() {
            return saturated;
        }
    }

    static ItemStack recoveryStack(WorldServer level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || isAir(state)) return null;
        net.minecraft.item.Item item = state.getBlock().getItem(
                level, pos.getX(), pos.getY(), pos.getZ());
        ItemStack stack = item == null ? null : new ItemStack(
                item, 1, state.getBlock().getDamageValue(
                        level, pos.getX(), pos.getY(), pos.getZ()));
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
            Block block = state.getBlock();
            stack = new ItemStack(block, 1, block.damageDropped(state.getMetadata()));
        }
        return stack;
    }

    static boolean recoverTrackedBlock(
            EntityPlayerMP player, WorldServer level, BlockPos pos, BlockState state) {
        if (player == null || level == null || pos == null || state == null || isAir(state)) return false;
        Integer breakExperience = TemporaryContextSwitcher.withTemporaryMainHandItem(
                player, null,
                () -> {
                    net.minecraftforge.event.world.BlockEvent.BreakEvent event =
                            ForgeHooks.onBlockBreakEvent(
                                    level, player.theItemInWorldManager.getGameType(), player,
                                    pos.getX(), pos.getY(), pos.getZ());
                    return event.isCanceled() ? -1 : event.getExpToDrop();
                });
        if (breakExperience == null || breakExperience.intValue() < 0) return false;
        return com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.destroyBlock(level, pos, false);
    }

    private static EntityItem materializeRecoveredBlock(
            WorldServer level, BlockPos pos, ItemStack recoveredBlock) {
        EntityItem entity = new EntityItem(
                level,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                recoveredBlock.copy());
        return level.spawnEntityInWorld(entity) ? entity : null;
    }

    private static PlacedRecoveryJob enqueueRecoveryJob(
            EntityPlayerMP player, RtsStorageSession session, BlockPos targetPos,
            List<EntityItem> droppedEntities) {
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
        for (EntityItem droppedEntity : droppedEntities) {
            if (claims.size() >= availableClaims) break;
            if (droppedEntity == null) continue;
            ItemStack droppedStack = droppedEntity.getEntityItem();
            if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(droppedStack)) continue;
            com.rtsbuilding.rtsbuilding.platform.entity.EntityCompat.setNoDespawn(droppedEntity);
            claims.addLast(new PlacedRecoveryClaim(
                    droppedEntity.getUniqueID(), ordinal++, droppedStack));
        }
        if (claims.isEmpty()) return null;
        PlacedRecoveryJob job = new PlacedRecoveryJob(
                UUID.randomUUID(), player.dimension, targetPos.toImmutable(), claims);
        session.placement.recoveryJobs.addLast(job);
        return job;
    }

    /**
     * Returns the list of recovery item handler, excluding the handler whose
     * linked-storage position matches the recovery target position (avoids
     * re-storing into the same block that was just broken).
     */
    private static List<IItemHandler> recoveryHandlersExcluding(List<LinkedHandler> orderedLinked, BlockPos targetPos) {
        if (orderedLinked == null || orderedLinked.isEmpty()) return Collections.emptyList();
        List<IItemHandler> handlers = new ArrayList<>(orderedLinked.size());
        for (LinkedHandler lh : orderedLinked) {
            if (lh == null || lh.pos() == null || lh.pos().equals(targetPos)) continue;
            IItemHandler h = lh.handler();
            if (h != null) handlers.add(h);
        }
        return handlers;
    }

    private static boolean isAir(BlockState state) {
        return state == null || state.getBlock() == Blocks.air;
    }

}
