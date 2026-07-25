package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * {@link InteractionService} 的默认实现——处理 RTS 模式下与方块/实体的远程交互。
 *
 * <p>该实现类根据 {@code sourceType} 将交互请求分发给不同的交互器：
 * <ul>
 *   <li>{@link com.rtsbuilding.rtsbuilding.server.service.interaction.RtsToolSlotInteractor}——工具槽交互</li>
 *   <li>{@link com.rtsbuilding.rtsbuilding.server.service.interaction.RtsLinkedItemInteractor}——链接物品交互</li>
 *   <li>{@link com.rtsbuilding.rtsbuilding.server.service.interaction.RtsEmptyHandInteractor}——空手交互</li>
 * </ul>
 * 同时处理远程菜单追踪、放置方块检测、音效播放和最近物品记录。
 */
public final class RtsInteractionServiceImpl implements InteractionService {

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

        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT || sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR
                ? player.getInventory().getItem(RtsMiningValidator.clampHotbarSlot(toolSlot)).copy()
                : ItemStack.EMPTY;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? SoundService.createSoundStack(itemId)
                : toolSnapshot.copy();
        ItemStack protectionStack = soundStack.isEmpty() ? ItemStack.EMPTY : soundStack.copyWithCount(1);
        if (targetEntity != null && !RtsClaimProtectionService.canInteractEntity(
                player, targetEntity, InteractionHand.MAIN_HAND, protectionStack, false)) {
            return;
        }
        if (blockHit != null) {
            Direction hitFace = blockHit.getDirection();
            if (!RtsClaimProtectionService.canInteractBlock(
                    player, effectiveBlockPos, hitFace, InteractionHand.MAIN_HAND, protectionStack)) {
                return;
            }
            if (!protectionStack.isEmpty() && protectionStack.getItem() instanceof BlockItem
                    && !RtsClaimProtectionService.canPlaceBlock(
                            player, interactionPlacementTarget(level, effectiveBlockPos, hitFace))) {
                return;
            }
        }

        InteractionResult result = InteractionResult.PASS;
        Vec3 hit = new Vec3(hitX, hitY, hitZ);
        if (blockHit != null) {
            RtsRemoteMenuService.sendRemoteMenuOpenHint(player, effectiveBlockPos);
        }
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
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterInteract, effectiveBlockPos);
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
                registry.page().recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
            } else if (!toolSnapshot.isEmpty()) {
                ResourceLocation toolId = BuiltInRegistries.ITEM.getKey(toolSnapshot.getItem());
                if (toolId != null) {
                    registry.page().recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        registry.page().requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending, false);
    }

    private static BlockPos interactionPlacementTarget(ServerLevel level, BlockPos clickedPos, Direction face) {
        if (level.hasChunkAt(clickedPos) && level.getBlockState(clickedPos).canBeReplaced()) {
            return clickedPos;
        }
        return clickedPos.relative(face);
    }
}
