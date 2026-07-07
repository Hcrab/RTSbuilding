package com.rtsbuilding.rtsbuilding.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端范围剔除：隐藏盒内箱子、机器等方块实体渲染。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void rtsbuilding$skipCulledBlockEntity(E blockEntity, float partialTick,
            PoseStack poseStack, MultiBufferSource bufferSource, CallbackInfo ci) {
        if (blockEntity != null && RtsCullingClientState.shouldCull(blockEntity.getBlockPos())) {
            ci.cancel();
        }
    }
}
