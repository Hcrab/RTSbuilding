package com.rtsbuilding.rtsbuilding.fabric.client;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import com.rtsbuilding.rtsbuilding.client.input.event.RtsScreenEvent;
import com.rtsbuilding.rtsbuilding.client.plugin.RtsPluginInventoryScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.screens.Screen;

/**
 * 把 Fabric 官方的逐屏可取消输入事件适配到 RTS 内部事件模型。
 *
 * <p>注册必须在每次 AFTER_INIT 后执行，因为 Fabric 会随屏幕重建清空逐屏监听器。
 * 本类只负责按键、点击、释放、滚轮和关闭；连续拖拽与字符码点由两个窄 Mixin 补齐。
 */
final class RtsFabricScreenInput {
    private RtsFabricScreenInput() {
    }

    static void register(Screen screen) {
        ScreenMouseEvents.allowMouseClick(screen).register((current, mouseX, mouseY, button) -> {
            RtsScreenEvent.MouseButtonPressed.Pre event = new RtsScreenEvent.MouseButtonPressed.Pre(
                    current, mouseX, mouseY, button);
            RtsPluginInventoryScreenEvents.onInventoryMousePressed(event);
            if (!event.isCanceled()) {
                RtsClientInputGate.onScreenMousePressed(event);
            }
            return !event.isCanceled();
        });
        ScreenMouseEvents.allowMouseRelease(screen).register((current, mouseX, mouseY, button) -> {
            RtsScreenEvent.MouseButtonReleased.Pre event = new RtsScreenEvent.MouseButtonReleased.Pre(
                    current, mouseX, mouseY, button);
            RtsClientInputGate.onScreenMouseReleased(event);
            return !event.isCanceled();
        });
        ScreenMouseEvents.allowMouseScroll(screen).register(
                (current, mouseX, mouseY, scrollX, scrollY) -> {
                    RtsScreenEvent.MouseScrolled.Pre event = new RtsScreenEvent.MouseScrolled.Pre(
                            current, mouseX, mouseY, scrollX, scrollY);
                    RtsClientInputGate.onScreenMouseScrolled(event);
                    return !event.isCanceled();
                });
        ScreenKeyboardEvents.allowKeyPress(screen).register((current, keyCode, scanCode, modifiers) -> {
            RtsScreenEvent.KeyPressed.Pre event = new RtsScreenEvent.KeyPressed.Pre(
                    current, keyCode, scanCode, modifiers);
            RtsClientInputGate.onScreenKeyPressed(event);
            return !event.isCanceled();
        });
        ScreenEvents.remove(screen).register(current ->
                RtsClientInputGate.onScreenClosing(new RtsScreenEvent.Closing(current)));
    }
}
