package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.entity.EntityPlayerSP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * RTS 相机切到镜像实体时，仍让 1.12 的本地玩家发送 CPacketPlayer。
 *
 * <p>1.12 在 {@code EntityPlayerSP#onUpdateWalkingPlayer} 中通过
 * {@code isCurrentViewEntity()} 决定是否发送位置；这与新版的
 * {@code isControlledCamera()} 门禁语义相同。</p>
 */
@Mixin(EntityPlayerSP.class)
abstract class LocalPlayerMixin {
    @Inject(method = "isCurrentViewEntity", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$isCurrentViewEntity(CallbackInfoReturnable<Boolean> cir) {
        if (ClientRtsController.get().isEnabled()) {
            cir.setReturnValue(true);
        }
    }
}
