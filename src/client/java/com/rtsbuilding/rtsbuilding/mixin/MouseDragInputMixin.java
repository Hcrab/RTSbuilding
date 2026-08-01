package com.rtsbuilding.rtsbuilding.mixin;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为 Fabric Screen API 尚未提供的连续鼠标拖拽补一个窄入口。
 *
 * <p>目标方法只包装一次 Screen#mouseDragged 调用；取消时仍保留 MouseHandler 自己的
 * 按钮和坐标状态，避免拖拽结束后出现“鼠标仍按住”的粘滞状态。
 */
@Mixin(MouseHandler.class)
public abstract class MouseDragInputMixin {
    @Shadow
    private int activeButton;

    @Inject(method = "method_55795", at = @At("HEAD"), cancellable = true, remap = false)
    private void rtsbuilding$routeScreenDrag(
            Screen screen, double mouseX, double mouseY, double dragX, double dragY, CallbackInfo callback) {
        RtsScreenEvent.MouseDragged.Pre event = new RtsScreenEvent.MouseDragged.Pre(
                screen, mouseX, mouseY, this.activeButton, dragX, dragY);
        RtsClientInputGate.onScreenMouseDragged(event);
        if (event.isCanceled()) {
            callback.cancel();
        }
    }
}
