package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;

/**
 * 输入路由器可见的 BuilderScreen 窄适配边界。
 *
 * <p>本类没有状态与优先级，只把路由器已经决定归属的事件转交给屏幕现有业务入口。
 * Pointer、Key 与 Scroll 路由因此不能访问渲染器、窗口集合或任意 Screen 生命周期，
 * 也不会在拆分过程中演变成另一个总调度中心。</p>
 */
final class BuilderScreenInputHost {
    private final BuilderScreen screen;

    BuilderScreenInputHost(BuilderScreen screen) {
        this.screen = screen;
    }

    void selectPlacementStateFromWheel(
            PlacementStateWheel.PlacementChoice choice, int button) {
        screen.selectPlacementStateFromWheel(choice, button);
    }

    void closePlacementStateWheelFromPointer(int button) {
        screen.closePlacementStateWheelFromPointer(button);
    }

    void selectModeFromWheelPointer(BuilderMode mode, int button) {
        screen.selectModeFromWheelPointer(mode, button);
    }

    void closeModeWheelFromPointer(int button) {
        screen.closeModeWheelFromPointer(button);
    }

    boolean handleOverlayClicks(double mouseX, double mouseY, int button) {
        return screen.handleOverlayClicks(mouseX, mouseY, button);
    }

    boolean handleBlueprintCaptureClicks(
            double mouseX, double mouseY, int button) {
        return screen.handleBlueprintCaptureClicks(mouseX, mouseY, button);
    }

    boolean handleHomeSelectionClicks(
            double mouseX, double mouseY, int button) {
        return screen.handleHomeSelectionClicks(mouseX, mouseY, button);
    }

    boolean handleRangeCullingSelectionClick(
            double mouseX, double mouseY, int button) {
        return screen.handleRangeCullingSelectionClick(
                mouseX, mouseY, button);
    }

    boolean handleAreaMineClickBlock(
            double mouseX, double mouseY, int button) {
        return screen.handleAreaMineClickBlock(mouseX, mouseY, button);
    }

    boolean handleLeftClickInteractions(
            double mouseX, double mouseY, int button) {
        return screen.handleLeftClickInteractions(mouseX, mouseY, button);
    }

    boolean handleWorldClickActions(
            double mouseX, double mouseY, int button) {
        return screen.handleWorldClickActions(mouseX, mouseY, button);
    }

    boolean forwardUnhandledMouseClicked(
            double mouseX, double mouseY, int button) {
        return screen.forwardUnhandledMouseClicked(mouseX, mouseY, button);
    }

    boolean isAltDown() {
        return screen.isAltDownForInput();
    }

    boolean isWorldArea(double mouseX, double mouseY) {
        return screen.isWorldArea(mouseX, mouseY);
    }

    boolean isInsideBottomPanel(double mouseX, double mouseY) {
        return screen.isInsideBottomPanel(mouseX, mouseY);
    }

    boolean isSearchFocused() {
        return screen.isSearchFocused();
    }

    void closePlacementStateWheelFromKey() {
        screen.closePlacementStateWheelFromKey();
    }

    boolean handleOverlayKeys(int keyCode, int scanCode, int modifiers) {
        return screen.handleOverlayKeys(keyCode, scanCode, modifiers);
    }

    boolean handleBlueprintKeys(int keyCode, int scanCode, int modifiers) {
        return screen.handleBlueprintKeys(keyCode, scanCode, modifiers);
    }

    boolean handleHomeSelectionKey(int keyCode) {
        return screen.handleHomeSelectionKey(keyCode);
    }

    boolean handleSelectionBoxKeys(
            int keyCode, int scanCode, int modifiers) {
        return screen.handleSelectionBoxKeys(keyCode, scanCode, modifiers);
    }

    boolean handleStorageBatchSelectionKey(int keyCode) {
        return screen.handleStorageBatchSelectionKey(keyCode);
    }

    boolean handleStorageBatchSelectionScroll(
            double mouseX, double mouseY, double scrollY) {
        return screen.handleStorageBatchSelectionScroll(mouseX, mouseY, scrollY);
    }

    boolean handleToolSlotKeys(int keyCode, int scanCode, int modifiers) {
        return screen.handleToolSlotKeys(keyCode, scanCode, modifiers);
    }

    boolean handleWorldInteractionKeys(
            int keyCode, int scanCode, int modifiers) {
        return screen.handleWorldInteractionKeys(keyCode, scanCode, modifiers);
    }

    boolean handleSearchFocusKeys(
            int keyCode, int scanCode, int modifiers) {
        return screen.handleSearchFocusKeys(keyCode, scanCode, modifiers);
    }

    boolean handleSensitivityKeys(int keyCode, int scanCode) {
        return screen.handleSensitivityKeys(keyCode, scanCode);
    }

    boolean forwardUnhandledKeyPressed(
            int keyCode, int scanCode, int modifiers) {
        return screen.forwardUnhandledKeyPressed(keyCode, scanCode, modifiers);
    }
}
