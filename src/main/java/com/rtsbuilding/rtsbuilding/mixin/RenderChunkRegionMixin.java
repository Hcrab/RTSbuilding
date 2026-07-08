package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 范围剔除的 vanilla chunk 编译入口。
 *
 * <p>chunk mesh 读取方块状态时，剔除盒内的位置直接表现为空气。这样不仅能跳过模型渲染，
 * 也能避免流体和方块实体被加入本次编译结果。
 */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {
    @Inject(method = { "getBlockState", "m_8055_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$cullBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = { "getFluidState", "m_6425_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$cullFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState().getFluidState());
        }
    }

    @Inject(method = { "getBlockEntity", "m_7702_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$cullBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(null);
        }
    }
}
