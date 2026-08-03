package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputEvents1122;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 1.12 大型整合包提供 overlay 输入的原始入口兜底。
 *
 * <p>本类只在 RTS overlay 真正消费当前事件时取消 {@link GuiScreen} 的后续处理；普通容器点击、
 * JEI、Mouse Tweaks 等未命中 overlay 的事件仍沿原链路执行。Forge 聚合事件入口保留为无 Mixin 环境下的回退。
 */
@Mixin(GuiScreen.class)
public abstract class GuiScreenOverlayInputMixin {
    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$routeOverlayMouseInput(CallbackInfo ci) {
        if (RtsClientInputEvents1122.routeCurrentMouseInput(
                (GuiScreen) (Object) this, "GUI_HEAD_MIXIN")) {
            ci.cancel();
        }
    }

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void rtsbuilding$routeOverlayKeyboardInput(CallbackInfo ci) {
        if (RtsClientInputEvents1122.routeCurrentKeyboardInput(
                (GuiScreen) (Object) this, "GUI_HEAD_MIXIN")) {
            ci.cancel();
        }
    }
}
