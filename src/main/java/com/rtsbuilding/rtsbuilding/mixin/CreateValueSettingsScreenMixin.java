package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.compat.create.RtsCreateValueSettingsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 仅替换 RTS 打开的 Create ValueSettingsScreen 的最终发送出口。
 *
 * <p>注入点位于 saveAndClose 的开头：普通 Create 屏幕不会被取消，原 NetworkHelper 路径保持原样；
 * 只有由 RTS 适配器构造并登记的屏幕才提交专用 payload 并取消原方法，从而不触发 Create 原生的近距限制。
 * 目标类不存在时 @Pseudo 会跳过，不形成 Create 编译期依赖。</p>
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen", remap = false)
public final class CreateValueSettingsScreenMixin {
    @Inject(method = "saveAndClose", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void rtsbuilding$saveRtsValueSettings(double mouseX, double mouseY, CallbackInfo callback) {
        if (RtsCreateValueSettingsCompat.submitNativeScreenSave(this, mouseX, mouseY)) {
            callback.cancel();
        }
    }

    @Inject(method = "onClose", at = @At("TAIL"), require = 0, remap = false)
    private void rtsbuilding$finishRtsValueSettingsScreen(CallbackInfo callback) {
        RtsCreateValueSettingsCompat.finishNativeScreen(this);
    }
}
