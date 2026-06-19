package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsPageService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.RtsSessionService;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

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
        Vec3 interactionPos = InteractionHelper.resolveInteractionPosition(null, hit, hitLocation);
        TemporaryContextSwitcher.RayContext rayContext = TemporaryContextSwitcher.parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);
        if (sendRemoteHint) {
            RtsRemoteMenuService.sendRemoteMenuOpenHint(player, clickedPos);
        }

        if (!useSelectedStorageItem) {
            if (forceEmptyHand) {
                return placeWithForcedEmptyHand(player, session, level, clickedPos, hit, interactionPos, rayContext,
                        forcePlace);
            }
            // 1.1.3 的普通右键依赖主手路径；itemId 为空时必须继续模拟原版 useItemOn/useItem。
            return placeWithMainHand(player, session, level, clickedPos, face, hit, interactionPos, rayContext,
                    skipIfOccupied, forcePlace, refreshStoragePage);
        }

        return placeWithStorageItem(player, session, level, clickedPos, face, hit, interactionPos, rayContext,
                rotateSteps, skipIfOccupied, forcePlace, itemId, itemPrototype, refreshStoragePage);
    }

    private static boolean placeWithForcedEmptyHand(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, BlockHitResult hit, Vec3 interactionPos, TemporaryContextSwitcher.RayContext rayContext,
            boolean forcePlace) {
        AbstractContainerMenu menuBeforeEmptyUse = player.containerMenu;
        TemporaryContextSwitcher.UseOnOutcome emptyUse = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> InteractionHelper.useItemOnWithMainHand(player, level, ItemStack.EMPTY, hit, forcePlace));
        AbstractContainerMenu menuAfterEmptyUse = player.containerMenu;
        if (menuAfterEmptyUse != menuBeforeEmptyUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterEmptyUse, clickedPos);
            return false;
        }

        if (emptyUse.result().consumesAction()) {
            RtsSessionService.saveToPlayerNbt(player, session);
            return true;
        }

        AbstractContainerMenu menuBeforeEmptyFallback = player.containerMenu;
        TemporaryContextSwitcher.UseOnOutcome emptyFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> InteractionHelper.useItemWithMainHand(player, level, ItemStack.EMPTY, forcePlace));
        AbstractContainerMenu menuAfterEmptyFallback = player.containerMenu;
        if (menuAfterEmptyFallback != menuBeforeEmptyFallback) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterEmptyFallback, clickedPos);
            return false;
        }
        if (emptyFallback.result().consumesAction()) {
            RtsSessionService.saveToPlayerNbt(player, session);
            return true;
        }
        return false;
    }

    private static boolean placeWithMainHand(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, Direction face, BlockHitResult hit,
            Vec3 interactionPos, TemporaryContextSwitcher.RayContext rayContext, boolean skipIfOccupied,
            boolean forcePlace, boolean refreshStoragePage) {
        ItemStack sourceSnapshot = player.getMainHandItem().copy();
        boolean sourcePlacesBlock = sourceSnapshot.getItem() instanceof BlockItem;
        if (skipIfOccupied && sourceSnapshot.getItem() instanceof BlockItem) {
            if (!level.hasChunkAt(clickedPos) || !level.getBlockState(clickedPos).canBeReplaced()) {
                RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
                return true;
            }
        }

        BlockState beforeClicked = level.getBlockState(clickedPos);
        BlockPos adjacentPos = clickedPos.relative(face);
        BlockState beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;

        AbstractContainerMenu menuBeforeMainHandUse = player.containerMenu;
        InteractionResult mainHandUse = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> TemporaryContextSwitcher.withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItemOn(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND,
                        hit)));
        AbstractContainerMenu menuAfterMainHandUse = player.containerMenu;
        if (menuAfterMainHandUse != menuBeforeMainHandUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterMainHandUse, clickedPos);
            return false;
        }

        if (mainHandUse.consumesAction()) {
            recordMainHandResult(player, session, level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent,
                    sourceSnapshot, sourcePlacesBlock);
            RtsSessionService.saveToPlayerNbt(player, session);
            return true;
        }

        AbstractContainerMenu menuBeforeUseFallback = player.containerMenu;
        InteractionResult mainHandUseFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> TemporaryContextSwitcher.withTemporaryShiftKey(player, forcePlace, () -> player.gameMode.useItem(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND)));
        AbstractContainerMenu menuAfterUseFallback = player.containerMenu;
        if (menuAfterUseFallback != menuBeforeUseFallback) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterUseFallback, clickedPos);
            return false;
        }
        if (mainHandUseFallback.consumesAction()) {
            if (!sourceSnapshot.isEmpty()) {
                SoundService.playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
                ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                if (sourceId != null) {
                    RtsPageService.recordRecentItem(
                            session,
                            sourceId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_USED,
                            1L);
                }
            }
            RtsSessionService.saveToPlayerNbt(player, session);
            return true;
        }

        return false;
    }


    private static boolean placeWithStorageItem(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, Direction face, BlockHitResult hit,
            Vec3 interactionPos, TemporaryContextSwitcher.RayContext rayContext, byte rotateSteps, boolean skipIfOccupied,
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
                        : RtsPlacementExtractor.extractSelectedFromLinkedCached(player, extractHandlers, item, preferredStack);
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
        TemporaryContextSwitcher.UseOnOutcome selectedOutcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                REMOTE_POV_BLOCK_REACH,
                () -> InteractionHelper.useItemOnWithMainHand(player, level, extracted, hit, forcePlace));
        AbstractContainerMenu menuAfterSelectedUse = player.containerMenu;
        if (menuAfterSelectedUse != menuBeforeSelectedUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterSelectedUse, clickedPos);
        }

        TemporaryContextSwitcher.UseOnOutcome finalOutcome = selectedOutcome;
        if (!selectedOutcome.result().consumesAction()) {
            ItemStack fallbackStack = selectedOutcome.remainder().isEmpty() ? extracted.copy() : selectedOutcome.remainder().copy();
            finalOutcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    REMOTE_POV_BLOCK_REACH,
                    () -> InteractionHelper.useItemWithMainHand(player, level, fallbackStack, forcePlace));
        }
        if (!creativeSource && !finalOutcome.remainder().isEmpty()) {
            RtsTransferInserter.refundToLinked(insertHandlers, player, finalOutcome.remainder());
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
                SoundService.playRemoteUseSound(player, level, null, placedPos, selectedSoundStack);
            }
            RtsPageService.recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        } else {
            SoundService.playRemoteUseSound(player, level, null, clickedPos, selectedSoundStack);
            RtsPageService.recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
        }

        RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
        return true;
    }

    private static void recordMainHandResult(ServerPlayer player, RtsStorageSession session, ServerLevel level,
            BlockPos clickedPos, BlockState beforeClicked, BlockPos adjacentPos, BlockState beforeAdjacent,
            ItemStack sourceSnapshot, boolean sourcePlacesBlock) {
        BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent);
        if (placedPos != null) {
            PlacedBlockTrackerData.get(level).mark(placedPos);
            if (sourcePlacesBlock) {
                RtsPlacementSound.playRemotePlacedBlockAnimation(player, placedPos);
                RtsPlacementSound.playRemotePlacedBlockSound(player, level, placedPos);
            } else {
                SoundService.playRemoteUseSound(player, level, null, placedPos, sourceSnapshot);
            }
            ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
            if (sourceId != null) {
                RtsPageService.recordRecentItem(
                        session,
                        sourceId.toString(),
                        S2CRtsStoragePagePayload.RECENT_ITEM_PLACED,
                        1L);
            }
        } else if (!sourceSnapshot.isEmpty()) {
            SoundService.playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
            ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
            if (sourceId != null) {
                RtsPageService.recordRecentItem(
                        session,
                        sourceId.toString(),
                        S2CRtsStoragePagePayload.RECENT_ITEM_USED,
                        1L);
            }
        }
    }


}
