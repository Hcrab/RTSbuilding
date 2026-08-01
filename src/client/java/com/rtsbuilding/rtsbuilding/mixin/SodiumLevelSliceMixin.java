package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sodium 0.6 区块网格的可选隐藏入口。
 *
 * <p>Sodium 使用 {@code LevelSlice} 在后台线程生成区块网格，会绕过原版
 * {@code RenderChunkRegion}。这里保持纯适配层，只把隐藏位置表现为空气，并且不直接
 * 链接 Sodium 类型，因此未安装 Sodium 时不会形成前置依赖。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
public abstract class SodiumLevelSliceMixin {
    @Inject(
            method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (RtsCullingClientState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
        }
    }

    @Inject(
            method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(
            method = "getBlockEntity(III)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$cullBlockEntity(int x, int y, int z, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsCullingClientState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(null);
        }
    }
}
