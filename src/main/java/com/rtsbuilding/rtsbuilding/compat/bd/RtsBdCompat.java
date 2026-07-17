package com.rtsbuilding.rtsbuilding.compat.bd;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Beyond Dimensions 的 26.1 兼容边界。
 *
 * <p>该模组目前没有可用于 NeoForge 26.1 的公开编译 API，因此核心工程不能继续
 * 直接链接它的类型。这里保留 RTSBuilding 内部稳定入口，让 Vanilla 储存、客户端
 * 和专用服务端能够独立迁移；将来只需要替换这一边界的后端，不必再次修改业务层。</p>
 */
public final class RtsBdCompat {
    /**
     * 储存提取服务使用的窄语义，不暴露第三方 key、bucket 或 network 类型。
     */
    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }

    private RtsBdCompat() {
    }

    /**
     * 26.1 后端尚未接入时必须返回 false，避免仅检测到模组 id 就进入不可用路径。
     */
    public static boolean isAvailable() {
        return false;
    }

    public static boolean hasPrimaryNetwork(ServerPlayer player) {
        return false;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player) {
        return null;
    }

    public static IFluidHandler createNetworkFluidHandler(ServerPlayer player) {
        return null;
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
        // 26.1 后端尚未接入，没有第三方缓存需要释放。
    }

    public static void refreshNetworkHandler(IItemHandler handler) {
        // 26.1 后端尚未接入，没有第三方缓存需要刷新。
    }

    public static String getNetworkDisplayName(ServerPlayer player) {
        return "Beyond Dimensions Network (26.1 API unavailable)";
    }
}
