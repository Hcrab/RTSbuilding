package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.capability.IFluidHandler;
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
    public static IItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        if (!player.getLevel().hasChunkAt(pos)) {
            return null;
        }
        var blockEntity = player.getLevel().getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        IItemHandler direct = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler sided = blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).orElse(null);
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
        if (!player.getLevel().hasChunkAt(pos)) {
            return null;
        }
        var blockEntity = player.getLevel().getBlockEntity(pos);
        if (blockEntity == null) {
            return null;
        }
        IFluidHandler direct = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        if (direct != null) {
            return direct;
        }
        for (Direction direction : Direction.values()) {
            IFluidHandler sided = blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, direction).orElse(null);
            if (sided != null) {
                return sided;
            }
        }
        return null;
    }
}
