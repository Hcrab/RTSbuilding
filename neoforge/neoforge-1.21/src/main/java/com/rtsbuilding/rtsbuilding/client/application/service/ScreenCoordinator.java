package com.rtsbuilding.rtsbuilding.client.application.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction.InteractionPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import javax.annotation.Nullable;

/**
 * 屏幕协调器：管理合并后的 {@link InteractionPanel}。
 *
 * <p>面板以网页式标签页承载全部框选容器：
 * 框选弹出面板时经 {@code EntityInteractionHandler} 打开容器标签面板，
 * 容器打开时经 {@link #showContainerScreen} 切换到（或新增）容器页。</p>
 */
public final class ScreenCoordinator {

    @Nullable
    private InteractionPanel interactionPanel;

    /**
     * 获取（必要时创建并注册到浮动窗口层）交互面板。
     */
    public InteractionPanel getOrCreateInteractionPanel(BuilderScreen builderScreen) {
        if (interactionPanel == null || interactionPanel.getScreen() != builderScreen) {
            interactionPanel = new InteractionPanel();
            interactionPanel.init(builderScreen);
            builderScreen.getFloatingWindowLayer().frontToBackWindows().add(interactionPanel);
        }
        return interactionPanel;
    }

    public void showContainerScreen(Screen screen, RtsFloatingWindowLayer floatingWindowLayer, BuilderScreen builderScreen) {
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        InteractionPanel panel = getOrCreateInteractionPanel(builderScreen);
        try {
            panel.openContainerPage(containerScreen);
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.error(
                    "RTS: Failed to open container page; reopening a fresh panel.",
                    throwable);
            panel.setOpen(false);
            interactionPanel = null;
            panel = getOrCreateInteractionPanel(builderScreen);
            panel.openContainerPage(containerScreen);
        }
        floatingWindowLayer.markSortDirty();
    }

    public void closeContainerScreen() {
        if (interactionPanel != null) {
            interactionPanel.setOpen(false);
        }
    }

    public boolean hasContainerScreen() {
        return interactionPanel != null && interactionPanel.isContainerPageOpen();
    }

    @Nullable
    public InteractionPanel getInteractionPanel() {
        return interactionPanel;
    }

    public void tickContainerScreen() {
        if (interactionPanel != null && interactionPanel.isOpen()) {
            interactionPanel.tick();
        }
    }

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
        return false;
    }
}
