package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.compat.jade.RtsJadePlugin;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在屏幕打开期间把真实 GLFW 按键动作转交给 Jade 兼容入口。
 *
 * <p>该混入由 Jade 条件插件控制，仅在 Jade 已安装时应用；它不取消原版屏幕输入，
 * 只补回 NeoForge 输入事件原先为 Jade 快捷键提供的 clickCount 通知。
 */
@Mixin(KeyboardHandler.class)
public final class JadeKeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void rtsbuilding$forwardBuilderScreenJadeKey(
            long window, int keyCode, int scanCode, int action, int modifiers, CallbackInfo callback) {
        RtsJadePlugin.onBuilderScreenKeyPressed(keyCode, scanCode, action);
    }
}
