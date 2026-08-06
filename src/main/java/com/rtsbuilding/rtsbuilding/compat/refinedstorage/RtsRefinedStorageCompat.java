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
import net.minecraft.server.level.ServerLevel;
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
        return player == null ? null : createNetworkItemHandler(player.serverLevel(), pos);
    }

    /**
     * 从明确的目标世界解析 Refined Storage 端点。
     *
     * <p>远程储存的权限、区块唤醒与会话校验由调用者的链接解析边界统一处理；
     * 这里只做已经定位的网络节点的 Transfer API 适配，不将玩家当前所在维度误当作限制。</p>
     */
    public static RtsItemHandler createNetworkItemHandler(ServerLevel level, BlockPos pos) {
        if (!isRefinedStorageBlock(level, pos)) {
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
     * 批量链接只在确认目标为 Refined Storage 方块后才读取 Transfer API 的端点身份。
     * Fabric 没有可稳定链接的公开网络节点能力；因此仅当多个端点实际暴露同一个
     * Storage 对象时才合并，无法证明同网时宁可保留端点，不能误把独立容器去重。
     */
    public static BatchNetworkProbe probeBatchNetwork(ServerLevel level, BlockPos pos) {
        if (!isRefinedStorageBlock(level, pos)) {
            return null;
        }
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos, null);
        if (storage == null) {
            for (Direction direction : Direction.values()) {
                storage = ItemStorage.SIDED.find(level, pos, direction);
                if (storage != null) {
                    break;
                }
            }
        }
        if (storage == null) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        String path = id == null ? "" : id.getPath();
        return new BatchNetworkProbe(storage, path.contains("grid") || path.contains("terminal"));
    }

    /** 单次扫描使用对象身份，不依赖第三方 Storage 的 equals 语义。 */
    public record BatchNetworkProbe(Object identity, boolean preferredTerminal) {
    }

    private static boolean isRefinedStorageBlock(ServerPlayer player, BlockPos pos) {
        return player != null && isRefinedStorageBlock(player.serverLevel(), pos);
    }

    private static boolean isRefinedStorageBlock(ServerLevel level, BlockPos pos) {
        if (!isAvailable() || level == null || pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return id != null && "refinedstorage".equals(id.getNamespace());
    }
}
