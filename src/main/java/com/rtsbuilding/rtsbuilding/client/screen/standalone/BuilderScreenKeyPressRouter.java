package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import org.lwjgl.input.Keyboard;

/**
 * BuilderScreen 的按键按下层级路由。
 *
 * <p>本类只维护处理顺序和轮盘的键盘所有权，不实现蓝图、世界、搜索、工具槽或灵敏度业务。
 * 每一层仍由 BuilderScreen 的窄入口处理。将顺序集中后，Pin 快捷键、文本焦点和世界按键
 * 的优先关系不再散落在 Screen 生命周期方法里。</p>
 */
final class BuilderScreenKeyPressRouter {
    /**
     * 保留 GLFW 修饰键掩码的数值约定，方便屏幕入口在 1.12 没有 modifiers 参数时仍沿用现有路由签名。
     * 上层应传入按键事件发生时计算出的掩码；若暂时传 0，本类也会直接查询 LWJGL2 的 Alt 状态兜底。
     */
    private static final int MODIFIER_ALT = 0x0004;

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
        if (keyCode == Keyboard.KEY_ESCAPE) {
            host.closePlacementStateWheelFromKey();
        } else if (keyCode == Keyboard.KEY_LEFT
                || keyCode == Keyboard.KEY_NUMPAD4) {
            this.placementStateWheel.cyclePlacementPage(-1);
        } else if (keyCode == Keyboard.KEY_RIGHT
                || keyCode == Keyboard.KEY_NUMPAD6) {
            this.placementStateWheel.cyclePlacementPage(1);
        }
        return true;
    }

    private boolean handleModeWheelKey(int keyCode, int modifiers) {
        if (!this.modeWheel.isOpen()) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.modeWheel.close();
            return true;
        }
        if (keyCode == Keyboard.KEY_SPACE && isAltDown(modifiers)) {
            this.modeWheel.close();
            return false;
        }
        return true;
    }

    private static boolean isAltDown(int modifiers) {
        return (modifiers & MODIFIER_ALT) != 0
                || Keyboard.isKeyDown(Keyboard.KEY_LMENU)
                || Keyboard.isKeyDown(Keyboard.KEY_RMENU);
    }
}
