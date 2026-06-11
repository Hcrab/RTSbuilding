package com.rtsbuilding.rtsbuilding.server.storage.placement;

import java.util.List;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.history.ServerHistoryManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;

import com.rtsbuilding.rtsbuilding.server.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Single interactive placement execution for RTS storage builder.
 *
 * <p>This helper owns the core {@link #placeSelectedInternal} state machine
 * that handles a single remote placement — whether from the player's main
 * hand or from a selected storage item. It manages the full placement flow:
 * skip-if-occupied prechecks, use-on-block attempt, use-item-as-fallback
 * attempt, extraction from network/linked storage, placement detection,
 * rotation, and recent-item recording.
 *
 * <p>It deliberately does not manage batch-job queuing, pre-resolved
 * quick-build placement, item extraction primitives, or sound/effect
 * dispatch — those responsibilities live in their dedicated helpers.
 */
public final class RtsPlacementExecutor {
    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;

    private RtsPlacementExecutor() {
    }

    /**
     * Attempts to place a single block at the given position using the remote
     * RTS placement machinery.
     *
     * <p>When {@code itemId} is blank or null the method uses the player's
     * main-hand item (interactive placement). When {@code itemId} is set the
     * method extracts the item from linked storage or player inventory.
     *
     * @param player              the server player
     * @param session             the player's RTS storage session
     * @param clickedPos          target block position
     * @param face                clicked face
     * @param hitX,hitY,hitZ      hit-location coordinates
     * @param rotateSteps         number of 90-degree clockwise rotations
     * @param forcePlace          whether to simulate shift-click (force place)
     * @param skipIfOccupied      skip positions that are already occupied
     * @param itemId              storage item id (blank/null for main-hand)
     * @param itemPrototype       preferred prototype stack for extraction
     * @param rayDirY ray context for reach extension
     * @param quickBuild          {@code true} when this is part of a quick-build batch
     * @param refreshStoragePage  {@code true} to trigger a storage page refresh
     * @param sendRemoteHint      {@code true} to send the menu-open hint packet
     * @return {@code true} if the position was handled and the batch should
     *         continue, {@code false} to abort the current batch job
     */
    public static boolean placeSelectedInternal(ServerPlayer player, RtsStorageSession session, BlockPos clickedPos,
                                                Direction face, double hitX, double hitY, double hitZ, byte rotateSteps, boolean forcePlace,
                                                boolean skipIfOccupied, String itemId, ItemStack itemPrototype, double rayOriginX, double rayOriginY,
                                                double rayOriginZ, double rayDirX, double rayDirY, double rayDirZ, boolean quickBuild,
                                                boolean forceEmptyHand, boolean refreshStoragePage, boolean sendRemoteHint) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.REMOTE_PLACE)) {
            return false;
        }
        if (session == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, clickedPos) || face == null) {
            return false;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        boolean useSelectedStorageItem = itemId != null && !itemId.isBlank();

        ServerLevel level = player.serverLevel();
        Vec3 hitLocation = new Vec3(hitX, hitY, hitZ);
        BlockHitResult hit = new BlockHitResult(hitLocation, face, clickedPos, false);
        Vec3 interactionPos = RtsStorageManager.resolveInteractionPosition(null, hit, hitLocation);
        RtsStorageManager.RayContext rayContext = RtsStorageManager.parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);
        if (sendRemoteHint) {
            RtsStorageManager.sendRemoteMenuOpenHint(player, clickedPos);
        }

        if (!useSelectedStorageItem) {
            if (forceEmptyHand) {
                return placeWithForcedEmptyHand(player, session, level, clickedPos, hit, interactionPos, rayContext,
                        forcePlace);
            }
            return false;
        }

        return placeWithStorageItem(player, session, level, clickedPos, face, hit, interactionPos, rayContext,
                rotateSteps, skipIfOccupied, forcePlace, itemId, itemPrototype, refreshStoragePage);
    }

    private static boolean placeWithForcedEmptyHand(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, BlockHitResult hit, Vec3 interactionPos, RtsStorageManager.RayContext rayContext,
            boolean forcePlace) {
        AbstractContainerMenu menuBeforeEmptyUse = player.containerMenu;
        RtsStorageManager.UseOnOutcome emptyUse = RtsStorageManager.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> RtsStorageManager.useItemOnWithMainHand(player, level, ItemStack.EMPTY, hit, forcePlace));
        AbstractContainerMenu menuAfterEmptyUse = player.containerMenu;
        if (menuAfterEmptyUse != menuBeforeEmptyUse) {
            RtsStorageManager.markRemoteMenuOpen(player, session, menuAfterEmptyUse, clickedPos);
            return false;
        }

        if (emptyUse.result().consumesAction()) {
            RtsStorageManager.saveSessionToPlayerNbt(player, session);
            return true;
        }

        AbstractContainerMenu menuBeforeEmptyFallback = player.containerMenu;
        RtsStorageManager.UseOnOutcome emptyFallback = RtsStorageManager.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> RtsStorageManager.useItemWithMainHand(player, level, ItemStack.EMPTY, forcePlace));
        AbstractContainerMenu menuAfterEmptyFallback = player.containerMenu;
        if (menuAfterEmptyFallback != menuBeforeEmptyFallback) {
            RtsStorageManager.markRemoteMenuOpen(player, session, menuAfterEmptyFallback, clickedPos);
            return false;
        }
        if (emptyFallback.result().consumesAction()) {
            RtsStorageManager.saveSessionToPlayerNbt(player, session);
            return true;
        }
        return false;
    }


    private static boolean placeWithStorageItem(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, Direction face, BlockHitResult hit,
            Vec3 interactionPos, RtsStorageManager.RayContext rayContext, byte rotateSteps, boolean skipIfOccupied,
            boolean forcePlace, String itemId, ItemStack itemPrototype, boolean refreshStoragePage) {
        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        boolean includePlayerMainInventory = RtsStoragePageBuilder.shouldIncludePlayerMainInventoryInStorageView(player, session);
        boolean creativeSource = player.isCreative();
        if (activeLinked.isEmpty() && !includePlayerMainInventory && !creativeSource) {
            return false;
        }

        List<IItemHandler> extractHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeLinked);
        List<IItemHandler> insertHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return false;
        }

        Item item = BuiltInRegistries.ITEM.get(id);
        ItemStack preferredStack = RtsPlacementExtractor.sanitizePrototype(itemId, itemPrototype);
        if (skipIfOccupied && item instanceof BlockItem) {
            if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
                return true;
            }
        }
        ItemStack extracted = creativeSource
                ? RtsPlacementExtractor.creativeStack(item, preferredStack)
                : includePlayerMainInventory
                        ? RtsPlacementExtractor.extractSelectedFromNetwork(extractHandlers, player, item, preferredStack)
                        : RtsPlacementExtractor.extractSelectedFromLinked(extractHandlers, item, preferredStack);
        if (extracted.isEmpty()) {
            RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
            return false;
        }
        ItemStack selectedSoundStack = extracted.copy();
        boolean selectedPlacesBlock = item instanceof BlockItem;

        BlockState beforeClicked = level.getBlockState(clickedPos);
        BlockPos adjacentPos = clickedPos.relative(face);
        BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

        AbstractContainerMenu menuBeforeSelectedUse = player.containerMenu;
        RtsStorageManager.UseOnOutcome selectedOutcome = RtsStorageManager.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> RtsStorageManager.useItemOnWithMainHand(player, level, extracted, hit, forcePlace));
        AbstractContainerMenu menuAfterSelectedUse = player.containerMenu;
        if (menuAfterSelectedUse != menuBeforeSelectedUse) {
            RtsStorageManager.markRemoteMenuOpen(player, session, menuAfterSelectedUse, clickedPos);
        }

        RtsStorageManager.UseOnOutcome finalOutcome = selectedOutcome;
        if (!selectedOutcome.result().consumesAction()) {
            ItemStack fallbackStack = selectedOutcome.remainder().isEmpty() ? extracted.copy() : selectedOutcome.remainder().copy();
            finalOutcome = RtsStorageManager.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> RtsStorageManager.useItemWithMainHand(player, level, fallbackStack, forcePlace));
        }
        if (!creativeSource && !finalOutcome.remainder().isEmpty()) {
            RtsStorageTransfers.refundToLinked(insertHandlers, player, finalOutcome.remainder());
        }

        if (!finalOutcome.result().consumesAction()) {
            RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
            return false;
        }

        BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
        if (placedPos != null) {
            RtsPlacementHelper.rotatePlacedBlock(level, placedPos, rotateSteps);
            PlacedBlockTrackerData.get(level).mark(placedPos);
            if (selectedPlacesBlock) {
                RtsPlacementSound.playRemotePlacedBlockAnimation(player, placedPos);
                RtsPlacementSound.playRemotePlacedBlockSound(player, level, placedPos);
            } else {
                RtsStorageManager.playRemoteUseSound(player, level, null, placedPos, selectedSoundStack);
            }
            RtsStorageManager.recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        } else {
            RtsStorageManager.playRemoteUseSound(player, level, null, clickedPos, selectedSoundStack);
            RtsStorageManager.recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
        }

        RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
        return true;
    }


}
