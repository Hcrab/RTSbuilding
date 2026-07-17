package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

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
    public static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        if (!player.level().hasChunkAt(pos)) {
            return null;
        }
        IItemHandler direct = player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
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
    public static IItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
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
    public static IFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        if (!player.level().hasChunkAt(pos)) {
            return null;
        }
        IFluidHandler direct = player.level().getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = player.level().getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }
}
