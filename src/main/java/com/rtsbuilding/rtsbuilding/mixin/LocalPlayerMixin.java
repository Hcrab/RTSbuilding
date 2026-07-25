package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RTS 镜头启用时继续让原版发送玩家位置同步包。
 *
 * <p>客户端每 tick 把摄像机实体切到本地镜像相机后，原版
 * {@code LocalPlayer.isControlledCamera()} 会返回 false，继而停止发送玩家移动包。
 * 服务器会看不到击退和寻路造成的真实位置变化，并周期性把玩家拉回旧锚点。
 * 本 Mixin 只在 RTS 已启用时恢复“受控相机”语义，不改普通游戏行为。</p>
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "isControlledCamera", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$isControlledCamera(CallbackInfoReturnable<Boolean> callback) {
        if (ClientRtsController.get().isEnabled()) {
            callback.setReturnValue(true);
        }
    }
}
