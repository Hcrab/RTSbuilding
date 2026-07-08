package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementSound;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * 流体服务——管理流体抽取和放置??
 *
 * <p>职责范围??
 * <ul>
 *   <li>从容器抽取流体存入链接网??/li>
 *   <li>从链接网络放置流体到世界</li>
 * </ul>
 */
public final class RtsFluidService {

    public static final RtsFluidService INSTANCE = new RtsFluidService();

    private RtsFluidService() {
    }

    /**
     * 从容器抽取流体并存入链接网络??
     */
    public static void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getOrCreate(player);
        if (!RtsCameraManager.isActive(player)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeItemHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        List<IItemHandler> extractItemHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeItemHandlers);
        List<IItemHandler> insertItemHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeItemHandlers);

        boolean changed = RtsStorageFluids.storeFluidFromContainer(
                player,
                session,
                extractItemHandlers,
                insertItemHandlers,
                activeFluidHandlers,
                sourceType,
                toolSlot,
                itemId);
        if (changed) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsSessionService.saveToPlayerNbt(player, session);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    /**
     * 从链接网络放置流体到世界??
     */
    public static void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face, double hitX, double hitY,
            double hitZ, boolean forcePlace, String fluidId,
            double rayOriginX, double rayOriginY, double rayOriginZ,
            double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        RtsStorageSession session = RtsSessionService.getIfPresent(player);
        if (session == null || !canAccessFluidPlacementTarget(player, clickedPos)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        if (RtsStorageFluids.placeFluid(player, session, activeFluidHandlers, clickedPos, face, hitX, hitY, hitZ, fluidId)) {
            RtsStorageTickService.INSTANCE.forceRefresh(player);
            session.transfer.pageDataVersion.incrementAndGet();
            RtsSessionService.saveToPlayerNbt(player, session);
            RtsPageService.requestPage(player, session.browser.page, session.browser.search, session.browser.category, session.browser.sort, session.browser.ascending);
        }
    }

    // ======================================================================
    // 内部辅助
    // ======================================================================

    private static boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        Level level = player.serverLevel();
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        if (level.mayInteract(player, pos)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos)) {
            return true;
        }
        if (!level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        if (!level.hasChunkAt(below)) {
            return false;
        }
        return level.mayInteract(player, below)
                && RtsCameraManager.isWithinActionRange(player, pos)
                && RtsProgressionManager.canAccessHomeRadius(player, pos);
    }
}
