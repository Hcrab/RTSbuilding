package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.compat.create.RtsCreateValueSettingsCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 只接管由 RTS 长按打开的 Create 数值设置页保存动作。
 *
 * <p>普通 Create 设置页仍走原生网络路径；Create 未安装时，{@link Pseudo} 和字符串目标
 * 保证本模组不会硬加载任何 Create 客户端类。</p>
 */
@Pseudo
@Mixin(
        targets = "com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen",
        remap = false)
abstract class CreateValueSettingsScreenMixin {

    @Inject(
            method = "saveAndClose",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void rtsbuilding$saveRemoteValueSettings(
            double mouseX, double mouseY, CallbackInfo callback) {
        if (RtsCreateValueSettingsCompat.submitNativeScreenSave(
                this, mouseX, mouseY)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "onClose",
            at = @At("TAIL"),
            require = 0,
            remap = false)
    private void rtsbuilding$finishRemoteValueSettings(CallbackInfo callback) {
        RtsCreateValueSettingsCompat.finishNativeScreen(this);
    }
}
