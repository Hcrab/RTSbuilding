package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在字符被分派给焦点控件前补齐 Fabric Screen API 尚未暴露的码点事件。
 *
 * <p>只有 RTS 容器叠层真正消费字符时才取消原分派；普通聊天框、搜索框和其他模组屏幕
 * 均保持原版输入路径，非 BMP 字符也以完整 Unicode 码点传入内部路由。
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardCharInputMixin {
    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$routeScreenCharacter(
            long window, int codePoint, int modifiers, CallbackInfo callback) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            return;
        }
        RtsScreenEvent.CharacterTyped.Pre event = new RtsScreenEvent.CharacterTyped.Pre(
                screen, codePoint, modifiers);
        RtsClientInputGate.onScreenCharTyped(event);
        if (event.isCanceled()) {
            callback.cancel();
        }
    }
}
