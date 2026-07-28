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
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandler;

import java.util.List;

/**
 * {@link FluidService} 的默认实现——处理远程流体抽取和放置操作。
 *
 * <p>该实现类协调多个系统组件：
 * <ul>
 *   <li>由 {@link RtsStorageFluids} 执行容器模拟/执行、真实余物回收和世界放置；</li>
 *   <li>由 {@link RtsLinkedStorageResolver} 解析具有正确提取/插入权限的物品与流体处理器；</li>
 *   <li>在进入底层前统一检查进度、相机、区块、世界修改权限和操作距离。</li>
 * </ul>
 *
 * <p>本类不自行重做 Forge 流体能力操作，避免服务层丢失容器 metadata/NBT 或模拟结果；
 * 成功修改后统一交给服务操作模板刷新缓存与储存页面。
 */
public final class RtsFluidServiceImpl implements FluidService {

    private final ServiceRegistry registry = ServiceRegistry.getInstance();
    private final FluidTransferGate fluidTransferGate = new RtsFluidTransferGateImpl();

    @Override
    public void storeFluidFromContainer(EntityPlayerMP player, byte sourceType, byte toolSlot, String itemId) {
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
            registry.serviceOp().afterModification(player, session);
        }
    }

    @Override
    public void placeFluid(EntityPlayerMP player, BlockPos clickedPos, EnumFacing face,
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

    private boolean canAccessFluidPlacementTarget(EntityPlayerMP player, BlockPos pos) {
        if (!RtsCameraManager.isActive(player) || pos == null) {
            return false;
        }
        WorldServer level = player.getServerWorld();
        if (!level.isBlockLoaded(pos)) {
            return false;
        }
        if (level.isBlockModifiable(player, pos)
                && RtsCameraManager.isWithinActionRange(player, pos)) {
            return true;
        }
        if (!level.isAirBlock(pos)) {
            return false;
        }
        BlockPos below = pos.down();
        if (!level.isBlockLoaded(below)) {
            return false;
        }
        return level.isBlockModifiable(player, below)
                && RtsCameraManager.isWithinActionRange(player, pos);
    }
}
