package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
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
import com.rtsbuilding.rtsbuilding.server.RtsServer;

import java.util.*;
import com.rtsbuilding.rtsbuilding.server.RtsServer;

/**
 * Placed block recovery service — manages destruction and item drop recovery for RTS remote-placed blocks.
 *
 * <p>This service handles the remote destruction process for placed blocks (tracked by {@code PlacedBlockTrackerData}),
 * including simulated silk touch, item drop collection, enqueue recovery, and auto-storage.
 * All methods are {@code static}, the class itself is a non-instantiable utility class.
 *
 * <p><b>Core flow:</b>
 * <ul>
 *   <li>{@link #breakPlaced(ServerPlayer, BlockPos, Direction, boolean)} —
 *       Remotely destroys a placed block: checks permissions and tracking status, simulates netherite pickaxe + silk touch break,
 *       collects new drops into queue, removes destroyed block from linked storage references, refreshes workflow progress</li>
 *   <li>{@link #tick(ServerPlayer, RtsStorageSession)} —
 *       Processes recovery job queue each tick, storing drop stacks into linked storage sequentially;
 *       processes at most {@code PLACED_RECOVERY_MAX_JOBS_PER_TICK} jobs
 *       and {@code PLACED_RECOVERY_MAX_STACKS_PER_TICK} stacks per tick</li>
 * </ul>
 *
 * <p><b>Internal methods:</b>
 * <ul>
 *   <li>{@link #snapshotNearbyDropIds(ServerLevel, BlockPos)} — Snapshots nearby drop UUIDs before breaking</li>
 *   <li>{@link #collectNewNearbyDrops(ServerLevel, BlockPos, Set)} — Collects new drops after breaking</li>
 *   <li>{@link #breakWithSimulatedSilkTouch(ServerPlayer, ServerLevel, BlockPos)} —
 *       Breaks block using simulated silk touch tool</li>
 *   <li>{@link #recoveryHandlersExcluding(List, BlockPos)} — Gets recovery handler list, excluding the just-destroyed block's own handler</li>
 * </ul>
 *
 * <p><b>Storage strategy:</b> Drops prefer existing stacks in linked storage,
 * overflow goes to player inventory, further overflow is dropped with player notification.
 * Uses {@link RtsLinkedHandlerResolutionService#orderHandlersForInsert} to get ordered insert handlers.
 */
public final class RtsPlacedRecoveryService {

    private RtsPlacedRecoveryService() {
    }

    /**
     * Remotely destroys a placed block.
     */
    public static void breakPlaced(ServerPlayer player, BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        boolean undoRecovery = allowAdjacentFallback;
        if (!undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (undoRecovery && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return;
        }
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
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
            RtsServer.get().session().saveToPlayerNbt(player, session);
        }
        RtsServer.get().page().markStorageViewDirty(player, session);
        // Refresh placement workflow progress after breaking placed block (update progress bar and required block count for restart)
        RtsProgressRefresher.refreshWorkflowProgress(player, session);
    }

    /**
     * Tick processing for recovery jobs.
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
            RtsServer.get().page().markStorageViewDirty(player, session);
            QuestService.runQuestDetect(player, session, false);
        }
    }

    // ---- Internal methods ----

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
