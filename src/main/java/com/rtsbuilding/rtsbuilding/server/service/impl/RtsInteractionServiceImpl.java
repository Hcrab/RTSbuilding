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
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;

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
    public void interactTarget(EntityPlayerMP player, int entityId, BlockPos clickedPos, EnumFacing face,
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

        WorldServer level = player.getServerWorld();
        Entity targetEntity = null;
        RayTraceResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        IBlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        IBlockState beforeAdjacent = null;
        boolean useItemInAir = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR;

        if (entityId >= 0) {
            targetEntity = level.getEntityByID(entityId);
            if (targetEntity == null || !targetEntity.isEntityAlive()) {
                return;
            }
            effectiveBlockPos = targetEntity.getPosition();
            if (!level.isBlockLoaded(effectiveBlockPos) || !level.isBlockModifiable(player, effectiveBlockPos)) {
                return;
            }
        } else {
            if (clickedPos == null || !RtsLinkedStorageResolver.canAccessWorldTarget(player, clickedPos)) {
                return;
            }
            effectiveBlockPos = clickedPos.toImmutable();
            if (!useItemInAir) {
                blockHit = new RayTraceResult(
                        new Vec3d(hitX, hitY, hitZ),
                        face == null ? EnumFacing.UP : face,
                        effectiveBlockPos);
                beforeClicked = level.getBlockState(effectiveBlockPos);
                adjacentPos = effectiveBlockPos.offset(blockHit.sideHit);
                beforeAdjacent = level.isBlockLoaded(adjacentPos) ? level.getBlockState(adjacentPos) : null;
            }
        }

        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT || sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR
                ? player.inventory.getStackInSlot(RtsMiningValidator.clampHotbarSlot(toolSlot)).copy()
                : ItemStack.EMPTY;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? SoundService.createSoundStack(itemId)
                : toolSnapshot.copy();
        ItemStack protectionStack = oneItemCopy(soundStack);
        if (targetEntity != null && !RtsClaimProtectionService.canInteractEntity(
                player, targetEntity, EnumHand.MAIN_HAND, protectionStack, false)) {
            return;
        }
        if (blockHit != null) {
            EnumFacing hitFace = blockHit.sideHit;
            if (!RtsClaimProtectionService.canInteractBlock(
                    player, effectiveBlockPos, hitFace, EnumHand.MAIN_HAND, protectionStack)) {
                return;
            }
            if (!protectionStack.isEmpty() && protectionStack.getItem() instanceof ItemBlock
                    && !RtsClaimProtectionService.canPlaceBlock(
                            player, interactionPlacementTarget(level, effectiveBlockPos, hitFace))) {
                return;
            }
        }

        EnumActionResult result = EnumActionResult.PASS;
        Vec3d hit = new Vec3d(hitX, hitY, hitZ);
        if (blockHit != null) {
            RtsRemoteMenuService.sendRemoteMenuOpenHint(player, effectiveBlockPos);
        }
        Container menuBeforeInteract = player.openContainer;

        if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
            result = RtsToolSlotInteractor.interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR) {
            result = RtsToolSlotInteractor.useItemInAirWithToolSlot(player, level, hit, toolSlot, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
            result = RtsLinkedItemInteractor.interactWithLinkedItem(player, level, session, targetEntity, blockHit, hit, itemId, rayContext);
        } else if (sourceType == C2SRtsInteractPayload.SOURCE_EMPTY_HAND) {
            result = RtsEmptyHandInteractor.interactWithEmptyHand(player, level, targetEntity, blockHit, hit, rayContext);
        }

        Container menuAfterInteract = player.openContainer;
        if (menuAfterInteract != menuBeforeInteract) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterInteract, effectiveBlockPos);
        }

        boolean playedSpecificSound = false;
        if (consumesAction(result) && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(
                    level, effectiveBlockPos, beforeClicked, adjacentPos, beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
                if (!soundStack.isEmpty() && soundStack.getItem() instanceof ItemBlock) {
                    RtsPlacementSound.playRemotePlacedBlockAnimation(player, placedPos);
                    RtsPlacementSound.playRemotePlacedBlockSound(player, level, placedPos);
                } else {
                    SoundService.playRemoteUseSound(player, level, targetEntity, placedPos, soundStack);
                }
                playedSpecificSound = true;
            }
        }
        if (consumesAction(result)) {
            if (!playedSpecificSound) {
                SoundService.playRemoteUseSound(player, level, targetEntity, effectiveBlockPos, soundStack);
            }
            if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                    && itemId != null && !itemId.trim().isEmpty()) {
                registry.page().recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
            } else if (!toolSnapshot.isEmpty()) {
                ResourceLocation toolId = Item.REGISTRY.getNameForObject(toolSnapshot.getItem());
                if (toolId != null) {
                    registry.page().recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        registry.page().requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending, false);
    }

    private static BlockPos interactionPlacementTarget(WorldServer level, BlockPos clickedPos, EnumFacing face) {
        if (level.isBlockLoaded(clickedPos)
                && level.getBlockState(clickedPos).getBlock().isReplaceable(level, clickedPos)) {
            return clickedPos;
        }
        return clickedPos.offset(face == null ? EnumFacing.UP : face);
    }

    /** 保护模组只需要一个用于判权的副本，绝不能缩减真实工具或储存堆。 */
    private static ItemStack oneItemCopy(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /** 1.12.2 没有 consumesAction；SUCCESS 是唯一表示动作已被消费的结果。 */
    private static boolean consumesAction(EnumActionResult result) {
        return result == EnumActionResult.SUCCESS;
    }
}
