package com.rtsbuilding.rtsbuilding.server.storage.port;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 流体容器与世界交互的 Loader 边界。
 *
 * <p>业务层只描述“排空容器、填充目标、解析位置、放置流体”四种意图。
 * capability 查询、汽化规则和 Loader 流体栈必须留在平台实现中。</p>
 */
public interface RtsFluidPlatformBridge {
    RtsFluidContainerDrain drainContainer(ItemStack container, int amount, boolean execute);

    int fillTarget(
            ServerLevel level, BlockPos clickedPos, Direction face, RtsFluidVolume volume);

    BlockPos resolvePlacementPos(
            ServerLevel level, ServerPlayer player, BlockHitResult hit, RtsFluidVolume volume);

    boolean placeFluid(
            ServerLevel level,
            ServerPlayer player,
            BlockPos pos,
            RtsFluidVolume volume,
            BlockHitResult placementHit);
}
