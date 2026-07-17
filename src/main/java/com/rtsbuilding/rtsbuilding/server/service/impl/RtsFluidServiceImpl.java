package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.api.FluidService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsFluidTransferGateImpl;
import com.rtsbuilding.rtsbuilding.server.storage.FluidTransferGate;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedFluidHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

import java.util.List;

/**
 * {@link FluidService} 的默认实现——处理远程流体抽取和放置操作。
 *
 * <p>该实现类协调多个系统组件：
 * <ul>
 *   <li>使用 {@link com.rtsbuilding.rtsbuilding.server.storage.RtsStorageFluids} 执行实际的流体存取逻辑</li>
 *   <li>使用 {@link com.rtsbuilding.rtsbuilding.server.service.resolver.RtsLinkedHandlerResolutionService} 解析流体处理器</li>
 *   <li>使用 {@link com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager} 检查相机状态</li>
 * </ul>
 */
public final class RtsFluidServiceImpl implements FluidService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();
    private final FluidTransferGate fluidTransferGate = new RtsFluidTransferGateImpl();

    @Override
    public void storeFluidFromContainer(ServerPlayer player, byte sourceType, byte toolSlot, String itemId) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        RtsStorageSession session = registry.session().getOrCreate(player);
        if (!RtsCameraManager.isActive(player)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);

        List<LinkedHandler> activeItemHandlers = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        List<RtsItemStorage> extractItemHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeItemHandlers);
        List<RtsItemStorage> insertItemHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeItemHandlers);

        boolean changed = RtsStorageFluids.storeFluidFromContainer(
                fluidTransferGate,
                player,
                session,
                extractItemHandlers,
                insertItemHandlers,
                activeFluidHandlers,
                sourceType,
                toolSlot,
                itemId);
        if (changed) {
            registry.serviceOp().afterModification(player, session);
        }
    }

    @Override
    public void placeFluid(ServerPlayer player, BlockPos clickedPos, Direction face,
                           double hitX, double hitY, double hitZ, boolean forcePlace, String fluidId,
                           double rayOriginX, double rayOriginY, double rayOriginZ,
                           double rayDirX, double rayDirY, double rayDirZ) {
        if (!RtsProgressionManager.canUse(player, RtsFeature.FLUID_HANDLING)) {
            return;
        }
        RtsStorageSession session = registry.session().getIfPresent(player);
        if (session == null || !canAccessFluidPlacementTarget(player, clickedPos)) {
            return;
        }
        RtsLinkedStorageResolver.sanitizeSessionDimension(player, session);
        List<LinkedFluidHandler> activeFluidHandlers = RtsLinkedStorageResolver.resolveLinkedFluidHandlers(player, session);
        if (RtsStorageFluids.placeFluid(player, session, activeFluidHandlers, clickedPos, face, hitX, hitY, hitZ, fluidId)) {
            registry.serviceOp().afterModification(player, session);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        Level level = player.level();
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
