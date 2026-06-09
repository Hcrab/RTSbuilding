package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.network.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

/**
 * Owns the remote mining state machine for one {@link RtsStorageSession}.
 *
 * <p>This helper is intentionally focused on player-visible mining behavior:
 * starting and ticking remote break progress, collecting and processing
 * Ultimine targets, borrowing a real mutable tool stack, reading back the
 * mutated main-hand remainder after block destruction, and returning that stack
 * to the original source or an explicit fallback. It deliberately does not own
 * crafting, fluid transfer, storage page construction, linked-storage
 * resolution rules, or placement batching; those systems remain in their
 * existing helpers so this split does not widen the gameplay surface.
 *
 * <p>The extraction exists because {@link RtsStorageManager} has become a broad
 * coordinator. Keeping mining here makes future tool, Ultimine, and server
 * tick fixes easier to review without changing packet handlers or the
 * multiplayer flow players already feel in-game.
 */
final class RtsStorageMining {
    private static final int ULTIMINE_MAX_BLOCKS = 256;
    private static final int ULTIMINE_BLOCKS_PER_TICK = 8;
    private static final int PLAYER_HOTBAR_SLOT_COUNT = 9;
    private static final int MINING_STORAGE_REFRESH_DELAY_TICKS = 8;
    private static final int AREA_DESTROY_MAX_TARGETS = 32768;

    private RtsStorageMining() {
    }

