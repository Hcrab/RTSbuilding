package com.rtsbuilding.rtsbuilding.compat.refinedstorage;

import com.rtsbuilding.rtsbuilding.platform.item.FabricItemHandler;
import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Refined Storage Fabric 的通用 Transfer API 桥。
 *
 * <p>1.21.1 Fabric 发行物不使用 NeoForge NetworkNode capability；这里先确认目标确实属于
 * Refined Storage，再从其公开的 Fabric 物品储存接口解析。若某种网络节点未暴露 Transfer API，
 * 返回 null 并由普通容器探测继续处理，不制造虚假网络内容。
 */
public final class RtsRefinedStorageCompat {
    private RtsRefinedStorageCompat() {
    }

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded("refinedstorage");
    }

    public static boolean isNetworkNodePosition(ServerPlayer player, BlockPos pos) {
        return isRefinedStorageBlock(player, pos) && createNetworkItemHandler(player, pos) != null;
    }

    public static RtsItemHandler createNetworkItemHandler(ServerPlayer player, BlockPos pos) {
        if (!isRefinedStorageBlock(player, pos)) {
            return null;
        }
        Storage<ItemVariant> direct = ItemStorage.SIDED.find(player.serverLevel(), pos, null);
        if (direct != null) {
            return new FabricItemHandler(direct);
        }
        for (Direction direction : Direction.values()) {
            Storage<ItemVariant> sided = ItemStorage.SIDED.find(player.serverLevel(), pos, direction);
            if (sided != null) {
                return new FabricItemHandler(sided);
            }
        }
        return null;
    }

    private static boolean isRefinedStorageBlock(ServerPlayer player, BlockPos pos) {
        if (!isAvailable() || player == null || pos == null || !player.serverLevel().hasChunkAt(pos)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(player.serverLevel().getBlockState(pos).getBlock());
        return id != null && "refinedstorage".equals(id.getNamespace());
    }
}
