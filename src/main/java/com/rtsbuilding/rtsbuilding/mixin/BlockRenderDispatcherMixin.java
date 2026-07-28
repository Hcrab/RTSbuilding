package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 客户端范围剔除：在 chunk 方块模型写入缓冲前跳过盒内方块。
 */
@Mixin(BlockRendererDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$skipCulledBlock(IBlockState state, BlockPos pos, IBlockAccess level,
            BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
        if (RtsCullingClientState.shouldCull(pos)) {
            cir.setReturnValue(false);
        }
    }
}
