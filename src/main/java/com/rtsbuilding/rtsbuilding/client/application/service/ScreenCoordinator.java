package com.rtsbuilding.rtsbuilding.client.application.service;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.container.ContainerScreenPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

public final class ScreenCoordinator {

    @Nullable
    private ContainerScreenPanel containerScreenPanel;

    // ======================== 容器屏幕面板管理 ========================

    public void showContainerScreen(Screen screen, RtsFloatingWindowLayer floatingWindowLayer, BuilderScreen builderScreen) {
        if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> containerScreen)) return;

        if (this.containerScreenPanel != null) {
            this.containerScreenPanel.setOpen(false);
        }

        this.containerScreenPanel = new ContainerScreenPanel(containerScreen);
        this.containerScreenPanel.init(builderScreen);
        floatingWindowLayer.frontToBackWindows().add(this.containerScreenPanel);
        this.containerScreenPanel.setOpen(true);
        floatingWindowLayer.markSortDirty();
    }

    public void closeContainerScreen() {
        if (this.containerScreenPanel != null) {
            this.containerScreenPanel.setOpen(false);
            this.containerScreenPanel = null;
        }
    }

    public boolean hasContainerScreen() {
        return this.containerScreenPanel != null && this.containerScreenPanel.isOpen();
    }

    @Nullable
    public ContainerScreenPanel getContainerScreenPanel() {
        return containerScreenPanel;
    }

    public void tickContainerScreen() {
        if (containerScreenPanel != null && containerScreenPanel.isOpen()) {
            containerScreenPanel.tick();
        }
    }

    // ======================== 全局状态持久化 ========================

    public void restoreGlobalState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        ThemeManager.getInstance().setLightMode(state.lightMode);
        CameraModule cam = CompositionRoot.get().module(CameraModule.class);
        if (cam != null) {
            cam.setInputSensitivity((float) state.camera.inputSensitivity);
        }
    }

    public void persistGlobalState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.lightMode = ThemeManager.getInstance().isLightMode();
        CameraModule cam = CompositionRoot.get().module(CameraModule.class);
        if (cam != null) {
            state.camera.inputSensitivity = cam.getInputSensitivity();
        }
        RtsClientUiStateStore.cache().markDirty();
    }

    // ======================== UI 命中检测 ========================

    public boolean isMouseOverUI(double mouseX, double mouseY,
                                 RtsFloatingWindowLayer floatingWindowLayer, TopBarPanel topBarPanel) {
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        return topBarPanel != null && topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY);
    }

    public boolean isMouseOverRtsPanelApi(double mouseX, double mouseY, int width, int height,
                                          RtsFloatingWindowLayer floatingWindowLayer, TopBarPanel topBarPanel,
                                          DownSidebarPanel downSidebarPanel) {
        if (floatingWindowLayer != null
                && floatingWindowLayer.isMouseOverWindowOrResizableBorder(mouseX, mouseY)) {
            return true;
        }
        if (topBarPanel != null && topBarPanel.isMouseOverAnyPopup((int) mouseX, (int) mouseY)) {
            return true;
        }
        int downH = downSidebarPanel != null ? downSidebarPanel.getCurrentHeight() : 0;
        if (downH > 0 && mouseY >= height - downH) {
            return true;
        }
        int rightW = 0;
        if (false) { /* right side check is caller-provided */ }
        return false;
    }
}
