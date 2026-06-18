package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.RtsMenuRemoteService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.service.api.InteractionService;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsEmptyHandInteractor;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsLinkedItemInteractor;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsToolSlotInteractor;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageRecentEntries;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class RtsInteractionServiceImpl implements InteractionService {

    private static final double REMOTE_POV_BLOCK_REACH = 4.0D;

    private final ServiceRegistry registry = ServiceRegistry.getInstance();

    @Override
    public void interactTarget(ServerPlayer player, int entityId, BlockPos clickedPos, Direction face,
                               double hitX, double hitY, double hitZ,
                               byte sourceType, byte toolSlot, String itemId,
                               double rayOriginX, double rayOriginY, double rayOriginZ,
                               double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.INTERACT)) {
            return;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || !RtsCameraManager.isActive(player)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        RayContext rayContext = TemporaryContextSwitcher.parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        ServerLevel level = player.serverLevel();
        Entity targetEntity = null;
        BlockHitResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        BlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        BlockState beforeAdjacent = null;
        boolean useItemInAir = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR;

        if (entityId >= 0) {
            targetEntity = level.getEntity(entityId);
            if (targetEntity == null || !targetEntity.isAlive()) {
                return;
            }
            effectiveBlockPos = targetEntity.blockPosition();
            if (!level.hasChunkAt(effectiveBlockPos) || !level.mayInteract(player, effectiveBlockPos)) {
                return;
            }
        } else {
            if (clickedPos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, clickedPos)) {
                return;
            }
            effectiveBlockPos = clickedPos.immutable();
            if (!useItemInAir) {
                blockHit = new BlockHitResult(new Vec3(hitX, hitY, hitZ), face, effectiveBlockPos, false);
                beforeClicked = level.getBlockState(effectiveBlockPos);
                adjacentPos = effectiveBlockPos.relative(face);
                beforeAdjacent = level.hasChunkAt(adjacentPos) ? level.getBlockState(adjacentPos) : null;
            }
        }

        InteractionResult result = InteractionResult.PASS;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        if (blockHit != null) {
            RtsRemoteMenuService.sendRemoteMenuOpenHint(player, effectiveBlockPos);
        }
        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT || sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR
                ? player.getInventory().getItem(RtsMiningValidator.clampHotbarSlot(toolSlot)).copy()
                : ItemStack.EMPTY;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? SoundService.createSoundStack(itemId)
                : toolSnapshot.copy();
        AbstractContainerMenu menuBeforeInteract = player.containerMenu;

        if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
            result = RtsToolSlotInteractor.interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR) {
            result = RtsToolSlotInteractor.useItemInAirWithToolSlot(player, level, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
            result = RtsLinkedItemInteractor.interactWithLinkedItem(player, level, session, targetEntity, blockHit, hit, itemId, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_EMPTY_HAND) {
            result = RtsEmptyHandInteractor.interactWithEmptyHand(player, level, targetEntity, blockHit, hit, rayContext);
        }

        AbstractContainerMenu menuAfterInteract = player.containerMenu;
        if (menuAfterInteract != menuBeforeInteract) {
            RtsMenuRemoteService.markOpen(player, session, menuAfterInteract, effectiveBlockPos);
        }

        boolean playedSpecificSound = false;
        if (result.consumesAction() && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(
                    level, effectiveBlockPos, beforeClicked, adjacentPos, beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
                if (!soundStack.isEmpty() && soundStack.getItem() instanceof BlockItem) {
                    RtsPlacementSound.playRemotePlacedBlockAnimation(player, placedPos);
                    RtsPlacementSound.playRemotePlacedBlockSound(player, level, placedPos);
                } else {
                    SoundService.playRemoteUseSound(player, level, targetEntity, placedPos, soundStack);
                }
                playedSpecificSound = true;
            }
        }
        if (result.consumesAction()) {
            if (!playedSpecificSound) {
                SoundService.playRemoteUseSound(player, level, targetEntity, effectiveBlockPos, soundStack);
            }
            if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM && itemId != null && !itemId.isBlank()) {
                RtsStorageRecentEntries.recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
            } else if (!toolSnapshot.isEmpty()) {
                ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(toolSnapshot.getItem());
                if (toolId != null) {
                    RtsStorageRecentEntries.recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        registry.page().requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending, false);
    }
}
