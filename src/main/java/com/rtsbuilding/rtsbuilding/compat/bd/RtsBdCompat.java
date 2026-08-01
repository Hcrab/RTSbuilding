package com.rtsbuilding.rtsbuilding.compat.bd;

import com.rtsbuilding.rtsbuilding.platform.fluid.RtsFluidHandler;
import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Beyond Dimensions 的 Fabric 平台边界。
 *
 * <p>截至本移植基线，Beyond Dimensions 1.21.1 只发布 NeoForge 版本，其公共 API 也直接
 * 暴露 NeoForge capability。Fabric 线因此明确报告不可用，不伪造网络或吞掉物品；如果该模组
 * 以后发布 Fabric API，应在本边界新增真实适配器而不改业务层。
 */
public final class RtsBdCompat {
    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }

    private RtsBdCompat() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static boolean hasPrimaryNetwork(ServerPlayer player) {
        return false;
    }

    public static RtsItemHandler createNetworkItemHandler(ServerPlayer player) {
        return null;
    }

    public static RtsFluidHandler createNetworkFluidHandler(ServerPlayer player) {
        return null;
    }

    public static void releaseNetworkHandler(RtsItemHandler handler) {
    }

    public static void refreshNetworkHandler(RtsItemHandler handler) {
    }

    public static String getNetworkDisplayName(ServerPlayer player) {
        return "Beyond Dimensions Network";
    }
}
