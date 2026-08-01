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
 * Embeddium chunk mesh 的可选剔除入口。
 *
 * <p>Embeddium 会使用自己的 WorldSlice 读取 chunk 数据，可能绕过 vanilla
 * {@code BlockRenderDispatcher}。这个 mixin 不直接引用 Embeddium 类型，因此没有安装 Embeddium
 * 时会作为虚拟目标跳过；安装时则让同一个剔除盒判断在它的 mesh 管线中生效。
 */
@Pseudo
@Mixin(targets = "org.embeddedt.embeddium.impl.world.WorldSlice", remap = false)
public abstract class EmbeddiumWorldSliceMixin {
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