    static void mine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face, boolean start,
            byte toolSlot, String toolItemId, ItemStack toolPrototype, boolean allowPlacedBlockRecovery) {
        if (start && !RtsProgressionManager.canUse(player, RtsFeature.REMOTE_BREAK)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);

        if (start) {
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                stopActiveMining(player, session);
                return;
            }

            if (allowPlacedBlockRecovery
                    && PlacedBlockTrackerData.get(player.serverLevel()).isPlaced(pos)
                    && RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
                BlockState before = player.serverLevel().getBlockState(pos);
                RtsStorageManager.breakPlaced(player, pos, face, false);
                BlockState after = player.serverLevel().getBlockState(pos);
                if (!before.equals(after)) {
                    stopActiveMining(player, session);
                    return;
                }
            }
            stopActiveMining(player, session);
            if (player.isCreative()) {
                destroyMinedBlock(player, session, pos, slot);
                RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
                return;
            }
            session.miningToolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
            beginRemoteMining(player, session, pos, face, slot);
            return;
        }

        stopActiveMining(player, session);
    }

    static void startUltimine(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face, byte toolSlot,
            String toolItemId, ItemStack toolPrototype, int requestedLimit, byte mode) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        int progressionLimit = RtsProgressionManager.getUltimineLimit(player);
        if (progressionLimit <= 0) {
            return;
        }
        int limit = Math.max(1, Math.min(Math.min(ULTIMINE_MAX_BLOCKS, progressionLimit), requestedLimit));

        if (player.isCreative()) {
            Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, ItemStack.EMPTY, limit, true, mode);
            if (targets.isEmpty()) {
                stopActiveMining(player, session);
                return;
            }
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        stopActiveMining(player, session);
        ToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        Deque<BlockPos> targets = collectUltimineTargets(player, pos, slot, toolLease.stack(), limit, false, mode);
        if (targets.isEmpty()) {
            returnMiningTool(player, session, toolLease);
            resetMiningState(session);
            return;
        }

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = slot;
        sendUltimineProgress(player, 0, targets.size());
        beginRemoteMining(player, session, targets.peekFirst(), face, slot);
    }

    static void areaDestroy(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions,
            byte toolSlot, String toolItemId, ItemStack toolPrototype) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.ULTIMINE)) {
            return;
        }
        if (session == null || positions == null || positions.isEmpty()) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        int slot = clampHotbarSlot(toolSlot);
        if (player.isCreative()) {
            Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, ItemStack.EMPTY, true);
            if (targets.isEmpty()) {
                stopActiveMining(player, session);
                return;
            }
            stopActiveMining(player, session);
            breakCreativeUltimineTargets(player, session, targets, slot);
            RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
            return;
        }

        stopActiveMining(player, session);
        ToolLease toolLease = borrowMiningTool(player, session, toolItemId, toolPrototype, slot);
        Deque<BlockPos> targets = collectAreaDestroyTargets(player, positions, slot, toolLease.stack(), false);
        if (targets.isEmpty()) {
            returnMiningTool(player, session, toolLease);
            resetMiningState(session);
            return;
        }

        session.miningToolLease = toolLease;
        session.ultimineTargets.clear();
        session.ultimineTargets.addAll(targets);
        session.ultimineProgressPos = targets.peekFirst();
        session.ultimineTotalTargets = targets.size();
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningToolSlot = slot;
        sendUltimineProgress(player, 0, targets.size());
        beginRemoteMining(player, session, targets.peekFirst(), null, slot);
    }

    static void tickActiveMining(ServerPlayer player, RtsStorageSession session) {
        if (session.miningPos == null) {
            if (!session.ultimineTargets.isEmpty()) {
                processUltimineTargets(player, session);
            }
            return;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, session.miningPos)) {
            stopActiveMining(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos pos = session.miningPos;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F) {
            stopActiveMining(player, session);
            return;
        }

        float step = computeRemoteDestroyStep(player, state, pos, session.miningToolSlot, session.miningToolLease.stack());
        if (step <= 0.0F) {
            return;
        }

        session.miningProgress += step;
        int stage = Math.min(9, (int) (session.miningProgress * 10.0F));
        if (stage != session.miningStage) {
            level.destroyBlockProgress(player.getId(), pos, stage);
            sendMineProgress(player, pos, stage);
            session.miningStage = stage;
        }

        if (session.miningProgress < 1.0F) {
            return;
        }

        boolean broken = destroyMinedBlock(player, session, pos, session.miningToolSlot);
        level.destroyBlockProgress(player.getId(), pos, -1);

        if (broken && !session.ultimineTargets.isEmpty()) {
            removeUltimineTarget(session, pos);
            session.ultimineProcessedTargets = Math.max(session.ultimineProcessedTargets, 1);
            if (session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                session.ultimineAbsorbedDrops |= absorbNearbyDropsIntoLinked(player, pos, session);
            }
            session.miningPos = null;
            session.miningProgress = 0.0F;
            session.miningStage = -1;
            processUltimineTargets(player, session);
            return;
        }

        sendMineProgress(player, pos, -1);
        if (broken && session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
            boolean absorbed = absorbNearbyDropsIntoLinked(player, pos, session);
            if (absorbed) {
                RtsStorageManager.runQuestDetect(player, session, false);
            }
        }
        returnMiningTool(player, session, session.miningToolLease);
        scheduleMiningStorageRefresh(player, session);
        resetMiningState(session);
    }

    static void stopActiveMining(ServerPlayer player, RtsStorageSession session) {
        boolean hadMiningState = session.miningPos != null
                || session.ultimineProgressPos != null
                || !session.ultimineTargets.isEmpty()
                || !session.miningToolLease.isEmpty();
        boolean hadUltimine = session.ultimineProgressPos != null || !session.ultimineTargets.isEmpty();
        BlockPos progressPos = session.miningPos != null ? session.miningPos : session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            sendMineProgress(player, progressPos, -1);
        }
        if (hadUltimine) {
            sendUltimineProgress(player, -1, 0);
        }
        returnMiningTool(player, session, session.miningToolLease);
        if (hadMiningState) {
            scheduleMiningStorageRefresh(player, session);
        }
        resetMiningState(session);
    }

    static void tickDeferredStoragePageRefresh(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null || session.deferredStorageRefreshTick < 0L) {
            return;
        }
        if (session.miningPos != null || session.ultimineProgressPos != null || !session.ultimineTargets.isEmpty()) {
            return;
        }
        if (player.serverLevel().getGameTime() < session.deferredStorageRefreshTick) {
            return;
        }
        session.deferredStorageRefreshTick = -1L;
        RtsStorageManager.requestPage(player, session.page, session.search, session.category, session.sort, session.ascending);
    }

    static void scheduleMiningStorageRefresh(ServerPlayer player, RtsStorageSession session) {
        if (player == null || session == null) {
            return;
        }
        RtsStorageManager.markStorageViewDirty(player, session);
        session.deferredStorageRefreshTick = player.serverLevel().getGameTime() + MINING_STORAGE_REFRESH_DELAY_TICKS;
    }
    static <T> T withTemporaryMainHandItem(ServerPlayer player, ItemStack stack, Supplier<T> action) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            return action.get();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
    }

    static <T> T withTemporaryOnGround(ServerPlayer player, boolean onGround, Supplier<T> action) {
        boolean previous = player.onGround();
        player.setOnGround(onGround);
        try {
            return action.get();
        } finally {
            player.setOnGround(previous);
        }
    }

    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool,
            int limit) {
        return collectUltimineTargets(player, seed, toolSlot, linkedTool, limit, player != null && player.isCreative(), (byte) 0);
    }

    private static Deque<BlockPos> collectUltimineTargets(ServerPlayer player, BlockPos seed, int toolSlot, ItemStack linkedTool,
            int limit, boolean creative, byte mode) {
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, seed)) {
            return new ArrayDeque<>();
        }

        ServerLevel level = player.serverLevel();
        List<BlockPos> targets = RtsUltimineCollector.collect(
                level,
                seed,
                limit,
                (candidatePos, state, seedState) -> isUltimineCandidate(
                        player,
                        candidatePos,
                        state,
                        seedState,
                        toolSlot,
                        linkedTool,
                        creative,
                mode));
        return new ArrayDeque<>(targets);
    }

    private static Deque<BlockPos> collectAreaDestroyTargets(ServerPlayer player, List<BlockPos> positions,
            int toolSlot, ItemStack linkedTool, boolean creative) {
        if (player == null || positions == null || positions.isEmpty()) {
            return new ArrayDeque<>();
        }
        ServerLevel level = player.serverLevel();
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        for (BlockPos raw : positions) {
            if (raw == null || unique.size() >= AREA_DESTROY_MAX_TARGETS) {
                continue;
            }
            BlockPos pos = raw.immutable();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            if (!creative && computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool) <= 0.0F) {
                continue;
            }
            unique.add(pos);
        }
        return new ArrayDeque<>(unique);
    }

    private static boolean isUltimineCandidate(
            ServerPlayer player,
            BlockPos pos,
            BlockState state,
            BlockState seedState,
            int toolSlot,
            ItemStack linkedTool,
            boolean creative,
            byte mode) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        if (mode == 0 && state.getBlock() != seedState.getBlock()) {
            return false;
        }
        if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, pos)) {
            return false;
        }
        if (creative) {
            return true;
        }
        if (state.getDestroySpeed(player.serverLevel(), pos) < 0.0F) {
            return false;
        }
        float seedDestroySpeed = seedState.getDestroySpeed(player.serverLevel(), pos);
        float candidateDestroySpeed = state.getDestroySpeed(player.serverLevel(), pos);
        if (seedDestroySpeed >= 0.0F && candidateDestroySpeed > seedDestroySpeed * 1.5F) {
            return false;
        }
        return computeRemoteDestroyStep(player, state, pos, toolSlot, linkedTool) > 0.0F;
    }

    private static void breakCreativeUltimineTargets(ServerPlayer player, RtsStorageSession session, Deque<BlockPos> targets,
            int toolSlot) {
        while (!targets.isEmpty()) {
            BlockPos target = targets.removeFirst();
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            destroyMinedBlock(player, session, target, toolSlot);
        }
    }

    private static void beginRemoteMining(ServerPlayer player, RtsStorageSession session, BlockPos pos, Direction face,
            int toolSlot) {
        if (session.miningPos != null && !session.miningPos.equals(pos)) {
            player.serverLevel().destroyBlockProgress(player.getId(), session.miningPos, -1);
            sendMineProgress(player, session.miningPos, -1);
        }
        session.miningPos = pos.immutable();
        session.miningFace = face == null ? Direction.DOWN : face;
        session.miningToolSlot = clampHotbarSlot(toolSlot);
        session.miningProgress = 0.0F;
        session.miningStage = -1;
    }

    private static void processUltimineTargets(ServerPlayer player, RtsStorageSession session) {
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        int processedThisTick = 0;
        while (processedThisTick < ULTIMINE_BLOCKS_PER_TICK && !session.ultimineTargets.isEmpty()) {
            BlockPos target = session.ultimineTargets.removeFirst();
            processedThisTick++;
            session.ultimineProcessedTargets++;

            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, target)) {
                continue;
            }
            BlockState targetState = level.getBlockState(target);
            if (targetState.isAir() || !targetState.getFluidState().isEmpty()) {
                sendMineProgress(player, target, -1);
                continue;
            }
            if (targetState.getDestroySpeed(level, target) < 0.0F) {
                continue;
            }
            if (computeRemoteDestroyStep(player, targetState, target, session.miningToolSlot, session.miningToolLease.stack()) <= 0.0F) {
                continue;
            }
            boolean targetBroken = destroyMinedBlock(player, session, target, session.miningToolSlot);
            if (targetBroken && session.autoStoreMinedDrops && RtsProgressionManager.canUse(player, RtsFeature.AUTO_STORE_MINED_DROPS)) {
                session.ultimineAbsorbedDrops |= absorbNearbyDropsIntoLinked(player, target, session);
            }
            if (targetBroken) {
            }
        }

        sendUltimineBatchProgress(player, session);
        if (session.ultimineTargets.isEmpty()) {
            finishUltimineBatch(player, session);
        }
    }

    private static void sendUltimineBatchProgress(ServerPlayer player, RtsStorageSession session) {
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos == null) {
            return;
        }
        int total = Math.max(1, session.ultimineTotalTargets);
        int stage = Math.min(9, (int) (session.ultimineProcessedTargets / (double) total * 10.0D));
        sendMineProgress(player, progressPos, stage);
        sendUltimineProgress(player, session.ultimineProcessedTargets, total);
    }

    private static void finishUltimineBatch(ServerPlayer player, RtsStorageSession session) {
        sendUltimineProgress(player, -1, 0);
        if (session.ultimineAbsorbedDrops) {
            RtsStorageManager.runQuestDetect(player, session, false);
        }
        returnMiningTool(player, session, session.miningToolLease);
        scheduleMiningStorageRefresh(player, session);
        BlockPos progressPos = session.ultimineProgressPos;
        if (progressPos != null) {
            player.serverLevel().destroyBlockProgress(player.getId(), progressPos, -1);
            sendMineProgress(player, progressPos, -1);
        }
        resetMiningState(session);
    }

    private static void removeUltimineTarget(RtsStorageSession session, BlockPos pos) {
        session.ultimineTargets.removeIf(target -> target.equals(pos));
    }

    private static void resetMiningState(RtsStorageSession session) {
        session.miningPos = null;
        session.ultimineTargets.clear();
        session.ultimineProgressPos = null;
        session.ultimineTotalTargets = 0;
        session.ultimineProcessedTargets = 0;
        session.ultimineAbsorbedDrops = false;
        session.miningFace = Direction.DOWN;
        session.miningProgress = 0.0F;
        session.miningStage = -1;
        session.miningToolLease = ToolLease.empty();
    }

    private static float computeRemoteDestroyStep(ServerPlayer player, BlockState state, BlockPos pos, int toolSlot,
            ItemStack linkedTool) {
        if (linkedTool != null && !linkedTool.isEmpty()) {
            return withTemporaryOnGround(player, true, () -> withTemporaryMainHandItem(
                    player,
                    linkedTool,
                    () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
        }
        return withTemporaryOnGround(player, true, () -> withTemporarySelectedSlot(
                player,
                toolSlot,
                () -> removeMiningSpeedPenalty(player, state.getDestroyProgress(player, player.serverLevel(), pos))));
    }

    private static boolean destroyMinedBlock(ServerPlayer player, RtsStorageSession session, BlockPos pos, int toolSlot) {
        BlockState beforeState = player.serverLevel().getBlockState(pos);
        boolean broken;
        if (session != null && session.miningToolLease != null && !session.miningToolLease.isEmpty()) {
            ToolLease lease = session.miningToolLease;
            MiningDestroyOutcome outcome = destroyBlockWithTemporaryMainHand(player, pos, lease.stack());
            session.miningToolLease = lease.withStack(protectBorrowedToolRemainder(player, lease, outcome.remainder()));
            broken = outcome.broken();
        } else {
            broken = withTemporarySelectedSlot(player, toolSlot, () -> player.gameMode.destroyBlock(pos));
        }
        if (broken && !beforeState.isAir()) {
            PacketDistributor.sendToPlayer(player, new S2CRtsBreakAnimationPayload(pos.immutable(), beforeState));
        }
        return broken;
    }

    private static ToolLease borrowMiningTool(ServerPlayer player, RtsStorageSession session, String toolItemId,
            ItemStack toolPrototype, int selectedToolSlot) {
        if (player == null || session == null || toolPrototype == null || toolPrototype.isEmpty()
                || toolItemId == null || toolItemId.isBlank()) {
            return ToolLease.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(toolItemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ToolLease.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item instanceof BlockItem || toolPrototype.getItem() != item) {
            return ToolLease.empty();
        }

        ToolLease playerLease = borrowMiningToolFromPlayerInventory(player, toolPrototype, selectedToolSlot);
        if (!playerLease.isEmpty()) {
            return playerLease;
        }

        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (activeLinked.isEmpty()) {
            return ToolLease.empty();
        }
        for (LinkedHandler linked : activeLinked) {
            ToolLease linkedLease = borrowMiningToolFromLinkedHandler(linked.handler(), toolPrototype);
            if (!linkedLease.isEmpty()) {
                return linkedLease;
            }
        }
        return ToolLease.empty();
    }

    private static ToolLease borrowMiningToolFromPlayerInventory(ServerPlayer player, ItemStack prototype, int selectedToolSlot) {
        int selected = clampHotbarSlot(selectedToolSlot);
        int start = RtsStoragePageBuilder.getPlayerMainInventoryStart(player);
        int end = RtsStoragePageBuilder.getPlayerMainInventoryEndExclusive(player);
        for (int slot = start; slot < end; slot++) {
            ToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        for (int slot = 0; slot < PLAYER_HOTBAR_SLOT_COUNT; slot++) {
            if (slot == selected) {
                continue;
            }
            ToolLease lease = borrowMiningToolFromPlayerSlot(player, prototype, slot);
            if (!lease.isEmpty()) {
                return lease;
            }
        }
        return ToolLease.empty();
    }

    private static ToolLease borrowMiningToolFromPlayerSlot(ServerPlayer player, ItemStack prototype, int slot) {
        if (slot < 0 || slot >= player.getInventory().getContainerSize()) {
            return ToolLease.empty();
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.isEmpty() || !ItemStack.isSameItemSameTags(current, prototype)) {
            return ToolLease.empty();
        }
        ItemStack borrowed = current.split(1);
        if (current.isEmpty()) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        } else {
            player.getInventory().setItem(slot, current);
        }
        player.getInventory().setChanged();
        return borrowed.isEmpty() ? ToolLease.empty() : ToolLease.playerSlot(slot, borrowed);
    }

    private static ToolLease borrowMiningToolFromLinkedHandler(IItemHandler handler, ItemStack prototype) {
        if (handler == null || prototype == null || prototype.isEmpty()) {
            return ToolLease.empty();
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, prototype)) {
                continue;
            }
            ItemStack borrowed = handler.extractItem(slot, 1, false);
            if (!borrowed.isEmpty() && ItemStack.isSameItemSameTags(borrowed, prototype)) {
                return ToolLease.linkedSlot(handler, slot, borrowed);
            }
            if (!borrowed.isEmpty()) {
                RtsStorageTransfers.insertToHandlerPreferExisting(handler, borrowed);
            }
        }
        return ToolLease.empty();
    }

    private static void returnMiningTool(ServerPlayer player, RtsStorageSession session, ToolLease lease) {
        if (player == null || session == null || lease == null || lease.isEmpty()) {
            return;
        }
        ItemStack remain = lease.returnToSource(player);
        if (remain.isEmpty()) {
            return;
        }
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = new ArrayList<>(activeLinked.size());
        for (LinkedHandler linked : activeLinked) {
            handlers.add(linked.handler());
        }
        RtsStorageTransfers.storeToLinkedWithFallback(handlers, player, remain);
    }

    private static ItemStack protectBorrowedToolRemainder(ServerPlayer player, ToolLease lease, ItemStack remainder) {
        if (remainder != null && !remainder.isEmpty()) {
            return remainder;
        }
        ItemStack original = lease.original();
        if (!shouldProtectEmptyBorrowedToolRemainder(original)) {
            return ItemStack.EMPTY;
        }
        RtsbuildingMod.LOGGER.warn(
                "RTS borrowed mining tool from {} became empty after block break; restoring original stack as a safety fallback for {}.",
                lease.describeSource(),
                player == null ? "unknown player" : player.getGameProfile().getName());
        return original.copy();
    }

    private static boolean shouldProtectEmptyBorrowedToolRemainder(ItemStack original) {
        return original != null
                && !original.isEmpty()
                && !(original.getItem() instanceof BlockItem)
                && original.getMaxStackSize() == 1
                && !original.isDamageableItem();
    }

    private static MiningDestroyOutcome destroyBlockWithTemporaryMainHand(ServerPlayer player, BlockPos pos, ItemStack tool) {
        ItemStack previousMainHand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        boolean broken;
        ItemStack remainder;
        try {
            broken = player.gameMode.destroyBlock(pos);
        } finally {
            remainder = player.getMainHandItem().copy();
            player.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        }
        return new MiningDestroyOutcome(broken, remainder);
    }

    private static float removeMiningSpeedPenalty(ServerPlayer player, float destroyStep) {
        if (destroyStep <= 0.0F) {
            return destroyStep;
        }
        return destroyStep;
    }
    private static <T> T withTemporarySelectedSlot(ServerPlayer player, int toolSlot, Supplier<T> action) {
        int slot = clampHotbarSlot(toolSlot);
        int prevSelected = player.getInventory().selected;

        player.getInventory().selected = slot;
        try {
            return action.get();
        } finally {
            player.getInventory().selected = prevSelected;
        }
    }

    private static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        PacketDistributor.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    private static void sendUltimineProgress(ServerPlayer player, int processed, int total) {
        PacketDistributor.sendToPlayer(player, new S2CRtsUltimineProgressPayload(processed, total));
    }

    private static int clampHotbarSlot(int slot) {
        return Math.max(0, Math.min(8, slot));
    }

    private static boolean absorbNearbyDropsIntoLinked(ServerPlayer player, BlockPos pos, RtsStorageSession session) {
        if (!RtsLinkedStorageResolver.hasAnyStorage(player, session)) {
            return false;
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        if (linked.isEmpty()) {
            return false;
        }
        List<IItemHandler> handlers = new ArrayList<>(linked.size());
        for (LinkedHandler handler : linked) {
            handlers.add(handler.handler());
        }

        AABB box = new AABB(pos).inflate(1.25D);
        List<ItemEntity> drops = player.serverLevel().getEntitiesOfClass(
                ItemEntity.class,
                box,
                entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty());
        boolean changed = false;
        for (ItemEntity drop : drops) {
            ItemStack original = drop.getItem();
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remain = RtsStorageTransfers.storeToLinkedOnly(handlers, original);
            if (remain.getCount() != original.getCount()) {
                changed = true;
            }
            if (remain.isEmpty()) {
                drop.discard();
            } else if (remain.getCount() != original.getCount()) {
                drop.setItem(remain);
            }
        }
        return changed;
    }

    private record MiningDestroyOutcome(boolean broken, ItemStack remainder) {
    }

    /**
     * Tracks a real borrowed mining tool and the exact destination it should be
     * returned to.
     *
     * <p>The lease keeps a copy of the original stack only for the existing
     * safety fallback when non-damageable single-stack tools unexpectedly come
     * back empty. The live {@code stack} field is the mutable borrowed stack
     * that block breaking has modified, so callers must update it from the
     * temporary main-hand remainder and return that exact remainder to source.
     */
    static final class ToolLease {
        private static final ToolLease EMPTY = new ToolLease(
                ItemStack.EMPTY,
                ItemStack.EMPTY,
                null,
                -1,
                -1,
                "none");

        private final ItemStack original;
        private final ItemStack stack;
        private final IItemHandler linkedHandler;
        private final int linkedSlot;
        private final int playerSlot;
        private final String sourceDescription;

        private ToolLease(ItemStack original, ItemStack stack, IItemHandler linkedHandler, int linkedSlot, int playerSlot,
                String sourceDescription) {
            this.original = original == null || original.isEmpty() ? ItemStack.EMPTY : original.copy();
            this.stack = stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack;
            this.linkedHandler = linkedHandler;
            this.linkedSlot = linkedSlot;
            this.playerSlot = playerSlot;
            this.sourceDescription = sourceDescription == null ? "unknown" : sourceDescription;
        }

        static ToolLease empty() {
            return EMPTY;
        }

        private static ToolLease playerSlot(int slot, ItemStack stack) {
            return new ToolLease(stack, stack, null, -1, slot, "player inventory slot " + slot);
        }

        private static ToolLease linkedSlot(IItemHandler handler, int slot, ItemStack stack) {
            return new ToolLease(stack, stack, handler, slot, -1, "linked storage slot " + slot);
        }

        private boolean isEmpty() {
            return this.stack.isEmpty();
        }

        private ItemStack stack() {
            return this.stack;
        }

        private ItemStack original() {
            return this.original;
        }

        private ToolLease withStack(ItemStack updatedStack) {
            if (this == EMPTY || updatedStack == null || updatedStack.isEmpty()) {
                return new ToolLease(this.original, ItemStack.EMPTY, this.linkedHandler, this.linkedSlot, this.playerSlot, this.sourceDescription);
            }
            return new ToolLease(this.original, updatedStack, this.linkedHandler, this.linkedSlot, this.playerSlot, this.sourceDescription);
        }

        private ItemStack returnToSource(ServerPlayer player) {
            if (this.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack remain = this.stack.copy();
            if (this.playerSlot >= 0) {
                remain = returnToPlayerSlot(player, this.playerSlot, remain);
            } else if (this.linkedHandler != null && this.linkedSlot >= 0) {
                remain = this.linkedHandler.insertItem(this.linkedSlot, remain, false);
            }
            return remain;
        }

        private String describeSource() {
            return this.sourceDescription;
        }

        private static ItemStack returnToPlayerSlot(ServerPlayer player, int slot, ItemStack stack) {
            if (player == null || stack == null || stack.isEmpty()
                    || slot < 0 || slot >= player.getInventory().getContainerSize()) {
                return stack == null ? ItemStack.EMPTY : stack.copy();
            }
            ItemStack remain = stack.copy();
            ItemStack current = player.getInventory().getItem(slot);
            if (current.isEmpty()) {
                player.getInventory().setItem(slot, remain);
                player.getInventory().setChanged();
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(current, remain)) {
                int free = Math.max(0, current.getMaxStackSize() - current.getCount());
                if (free > 0) {
                    int moved = Math.min(free, remain.getCount());
                    current.grow(moved);
                    remain.shrink(moved);
                    player.getInventory().setItem(slot, current);
                    player.getInventory().setChanged();
                }
            }
            return remain;
        }
    }
}
