package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.RtsRemoteMenuService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.SoundService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStoragePageBuilder;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.util.InteractionHelper;
import com.rtsbuilding.rtsbuilding.server.util.TemporaryContextSwitcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
 * 单方块远程放置执行器，管理交互式放置的完整流程。
 *
 * <p>核心方法 {@link #placeSelectedInternal} 是一个状态机，处理从物品提取到放置完成的
 * 完整远程放置流程：跳过已占用检查→对方块使用尝试→物品回退→
 * 从网络/链接存储提取→使用物品→放置检测→方块旋转→
 * 音效/动画播放→最近物品记录。
 *
 * <p><b>两种模式：</b>
 * <ul>
 *   <li><b>强制空手</b>（{@link #placeWithForcedEmptyHand}）— 
 *   用于与方块交互（如打开箱子、按钮），使用空手触发交互</li>
 *   <li><b>存储物品放置</b>（{@link #placeWithStorageItem}）— 
 *   从链接存储或聚合缓存提取物品后放置到世界中</li>
 * </ul>
 *
 * <p>不负责：批处理作业排队（{@link RtsPlacementBatch}）、
 * 快速建造预解析（{@link RtsPlacementQuickBuild}）、
 * 物品提取原语（{@link RtsPlacementExtractor}）、
 * 音效分发（{@link RtsPlacementSound}/{@link com.rtsbuilding.rtsbuilding.server.service.SoundService}）。
 */
public final class RtsPlacementExecutor {
    private RtsPlacementExecutor() {
    }

    /**
     * 尝试使用远程 RTS 放置机制在给定位置放置单个方块。
     *
     * <p>当 {@code itemId} 为空白或 null 时，方法使用玩家的主手物品（交互式放置）。
     * 当设置了 {@code itemId} 时，方法从链接储存或玩家背包中提取物品。
     *
     * @param player              服务端玩家
     * @param session             玩家的 RTS 储存会话
     * @param clickedPos          目标方块位置
     * @param face                点击的面
     * @param hitX,hitY,hitZ      点击位置坐标
     * @param rotateSteps         顺时针 90 度旋转步数
     * @param forcePlace          是否模拟 Shift 点击（强制放置）
     * @param skipIfOccupied      跳过已被占用的位置
     * @param itemId              储存物品 id（空白/null 为主手）
     * @param itemPrototype       提取的首选原型堆叠
     * @param rayDirY 用于延伸射程的射线上下文
     * @param quickBuild          {@code true} 当这是快速建造批次的一部分时
     * @param refreshStoragePage  {@code true} 触发储存页面刷新
     * @param sendRemoteHint      {@code true} 发送菜单打开提示数据包
     * @return {@code true} 如果位置已处理且批次应继续，{@code false} 中止当前批处理作业
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
        if (!RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, hit.getDirection(), InteractionHand.MAIN_HAND, ItemStack.EMPTY)) {
            return false;
        }
        AbstractContainerMenu menuBeforeEmptyUse = player.containerMenu;
        TemporaryContextSwitcher.UseOnOutcome emptyUse = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                Config.remotePovBlockReach(),
                () -> InteractionHelper.useItemOnWithMainHand(player, level, ItemStack.EMPTY, hit, forcePlace));
        AbstractContainerMenu menuAfterEmptyUse = player.containerMenu;
        if (menuAfterEmptyUse != menuBeforeEmptyUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterEmptyUse, clickedPos);
            return false;
        }

        if (emptyUse.result().consumesAction()) {
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
            return true;
        }

        AbstractContainerMenu menuBeforeEmptyFallback = player.containerMenu;
        TemporaryContextSwitcher.UseOnOutcome emptyFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                Config.remotePovBlockReach(),
                () -> InteractionHelper.useItemWithMainHand(player, level, ItemStack.EMPTY, forcePlace));
        AbstractContainerMenu menuAfterEmptyFallback = player.containerMenu;
        if (menuAfterEmptyFallback != menuBeforeEmptyFallback) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterEmptyFallback, clickedPos);
            return false;
        }
        if (emptyFallback.result().consumesAction()) {
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
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
        if (!RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, face, InteractionHand.MAIN_HAND, sourceSnapshot)) {
            return false;
        }
        if (sourcePlacesBlock && !RtsClaimProtectionService.canPlaceBlock(
                player, placementTargetPos(level, clickedPos, face))) {
            return false;
        }
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
        TemporaryContextSwitcher.UseOnOutcome mainHandUse = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                Config.remotePovBlockReach(),
                () -> InteractionHelper.useItemOnWithRealMainHand(player, level, hit, forcePlace));
        AbstractContainerMenu menuAfterMainHandUse = player.containerMenu;
        if (menuAfterMainHandUse != menuBeforeMainHandUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterMainHandUse, clickedPos);
            return false;
        }

        if (mainHandUse.result().consumesAction()) {
            recordMainHandResult(player, session, level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent,
                    sourceSnapshot, sourcePlacesBlock);
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
            return true;
        }

        AbstractContainerMenu menuBeforeUseFallback = player.containerMenu;
        TemporaryContextSwitcher.UseOnOutcome mainHandUseFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                player,
                interactionPos,
                hit.getLocation(),
                rayContext,
                Config.remotePovBlockReach(),
                () -> InteractionHelper.useItemWithRealMainHand(player, level, forcePlace));
        AbstractContainerMenu menuAfterUseFallback = player.containerMenu;
        if (menuAfterUseFallback != menuBeforeUseFallback) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterUseFallback, clickedPos);
            return false;
        }
        if (mainHandUseFallback.result().consumesAction()) {
            if (!sourceSnapshot.isEmpty()) {
                SoundService.playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
                ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                if (sourceId != null) {
                    ServiceRegistry.getInstance().page().recordRecentItem(
                            session,
                            sourceId.toString(),
                            S2CRtsStoragePagePayload.RECENT_ITEM_USED,
                            1L);
                }
            }
            ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
            return true;
        }

        if (forcePlace) {
            AbstractContainerMenu menuBeforeInteractFallback = player.containerMenu;
            TemporaryContextSwitcher.UseOnOutcome interactFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    Config.remotePovBlockReach(),
                    () -> InteractionHelper.useItemOnWithRealMainHand(player, level, hit, false));
            AbstractContainerMenu menuAfterInteractFallback = player.containerMenu;
            if (menuAfterInteractFallback != menuBeforeInteractFallback) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterInteractFallback, clickedPos);
                return false;
            }
            if (interactFallback.result().consumesAction()) {
                recordMainHandResult(player, session, level, clickedPos, beforeClicked, adjacentPos, beforeAdjacent,
                        sourceSnapshot, sourcePlacesBlock);
                ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
                return true;
            }

            AbstractContainerMenu menuBeforeItemInteractFallback = player.containerMenu;
            TemporaryContextSwitcher.UseOnOutcome itemInteractFallback = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    Config.remotePovBlockReach(),
                    () -> InteractionHelper.useItemWithRealMainHand(player, level, false));
            AbstractContainerMenu menuAfterItemInteractFallback = player.containerMenu;
            if (menuAfterItemInteractFallback != menuBeforeItemInteractFallback) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterItemInteractFallback, clickedPos);
                return false;
            }
            if (itemInteractFallback.result().consumesAction()) {
                if (!sourceSnapshot.isEmpty()) {
                    SoundService.playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
                    ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
                    if (sourceId != null) {
                        ServiceRegistry.getInstance().page().recordRecentItem(
                                session,
                                sourceId.toString(),
                                S2CRtsStoragePagePayload.RECENT_ITEM_USED,
                                1L);
                    }
                }
                ServiceRegistry.getInstance().session().saveToPlayerNbt(player, session);
                return true;
            }
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
        ItemStack protectionStack = preferredStack.isEmpty() ? new ItemStack(item) : preferredStack.copyWithCount(1);
        if (!RtsClaimProtectionService.canInteractBlock(
                player, clickedPos, face, InteractionHand.MAIN_HAND, protectionStack)) {
            return false;
        }
        if (item instanceof BlockItem && !RtsClaimProtectionService.canPlaceBlock(
                player, placementTargetPos(level, clickedPos, face))) {
            return false;
        }
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
                Config.remotePovBlockReach(),
                () -> InteractionHelper.useItemOnWithMainHand(player, level, extracted, hit, forcePlace));
        AbstractContainerMenu menuAfterSelectedUse = player.containerMenu;
        if (menuAfterSelectedUse != menuBeforeSelectedUse) {
            RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterSelectedUse, clickedPos);
        }

        TemporaryContextSwitcher.UseOnOutcome finalOutcome = selectedOutcome;
        ItemStack lastAttemptStack = extracted.copy();
        if (!selectedOutcome.result().consumesAction()) {
            ItemStack fallbackStack = nextAttemptStack(selectedOutcome, lastAttemptStack);
            lastAttemptStack = fallbackStack.copy();
            AbstractContainerMenu menuBeforeSelectedFallback = player.containerMenu;
            finalOutcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    Config.remotePovBlockReach(),
                    () -> InteractionHelper.useItemWithMainHand(player, level, fallbackStack, forcePlace));
            AbstractContainerMenu menuAfterSelectedFallback = player.containerMenu;
            if (menuAfterSelectedFallback != menuBeforeSelectedFallback) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterSelectedFallback, clickedPos);
            }
        }
        if (forcePlace && !finalOutcome.result().consumesAction()) {
            ItemStack storageInteractStack = nextAttemptStack(finalOutcome, lastAttemptStack);
            lastAttemptStack = storageInteractStack.copy();
            AbstractContainerMenu menuBeforeStorageInteractFallback = player.containerMenu;
            finalOutcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    Config.remotePovBlockReach(),
                    () -> InteractionHelper.useItemOnWithMainHand(player, level, storageInteractStack, hit, false));
            AbstractContainerMenu menuAfterStorageInteractFallback = player.containerMenu;
            if (menuAfterStorageInteractFallback != menuBeforeStorageInteractFallback) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterStorageInteractFallback, clickedPos);
            }
        }
        if (forcePlace && !finalOutcome.result().consumesAction()) {
            ItemStack storageItemInteractStack = nextAttemptStack(finalOutcome, lastAttemptStack);
            AbstractContainerMenu menuBeforeStorageItemInteractFallback = player.containerMenu;
            finalOutcome = TemporaryContextSwitcher.withTemporaryUseItemContext(
                    player,
                    interactionPos,
                    hit.getLocation(),
                    rayContext,
                    Config.remotePovBlockReach(),
                    () -> InteractionHelper.useItemWithMainHand(player, level, storageItemInteractStack, false));
            AbstractContainerMenu menuAfterStorageItemInteractFallback = player.containerMenu;
            if (menuAfterStorageItemInteractFallback != menuBeforeStorageItemInteractFallback) {
                RtsRemoteMenuService.markRemoteMenuOpen(player, session, menuAfterStorageItemInteractFallback, clickedPos);
            }
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
            ServiceRegistry.getInstance().page().recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_PLACED, 1L);
        } else {
            SoundService.playRemoteUseSound(player, level, null, clickedPos, selectedSoundStack);
            ServiceRegistry.getInstance().page().recordRecentItem(session, itemId, S2CRtsStoragePagePayload.RECENT_ITEM_USED, 1L);
        }

        RtsPlacementHelper.requestSessionPage(player, session, refreshStoragePage);
        return true;
    }

    private static ItemStack nextAttemptStack(TemporaryContextSwitcher.UseOnOutcome outcome, ItemStack previousStack) {
        if (outcome != null && !outcome.remainder().isEmpty()) {
            return outcome.remainder().copy();
        }
        return previousStack == null ? ItemStack.EMPTY : previousStack.copy();
    }

    private static BlockPos placementTargetPos(ServerLevel level, BlockPos clickedPos, Direction face) {
        if (level.hasChunkAt(clickedPos) && level.getBlockState(clickedPos).canBeReplaced()) {
            return clickedPos;
        }
        return clickedPos.relative(face);
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
                ServiceRegistry.getInstance().page().recordRecentItem(
                        session,
                        sourceId.toString(),
                        S2CRtsStoragePagePayload.RECENT_ITEM_PLACED,
                        1L);
            }
        } else if (!sourceSnapshot.isEmpty()) {
            SoundService.playRemoteUseSound(player, level, null, clickedPos, sourceSnapshot);
            ResourceLocation sourceId = BuiltInRegistries.ITEM.getKey(sourceSnapshot.getItem());
            if (sourceId != null) {
                ServiceRegistry.getInstance().page().recordRecentItem(
                        session,
                        sourceId.toString(),
                        S2CRtsStoragePagePayload.RECENT_ITEM_USED,
                        1L);
            }
        }
    }


}
