package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraRenderSync;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 补齐 Forge 1.20.1 缺少帧前渲染事件的适配层。
 *
 * <p>这里只负责在整帧渲染入口推进一次视觉镜头，不拥有镜头状态，也不改变世界渲染阶段。
 * 这样可以与 1.21.1 的 {@code RenderFrameEvent.Pre} 保持相同的时序契约。</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render(FJZ)V", at = @At("HEAD"))
    private void rtsbuilding$syncVisualCameraBeforeFrame(float partialTick, long nanoTime,
            boolean renderLevel, CallbackInfo ci) {
        RtsCameraRenderSync.beforeRenderFrame();
    }
}
