package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.HistoryBlockRecord;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningDropCapture;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedStorageBlockEventHandler;
import com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.OverflowOutcome;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryClaim;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsPlacementState.PlacedRecoveryJob;
import com.rtsbuilding.rtsbuilding.server.task.BoundedQueueSelector;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
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
import net.neoforged.neoforge.items.IItemHandler;

import java.util.*;

/**
 * 已放置方块恢复服务。
 *
 * <p>新请求通过 {@link #tryInstantRecovery(ServerPlayer, RtsStorageSession, BlockPos, Direction)}
 * 调用一次原版玩家破坏 API。服务只在精确
 * tracker、远程目标和领地权限都通过后，临时换入内部精准采集工具；掉落由
 * {@link RtsMiningDropCapture} 在最终事件边界处理，不再克隆方块物品、人工生成实体或
 * 扫描附近实体。玩家真实主手始终由 {@link TemporaryContextSwitcher} 的
 * {@code try/finally} 恢复。</p>
 *
 * <p>{@link #tick} 与 {@link #tickBudgeted} 仅保留给旧存档中已经持久化的 recovery
 * claim。新瞬时回收不会创建 {@link PlacedRecoveryJob}。</p>
 */
public final class RtsPlacedRecoveryService {

    private RtsPlacedRecoveryService() {
    }

