package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 客户端范围剔除：在 1.7.10 的 RenderBlocks 写入 Tessellator 前跳过盒内方块。 */
@Mixin(RenderBlocks.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBlockByRenderType", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$skipCulledBlock(Block block, int x, int y, int z,
            CallbackInfoReturnable<Boolean> cir) {
        if (RtsCullingClientState.shouldCull(new BlockPos(x, y, z))) {
            cir.setReturnValue(false);
        }
    }
}
