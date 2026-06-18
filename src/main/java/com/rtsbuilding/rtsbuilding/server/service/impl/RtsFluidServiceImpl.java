package com.rtsbuilding.rtsbuilding.server.service.impl;

import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.service.ServiceOperationTemplate;
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
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

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
        List<IItemHandler> extractItemHandlers = RtsLinkedStorageResolver.itemHandlersForExtract(activeItemHandlers);
        List<IItemHandler> insertItemHandlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeItemHandlers);

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
            ServiceOperationTemplate.afterModification(player, session);
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
            ServiceOperationTemplate.afterModification(player, session);
        }
    }

    // ────────────────────────────────────────────────────────────────
    //  Internal helpers
    // ────────────────────────────────────────────────────────────────

    private boolean canAccessFluidPlacementTarget(ServerPlayer player, BlockPos pos) {
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
