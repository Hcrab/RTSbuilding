package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 Minecraft 原生的客户端-服务端同步机制正常工作。
 * <p>
 * RTS 模式每 tick 调用 {@code minecraft.setCameraEntity(localMirrorCamera)} 切换摄像机，
 * 导致 {@code isControlledCamera()} 返回 {@code false}，客户端停止发送
 * {@link net.minecraft.network.protocol.game.ServerboundMovePlayerPacket}。
 * 服务器因此无法知道玩家实体被击退/移动后的真实位置，会定期把玩家复位到锚点。
 * <p>
 * 通过此 Mixin，即使摄像机实体不是玩家，也让客户端继续发送位置包，
 * 从而正确同步位置、旋转、onGround 等所有状态。
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$isControlledCamera(CallbackInfoReturnable<Boolean> cir) {
        CameraModule cam = RtsClientKernel.get().module("camera");
        if (cam != null && cam.getState().isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
