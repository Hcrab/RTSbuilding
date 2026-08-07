package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.screen.mode.internal.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.internal.PlacementStateWheel;
import org.lwjgl.glfw.GLFW;

/**
 * BuilderScreen 的按键按下层级路由。
 *
 * <p>本类只维护处理顺序和轮盘的键盘所有权，不实现蓝图、世界、搜索、工具槽或灵敏度业务。
 * 每一层仍由 BuilderScreen 的窄入口处理。将顺序集中后，Pin 快捷键、文本焦点和世界按键
 * 的优先关系不再散落在 Screen 生命周期方法里。</p>
 */
final class BuilderScreenKeyPressRouter {
    private final BuilderScreenInputHost host;
    private final PlacementStateWheel placementStateWheel;
    private final BuilderModeWheel modeWheel;

    BuilderScreenKeyPressRouter(
            BuilderScreenInputHost host,
            PlacementStateWheel placementStateWheel,
            BuilderModeWheel modeWheel) {
        this.host = host;
        this.placementStateWheel = placementStateWheel;
        this.modeWheel = modeWheel;
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (handlePlacementWheelKey(keyCode)) {
            return true;
        }
        if (handleModeWheelKey(keyCode, modifiers)) {
            return true;
        }
        if (host.handleOverlayKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (host.handleBlueprintKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (host.handleHomeSelectionKey(keyCode)) {
            return true;
        }
        if (host.handleStorageBatchSelectionKey(keyCode)) {
            return true;
        }
        if (host.handleSelectionBoxKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Pin 是明确的 UI 操作，必须先于世界/相机按键。
        if (host.handleToolSlotKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (host.handleWorldInteractionKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (host.handleSearchFocusKeys(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (host.handleSensitivityKeys(keyCode, scanCode)) {
            return true;
        }
        return host.forwardUnhandledKeyPressed(keyCode, scanCode, modifiers);
    }

    private boolean handlePlacementWheelKey(int keyCode) {
        if (!this.placementStateWheel.isOpen()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            host.closePlacementStateWheelFromKey();
        } else if (keyCode == GLFW.GLFW_KEY_LEFT
                || keyCode == GLFW.GLFW_KEY_KP_4) {
            this.placementStateWheel.cyclePlacementPage(-1);
        } else if (keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_KP_6) {
            this.placementStateWheel.cyclePlacementPage(1);
        }
        return true;
    }

    private boolean handleModeWheelKey(int keyCode, int modifiers) {
        if (!this.modeWheel.isOpen()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.modeWheel.close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE
                && (modifiers & GLFW.GLFW_MOD_ALT) != 0) {
            this.modeWheel.close();
            return false;
        }
        return true;
    }
}
