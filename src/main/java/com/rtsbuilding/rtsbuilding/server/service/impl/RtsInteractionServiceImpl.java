package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsRemoteMenuResultPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteInteractionResult;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.service.api.InteractionService;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsEmptyHandInteractor;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsLinkedItemInteractor;
import com.rtsbuilding.rtsbuilding.server.service.interaction.RtsToolSlotInteractor;
import com.rtsbuilding.rtsbuilding.server.service.mining.RtsMiningValidator;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementHelper;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementExtractor;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher.RayContext;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumActionResult;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.interaction.EnumHand;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
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
    public RtsRemoteInteractionResult interactTarget(EntityPlayerMP player, int entityId, BlockPos clickedPos, EnumFacing face,
                               double hitX, double hitY, double hitZ,
                               byte sourceType, byte toolSlot, String itemId, ItemStack itemPrototype,
                               double rayOriginX, double rayOriginY, double rayOriginZ,
                               double rayDirX, double rayDirY, double rayDirZ,
                               long traceId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.INTERACT)) {
            return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_PROGRESSION_LOCKED,
                    "PROGRESSION_LOCKED");
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || !RtsCameraManager.isActive(player)) {
            return rejected(traceId, session == null
                    ? S2CRtsRemoteMenuResultPayload.REASON_NO_SESSION
                    : S2CRtsRemoteMenuResultPayload.REASON_RTS_INACTIVE,
                    session == null ? "NO_SESSION" : "RTS_INACTIVE");
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        RayContext rayContext = TemporaryContextSwitcher.parseRayContext(
                rayOriginX, rayOriginY, rayOriginZ,
                rayDirX, rayDirY, rayDirZ);

        WorldServer level = player.getServerForPlayer();
        Entity targetEntity = null;
        RayTraceResult blockHit = null;
        BlockPos effectiveBlockPos = null;
        BlockState beforeClicked = null;
        BlockPos adjacentPos = null;
        BlockState beforeAdjacent = null;
        boolean useItemInAir = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR;
        boolean preparedRemoteChunk = false;

        if (entityId >= 0) {
            targetEntity = level.getEntityByID(entityId);
            if (targetEntity == null || !targetEntity.isEntityAlive()) {
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_TARGET_MISSING,
                        "TARGET_MISSING");
            }
            effectiveBlockPos = com.rtsbuilding.rtsbuilding.platform.player.PlayerCompat.blockPosition(targetEntity);
            if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, effectiveBlockPos)
                    || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockModifiable(level, player, effectiveBlockPos)) {
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_TARGET_UNAVAILABLE,
                        "TARGET_UNAVAILABLE");
            }
        } else {
            if (clickedPos == null || !RtsCameraManager.isWithinActionRange(player, clickedPos)
                    || clickedPos.getY() < 0 || clickedPos.getY() >= level.getHeight()) {
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_OUT_OF_RANGE,
                        "OUT_OF_RANGE");
            }
            preparedRemoteChunk = RtsRemoteMenuService.prepareTargetChunk(player, clickedPos, traceId);
            if (!RtsLinkedStorageResolver.canAccessWorldTarget(player, clickedPos)) {
                if (preparedRemoteChunk) {
                    RtsRemoteMenuService.releasePreparedTarget(player, traceId, "TARGET_UNAVAILABLE");
                }
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_TARGET_UNAVAILABLE,
                        "TARGET_UNAVAILABLE");
            }
            effectiveBlockPos = clickedPos.toImmutable();
            if (!useItemInAir) {
                blockHit = new RayTraceResult(
                        new Vec3d(hitX, hitY, hitZ),
                        face == null ? EnumFacing.UP : face,
                        effectiveBlockPos);
                beforeClicked = BlockState.fromWorld(level, effectiveBlockPos);
                adjacentPos = effectiveBlockPos.offset(blockHit.sideHit);
                beforeAdjacent = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, adjacentPos) ? BlockState.fromWorld(level, adjacentPos) : null;
            }
        }

        ItemStack toolSnapshot = sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT || sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR
                ? com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.copyOrNull(
                        player.inventory.getStackInSlot(RtsMiningValidator.clampHotbarSlot(toolSlot)))
                : null;
        ItemStack selectedPrototype = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? RtsPlacementExtractor.sanitizePrototype(itemId, itemPrototype)
                : null;
        ItemStack soundStack = sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM
                ? (selectedPrototype == null ? SoundService.createSoundStack(itemId) : selectedPrototype.copy())
                : com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.copyOrNull(toolSnapshot);
        ItemStack protectionStack = oneItemCopy(soundStack);
        if (targetEntity != null && !RtsClaimProtectionService.canInteractEntity(
                player, targetEntity, EnumHand.MAIN_HAND, protectionStack, false)) {
            return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_CLAIM_DENIED,
                    "CLAIM_DENIED_ENTITY");
        }
        if (blockHit != null) {
            EnumFacing hitFace = blockHit.sideHit;
            if (!RtsClaimProtectionService.canInteractBlock(
                    player, effectiveBlockPos, hitFace, EnumHand.MAIN_HAND, protectionStack)) {
                if (preparedRemoteChunk) {
                    RtsRemoteMenuService.releasePreparedTarget(player, traceId, "CLAIM_DENIED");
                }
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_CLAIM_DENIED,
                        "CLAIM_DENIED_BLOCK");
            }
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(protectionStack) && protectionStack.getItem() instanceof ItemBlock
                    && !RtsClaimProtectionService.canPlaceBlock(
                            player, interactionPlacementTarget(level, effectiveBlockPos, hitFace))) {
                if (preparedRemoteChunk) {
                    RtsRemoteMenuService.releasePreparedTarget(player, traceId, "CLAIM_DENIED");
                }
                return rejected(traceId, S2CRtsRemoteMenuResultPayload.REASON_CLAIM_DENIED,
                        "CLAIM_DENIED_PLACE");
            }
        }

        EnumActionResult result = EnumActionResult.PASS;
        Vec3d hit = new Vec3d(hitX, hitY, hitZ);
        if (blockHit != null) {
            RtsRemoteMenuService.sendRemoteMenuOpenHint(player, effectiveBlockPos, traceId);
        }
        Container menuBeforeInteract = player.openContainer;
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=S event=INTERACTION_BEGIN trace={} kind=REMOTE_GUI target={} source={} menuBefore={} windowBefore={}",
                RtsTraceIds.format(traceId), effectiveBlockPos, sourceName(sourceType),
                menuName(menuBeforeInteract), windowId(menuBeforeInteract));

        try {
            if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT) {
                result = RtsToolSlotInteractor.interactWithToolSlot(player, level, targetEntity, blockHit, hit, toolSlot, rayContext);
            } else if (sourceType == C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR) {
                result = RtsToolSlotInteractor.useItemInAirWithToolSlot(player, level, hit, toolSlot, rayContext);
            } else if (sourceType == C2SRtsInteractPayload.SOURCE_PIN_ITEM) {
                result = RtsLinkedItemInteractor.interactWithLinkedItem(
                        player, level, session, targetEntity, blockHit, hit,
                        itemId, selectedPrototype, rayContext);
            } else if (sourceType == C2SRtsInteractPayload.SOURCE_EMPTY_HAND) {
                result = RtsEmptyHandInteractor.interactWithEmptyHand(player, level, targetEntity, blockHit, hit, rayContext);
            }
        } catch (RuntimeException | LinkageError failure) {
            if (preparedRemoteChunk) {
                RtsRemoteMenuService.releasePreparedTarget(player, traceId, "EXCEPTION");
            }
            RtsbuildingMod.LOGGER.error(
                    "[RTS-TRACE] side=S event=INTERACTION_FAILED trace={} kind=REMOTE_GUI target={} failure={}",
                    RtsTraceIds.format(traceId), effectiveBlockPos, failure.getClass().getName(), failure);
            throw failure;
        }

        Container menuAfterInteract = player.openContainer;
        RtsRemoteInteractionResult interactionResult;
        if (menuAfterInteract != menuBeforeInteract) {
            RtsRemoteMenuService.markRemoteMenuOpen(
                    player, session, menuAfterInteract, effectiveBlockPos, traceId);
            preparedRemoteChunk = false;
            interactionResult = RtsRemoteInteractionResult.menuOpened(menuAfterInteract.windowId);
        } else {
            interactionResult = RtsRemoteInteractionResult.noMenu(
                    consumesAction(result)
                            ? S2CRtsRemoteMenuResultPayload.REASON_ACTION_CONSUMED
                            : S2CRtsRemoteMenuResultPayload.REASON_NO_EFFECT);
        }
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=S event=INTERACTION_RETURN trace={} kind=REMOTE_GUI result={} menuAfter={} windowAfter={} changed={}",
                RtsTraceIds.format(traceId), result, menuName(menuAfterInteract),
                windowId(menuAfterInteract), menuAfterInteract != menuBeforeInteract);

        boolean playedSpecificSound = false;
        if (consumesAction(result) && blockHit != null && beforeClicked != null) {
            BlockPos placedPos = RtsPlacementHelper.detectPlacedPos(
                    level, effectiveBlockPos, beforeClicked, adjacentPos, beforeAdjacent);
            if (placedPos != null) {
                PlacedBlockTrackerData.get(level).mark(placedPos);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(soundStack) && soundStack.getItem() instanceof ItemBlock) {
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
            } else if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(toolSnapshot)) {
                ResourceLocation toolId = com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries.ITEMS.getNameForObject(toolSnapshot.getItem());
                if (toolId != null) {
                    registry.page().recordRecentItem(session, toolId.toString(), S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
                }
            }
        }

        registry.page().requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending, false);
        if (preparedRemoteChunk) {
            RtsRemoteMenuService.releasePreparedTarget(player, traceId, "NO_MENU");
        }
        return interactionResult;
    }

    private static RtsRemoteInteractionResult rejected(long traceId, short reason, String stage) {
        RtsbuildingMod.LOGGER.warn(
                "[RTS-TRACE] side=S event=INTERACTION_REJECTED trace={} kind=REMOTE_GUI reason={} stage={}",
                RtsTraceIds.format(traceId), S2CRtsRemoteMenuResultPayload.reasonName(reason), stage);
        return RtsRemoteInteractionResult.rejected(reason);
    }

    private static String sourceName(byte sourceType) {
        switch (sourceType) {
            case C2SRtsInteractPayload.SOURCE_TOOL_SLOT: return "TOOL_SLOT";
            case C2SRtsInteractPayload.SOURCE_PIN_ITEM: return "PINNED_ITEM";
            case C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR: return "TOOL_SLOT_AIR";
            case C2SRtsInteractPayload.SOURCE_EMPTY_HAND: return "EMPTY_HAND";
            default: return "UNKNOWN";
        }
    }

    private static String menuName(Container menu) {
        return menu == null ? "null" : menu.getClass().getName();
    }

    private static int windowId(Container menu) {
        return menu == null ? -1 : menu.windowId;
    }

    private static BlockPos interactionPlacementTarget(WorldServer level, BlockPos clickedPos, EnumFacing face) {
        if (com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, clickedPos)
                && com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isReplaceable(level, clickedPos)) {
            return clickedPos;
        }
        return clickedPos.offset(face == null ? EnumFacing.UP : face);
    }

    /** 保护模组只需要一个用于判权的副本，绝不能缩减真实工具或储存堆。 */
    private static ItemStack oneItemCopy(ItemStack stack) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    /** 1.12.2 没有 consumesAction；SUCCESS 是唯一表示动作已被消费的结果。 */
    private static boolean consumesAction(EnumActionResult result) {
        return result == EnumActionResult.SUCCESS;
    }
}
