package com.rtsbuilding.rtsbuilding.compat.ae2;

import com.rtsbuilding.rtsbuilding.platform.item.RtsItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * AE2 的 Fabric 平台边界。
 *
 * <p>AE2 没有 1.21.1 Fabric 发行物，原专属处理器依赖其 NeoForge BlockCapability。
 * Fabric 线保留稳定调用契约，并让通用 Fabric Transfer 探测继续作为后备；不存在兼容模组时
 * 绝不加载 AE2 类。后续若 AE2 恢复 Fabric，可只替换本类实现。
 */
public final class RtsAe2Compat {
    public interface ReportedCountItemHandler extends com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler {
    }

    public interface AnySlotInsertItemHandler extends com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler {
    }

    private RtsAe2Compat() {
    }

    public static boolean isAvailable() {
        return false;
    }

    public static RtsItemHandler createNetworkItemHandler(ServerPlayer player, BlockPos pos) {
        return null;
    }

    public static long getReportedCount(RtsItemHandler handler, int slot, ItemStack fallbackStack) {
        if (handler instanceof com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler reported) {
            return Math.max(0L, reported.getReportedCount(slot));
        }
        return fallbackStack == null || fallbackStack.isEmpty()
                ? 0L : Math.max(0L, fallbackStack.getCount());
    }

    public static void releaseNetworkHandler(RtsItemHandler handler) {
    }

    public static String resolveGuiBindingIconItemId(
            Level level, BlockPos pos, Direction face, String labelHint) {
        return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, labelHint);
    }
}
