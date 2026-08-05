package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.storage.InventoryItemHandler;
import com.rtsbuilding.rtsbuilding.platform.storage.IFluidHandler;
import com.rtsbuilding.rtsbuilding.platform.storage.NativeFluidHandlerAdapter;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;
import net.minecraftforge.common.util.ForgeDirection;

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
        if (player == null || pos == null) {
            return null;
        }
        WorldServer world = player.getServerForPlayer();
        if (!world.blockExists(pos.getX(), pos.getY(), pos.getZ())) {
            return null;
        }
        TileEntity tile = world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
        if (!(tile instanceof IInventory)) {
            return null;
        }

        IInventory inventory = (IInventory) tile;
        if (inventory instanceof ISidedInventory) {
            for (EnumFacing direction : EnumFacing.values()) {
                InventoryItemHandler sided = new InventoryItemHandler(inventory, direction);
                if (sided.getSlots() > 0) return sided;
            }
        }
        return new InventoryItemHandler(inventory);
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
        // Refined Storage 不存在于 1.7.10/GTNH；该版本以 AE2/GT 网络为优先目标。
        return findHandler(player, pos);
    }

    /**
     * 探测方块坐标的流体处理器，先检查直接能力，再检查所有侧面。
     */
    public static IFluidHandler findFluidHandler(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(player.getServerForPlayer(), pos)) {
            return null;
        }
        TileEntity tile = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(player.getServerForPlayer(), pos);
        if (tile == null) {
            return null;
        }
        if (!(tile instanceof net.minecraftforge.fluids.IFluidHandler)) return null;
        net.minecraftforge.fluids.IFluidHandler nativeHandler =
                (net.minecraftforge.fluids.IFluidHandler) tile;

        NativeFluidHandlerAdapter unsided =
                new NativeFluidHandlerAdapter(nativeHandler, ForgeDirection.UNKNOWN);
        if (unsided.getTankProperties().length > 0) return unsided;

        // GT 机器经常只在允许输入/输出的一面报告槽位；选择一个真实可见面，避免重复计数。
        for (EnumFacing direction : EnumFacing.values()) {
            NativeFluidHandlerAdapter sided =
                    new NativeFluidHandlerAdapter(nativeHandler, direction.toForgeDirection());
            if (sided.getTankProperties().length > 0) return sided;
        }
        // 某些实现不报告 FluidTankInfo，却仍允许操作；保留 UNKNOWN 回退而不是误判为非流体机器。
        return unsided;
    }
}
