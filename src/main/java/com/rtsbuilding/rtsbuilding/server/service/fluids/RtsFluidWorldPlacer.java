package com.rtsbuilding.rtsbuilding.server.service.fluids;

import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidPlatform;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsFluidVolume;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 世界流体交互的业务门面。
 *
 * <p>该类只保留 RTS 调用语义，不查询 capability，也不处理 Loader 流体栈。
 * NeoForge 的目标容器查找、汽化和实际方块状态转换由已安装的平台桥完成。
 * 保留这个门面可让旧版本反哺时维持现有调用位置。</p>
 */
public final class RtsFluidWorldPlacer {
    private RtsFluidWorldPlacer() {
    }

    public static int fillFluidHandlerAtTarget(
            ServerLevel level, BlockPos clickedPos, Direction face, RtsFluidVolume volume) {
        return RtsFluidPlatform.bridge().fillTarget(level, clickedPos, face, volume);
    }

    public static BlockPos resolveFluidPlacementPos(
            ServerLevel level, ServerPlayer player, BlockHitResult hit, RtsFluidVolume volume) {
        return RtsFluidPlatform.bridge().resolvePlacementPos(level, player, hit, volume);
    }

    public static boolean placeFluidBlock(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            RtsFluidVolume volume,
            BlockHitResult placementHit) {
        return RtsFluidPlatform.bridge().placeFluid(level, player, pos, volume, placementHit);
    }
}
