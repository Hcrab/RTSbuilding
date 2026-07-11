package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldSliceBridge;
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
 * Embeddium 1.20.1 区块网格的隐藏入口。
 *
 * <p>1.20.1 的 Embeddium 仍使用 Sodium 旧包名下的 {@code WorldSlice}。这里不直接
 * 链接可选模组类型，并同时声明开发映射名和 Forge 生产环境的 SRG 名，保证隐藏判断
 * 在开发环境与整合包中都进入同一条方块、流体和方块实体读取链路。
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.world.WorldSlice", remap = false)
public abstract class EmbeddiumWorldSliceMixin implements RtsCullingWorldSliceBridge {
    @Inject(
            method = {
                    "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                    "m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
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
            require = 1,
            remap = false)
    private void rtsbuilding$cullBlockState(int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
        if (RtsCullingClientState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(
            method = {
                    "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
                    "m_6425_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            remap = false)
    private void rtsbuilding$cullFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
        }
    }

    @Inject(
            method = {
                    "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    "m_7702_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
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
            require = 1,
            remap = false)
    private void rtsbuilding$cullBlockEntity(int x, int y, int z, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsCullingClientState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(null);
        }
    }
}
