package com.rtsbuilding.rtsbuilding.server.storage.handler;

import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2Compat;
import com.rtsbuilding.rtsbuilding.compat.refinedstorage.RtsRefinedStorageCompat;
import com.rtsbuilding.rtsbuilding.platform.item.FabricItemHandler;
import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import com.rtsbuilding.rtsbuilding.platform.fluid.FabricFluidHandler;
import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * 在链接存储坐标处探测方块容纳物的物品和流体处理器（Capability）。
 *
 * <p>本类仅持有世界中方块坐标的低级 {@link RtsItemHandler} 和
 * {@link RtsFluidHandler} 能力查询逻辑。它扫描直接和侧面的能力，
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
    public static RtsItemHandler findHandler(ServerPlayer player, BlockPos pos) {
        return player == null ? null : findHandler(player.serverLevel(), pos);
    }

    public static RtsItemHandler findHandler(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        Storage<ItemVariant> direct = ItemStorage.SIDED.find(level, pos, null);
        if (direct != null) {
            return new FabricItemHandler(direct);
        }
        for (Direction direction : Direction.values()) {
            Storage<ItemVariant> sided = ItemStorage.SIDED.find(level, pos, direction);
            if (sided != null) {
                return new FabricItemHandler(sided);
            }
        }
        return null;
    }

    /**
     * 探测方块坐标的物品处理器，优先使用 AE2 / Refined Storage 虚拟网络处理器，
     * 再回退到直接/侧面能力扫描。
     */
    public static RtsItemHandler findLinkedItemHandler(ServerPlayer player, BlockPos pos) {
        return player == null ? null : findLinkedItemHandler(player, player.serverLevel(), pos);
    }

    /**
     * 解析已通过服务端会话与区块校验的链接端点。
     * 这里不重新引入玩家当前维度或距离限制，以免误伤合法的跨维 RTS 储存操作。
     */
    public static RtsItemHandler findLinkedItemHandler(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player == null || level == null || pos == null) {
            return null;
        }
        // Fabric 1.21.1 暂无 AE2 发行；仍保留同维度稳定调用面以便后续支持。
        // 异维度时不能把玩家当前维度的端点冒充为目标端点。
        RtsItemHandler ae2Network = player.serverLevel() == level
                ? RtsAe2Compat.createNetworkItemHandler(player, pos)
                : null;
        if (ae2Network != null) {
            return ae2Network;
        }
        RtsItemHandler refinedStorageNetwork = RtsRefinedStorageCompat.createNetworkItemHandler(level, pos);
        if (refinedStorageNetwork != null) {
            return refinedStorageNetwork;
        }
        return findHandler(level, pos);
    }

    /**
     * 探测方块坐标的流体处理器，先检查直接能力，再检查所有侧面。
     */
    public static RtsFluidHandler findFluidHandler(ServerPlayer player, BlockPos pos) {
        return player == null ? null : findFluidHandler(player.serverLevel(), pos);
    }

    public static RtsFluidHandler findFluidHandler(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        Storage<FluidVariant> direct = FluidStorage.SIDED.find(level, pos, null);
        if (direct != null) {
            return new FabricFluidHandler(direct);
        }
        for (Direction direction : Direction.values()) {
            Storage<FluidVariant> sided = FluidStorage.SIDED.find(level, pos, direction);
            if (sided != null) {
                return new FabricFluidHandler(sided);
            }
        }
        return null;
    }
}