    /** 兼容既有交互 API；显式点击本身即为本次回收授权。 */
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
        ServerLevel level = player.serverLevel();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        BlockPos targetPos = pos.immutable();
        if (!tracker.checkRecovery(level, targetPos, player.getUUID()).canRecover()) {
            if (!allowAdjacentFallback) {
                return;
            }
            Direction resolvedFace = face == null ? Direction.UP : face;
            BlockPos adjacent = targetPos.relative(resolvedFace);
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, adjacent)
                    || !tracker.checkRecovery(level, adjacent, player.getUUID()).canRecover()) {
                return;
            }
            targetPos = adjacent;
        }

        tryInstantRecovery(player, session, targetPos, face, !undoRecovery);
    }

    /**
     * 尝试同步回收一个精确追踪目标，供 Borrow2 之前的挖掘管线调用。
     */
    public static InstantRecoveryResult tryInstantRecovery(
            ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face) {
        return tryInstantRecovery(player, session, pos, face, true);
    }

    private static InstantRecoveryResult tryInstantRecovery(
            ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            boolean recordHistory) {
        if (player == null || session == null || pos == null) {
            return InstantRecoveryResult.FAILED;
        }
        ServerLevel level = player.serverLevel();
        BlockPos targetPos = pos.immutable();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        PlacedBlockTrackerData.RecoveryCheck recoveryCheck =
                tracker.checkRecovery(level, targetPos, player.getUUID());
        if (!recoveryCheck.canRecover()) {
            return InstantRecoveryResult.NOT_TRACKED;
        }
        PlacedBlockTrackerData.CredentialSnapshot originalCredential = recoveryCheck.credential();
        Direction actualFace = face == null ? Direction.UP : face;
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, targetPos)
                || !RtsClaimProtectionService.canBreakBlock(player, targetPos, actualFace)) {
            return InstantRecoveryResult.REJECTED;
        }

        BlockState state = level.getBlockState(targetPos);
        if (state.isAir()) {
            // tracker 指向空气说明记录已经陈旧；不能为了“失败保留”继续制造永久脏标记。
            tracker.clear(targetPos);
            return InstantRecoveryResult.FAILED;
        }
        var brokenStorageIdentity = RtsLinkedStorageBlockEventHandler.captureBrokenIdentity(level, targetPos);

        HistoryBlockRecord historyRecord;
        boolean destroyAccepted;
        try {
            historyRecord = recordHistory
                    ? ServerHistoryManager.captureBlock(level, targetPos, true)
                    : null;
            ItemStack internalTool = createInternalSilkTouchTool(level);
            destroyAccepted = RtsMiningDropCapture.captureInstantRecovery(
                    player, session, targetPos,
                    () -> TemporaryContextSwitcher.withTemporaryMainHandItem(
                            player, internalTool,
                            () -> player.gameMode.destroyBlock(targetPos)));
        } catch (Exception exception) {
            reconcileTrackerAndLinkedStorageAfterFailure(
                    level, tracker, targetPos, state, originalCredential, brokenStorageIdentity);
            RtsbuildingMod.LOGGER.error(
                    "[PlacedRecovery] 原版破坏调用异常，已按最终世界状态对账：player={}, pos={}",
                    player.getGameProfile().getName(), targetPos, exception);
            return InstantRecoveryResult.FAILED;
        }
        if (!destroyAccepted) {
            reconcileTrackerAndLinkedStorageAfterFailure(
                    level, tracker, targetPos, state, originalCredential, brokenStorageIdentity);
            return InstantRecoveryResult.REJECTED;
        }
        if (state.equals(level.getBlockState(targetPos))) {
            tracker.restoreSnapshot(targetPos, originalCredential);
            return InstantRecoveryResult.FAILED;
        }

        tracker.clear(targetPos);
        // BreakEvent 在本次瞬时上下文内刻意不提前提交；到这里原方块已经确实改变，
        // 才统一清理所有玩家的链接引用，避免失败破坏造成不可恢复的提前解绑。
        RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockBroken(
                level, targetPos, brokenStorageIdentity);
        try {
            RtsPlacementSound.playRemoteBlockBreakSound(player, level, targetPos, state);
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[PlacedRecovery] 播放回收声音失败：player={}, pos={}",
                    player.getGameProfile().getName(), targetPos, exception);
        }
        if (historyRecord != null) {
            try {
                ServerHistoryManager.recordBreakWithRecords(
                        player, List.of(historyRecord), actualFace, -1, false);
            } catch (Exception exception) {
                RtsbuildingMod.LOGGER.warn(
                        "[PlacedRecovery] 写入回收历史失败：player={}, pos={}",
                        player.getGameProfile().getName(), targetPos, exception);
            }
        }

        try {
            ServiceRegistry.getInstance().page().markStorageViewDirty(player, session);
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[PlacedRecovery] 标记储存页面刷新失败：player={}, pos={}",
                    player.getGameProfile().getName(), targetPos, exception);
        }
        try {
            RtsProgressRefresher.refreshWorkflowProgress(player, session);
        } catch (Exception exception) {
            RtsbuildingMod.LOGGER.warn(
                    "[PlacedRecovery] 刷新工作流进度失败：player={}, pos={}",
                    player.getGameProfile().getName(), targetPos, exception);
        }
        return InstantRecoveryResult.BROKEN;
    }

    /** 失败不等于世界没变化；只对仍为原状态的位置恢复 tracker。 */
    private static void reconcileTrackerAndLinkedStorageAfterFailure(
            ServerLevel level, PlacedBlockTrackerData tracker,
            BlockPos targetPos, BlockState originalState,
            PlacedBlockTrackerData.CredentialSnapshot originalCredential,
            RtsLinkedStorageBlockEventHandler.BrokenLinkedStorageIdentity brokenStorageIdentity) {
        if (originalState.equals(level.getBlockState(targetPos))) {
            tracker.restoreSnapshot(targetPos, originalCredential);
            return;
        }
        tracker.clear(targetPos);
        RtsLinkedStorageBlockEventHandler.onLinkedStorageBlockBroken(
                level, targetPos, brokenStorageIdentity);
    }

    private static ItemStack createInternalSilkTouchTool(ServerLevel level) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
    }

    /** 瞬时回收的穷尽结果；只有 {@link #BROKEN} 允许消费 tracker。 */
    public enum InstantRecoveryResult {
        NOT_TRACKED,
        BROKEN,
        REJECTED,
        FAILED
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
                            && player.serverLevel().dimension().equals(candidate.dimension())
                            && player.serverLevel().hasChunkAt(candidate.targetPos())),
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
            ServerLevel jobLevel = player.serverLevel();

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
                player.displayClientMessage(
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
