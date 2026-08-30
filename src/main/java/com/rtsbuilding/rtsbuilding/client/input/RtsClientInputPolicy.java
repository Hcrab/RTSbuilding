package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 容器叠层输入资格的唯一策略。
 *
 * <p>策略只回答某个屏幕是否属于叠层输入域，不读取鼠标位置、不改变 UI 状态，
 * 更不执行库存或网络副作用。路由器必须先经过这里，避免各事件入口维护不同门禁。</p>
 */
final class RtsClientInputPolicy {
    private RtsClientInputPolicy() {
    }

    static boolean isOverlayContainer(Screen screen) {
        return screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof BuilderScreen)
                && !(screen instanceof RtsCraftTerminalScreen);
    }

    static boolean canHandleOverlayInput(Screen screen) {
        return ClientRtsController.get().canUseStorageOverlay()
                && RtsClientUiStateStore.isContainerOverlayEnabled()
                && isOverlayContainer(screen);
    }
}
