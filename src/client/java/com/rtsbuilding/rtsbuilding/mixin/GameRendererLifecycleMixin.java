package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.camera.RtsCameraRenderSync;
import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在原版相机采样前推进 RTS 帧姿态，并在 RTS 模式下隐藏第一人称手部。 */
@Mixin(GameRenderer.class)
public abstract class GameRendererLifecycleMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void rtsbuilding$beforeRender(
            DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
        RtsCameraRenderSync.syncBeforeRender();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$renderItemInHand(
            Camera camera, float partialTick, Matrix4f projectionMatrix, CallbackInfo callback) {
        if (RtsClientInputGate.suppressHandRendering()) {
            callback.cancel();
        }
    }
}
