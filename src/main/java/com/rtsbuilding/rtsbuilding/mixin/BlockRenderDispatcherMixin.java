package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端范围剔除：在方块模型写入 chunk 缓冲前跳过盒内方块。
 */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(
            method = {
                    "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V",
                    "m_234355_(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void rtsbuilding$skipCulledBlockLegacy(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random,
            CallbackInfo ci) {
        if (RtsCullingClientState.shouldCull(pos)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void rtsbuilding$skipCulledBlock(BlockState state, BlockPos pos, BlockAndTintGetter level,
            PoseStack poseStack, VertexConsumer consumer, boolean checkSides, RandomSource random,
            ModelData modelData, RenderType renderType, CallbackInfo ci) {
        if (RtsCullingClientState.shouldCull(pos)) {
            ci.cancel();
        }
    }

    @Inject(method = { "renderLiquid", "m_234363_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$skipCulledLiquid(BlockPos pos, BlockAndTintGetter level, VertexConsumer buffer,
            BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (RtsCullingClientState.shouldCull(pos)) {
            ci.cancel();
        }
    }
}
