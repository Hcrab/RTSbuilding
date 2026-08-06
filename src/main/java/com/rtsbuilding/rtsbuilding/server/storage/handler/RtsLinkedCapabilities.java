package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * 在链接存储坐标处探测方块容纳物的物品和流体处理器（Capability）。
 *
 * <p>本类仅持有世界中方块坐标的低级 {@link IItemHandler} 和
 * {@link IFluidHandler} 能力查询逻辑。它扫描直接和侧面的能力，
 * 并在适用时委托给 AE2 虚拟网络处理器。
 *
 * <p>它刻意不解析会话引用、构建存储页面、转移物品/流体、
 * 修改物品栏或管理权限。这些职责保留在 {@link RtsLinkedStorageResolver}
 * 和其他存储辅助类中。
 */
public final class RtsLinkedCapabilities {
    private RtsLinkedCapabilities() {
    }

    /**
     * 探测方块坐标的物品处理器，先检查直接能力，再检查所有侧面。
     */
    public static IItemHandler findHandler(EntityPlayerMP player, BlockPos pos) {
        return player == null ? null : findHandlerInLevel(player.getServerWorld(), pos);
    }

    /** 在指定世界探测原生容器 capability，不隐式加载区块。 */
    public static IItemHandler findHandlerInLevel(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !level.isBlockLoaded(pos)) {
            return null;
        }
        TileEntity tile = level.getTileEntity(pos);
        if (tile == null) {
            return null;
        }
        IItemHandler direct = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (direct != null) {
            return direct;
        }
        for (EnumFacing direction : EnumFacing.values()) {
            IItemHandler sided = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }

    /**
     * 探测方块坐标的物品处理器，优先使用 AE2 / Refined Storage 虚拟网络处理器，
     * 再回退到直接/侧面能力扫描。
     */
    public static IItemHandler findLinkedItemHandler(EntityPlayerMP player, BlockPos pos) {
        IItemHandler ae2Network = RtsAe2Compat.createNetworkItemHandler(player, pos);
        if (ae2Network != null) {
            return ae2Network;
        }
        IItemHandler refinedStorageNetwork = RtsRefinedStorageCompat.createNetworkItemHandler(player, pos);
        if (refinedStorageNetwork != null) {
            return refinedStorageNetwork;
        }
        return findHandler(player, pos);
    }

    /**
     * 探测方块坐标的流体处理器，先检查直接能力，再检查所有侧面。
     */
    public static IFluidHandler findFluidHandler(EntityPlayerMP player, BlockPos pos) {
        return player == null ? null : findFluidHandlerInLevel(player.getServerWorld(), pos);
    }

    /** 在指定世界探测原生流体 capability，不隐式加载区块。 */
    public static IFluidHandler findFluidHandlerInLevel(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !level.isBlockLoaded(pos)) {
            return null;
        }
        TileEntity tile = level.getTileEntity(pos);
        if (tile == null) {
            return null;
        }
        IFluidHandler direct = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
        if (direct != null) {
            return direct;
        }
        for (EnumFacing direction : EnumFacing.values()) {
            IFluidHandler sided = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }
}
