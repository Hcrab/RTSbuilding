package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;

/**
 * BuilderScreen 的鼠标按下路由器。
 *
 * <p>本类只决定输入层的先后顺序：轮盘、浮窗、选择模式、面板与世界操作按从前到后的
 * 固定层级依次获得处理机会。它不拥有任何游戏状态，也不执行放置、破坏、绑定或相机动作；
 * 这些行为仍由 {@link BuilderScreen} 及其专用组件负责。</p>
 *
 * <p>将顺序从主屏幕中提取出来，可以让后续新增浮窗或输入层时只改一处，并避免世界点击
 * 意外穿透到被覆盖的面板。坐标缩放仍由主屏幕入口处理，保证这里始终接收 RTS 虚拟视口坐标。</p>
 */
final class BuilderScreenPointerClickRouter {
    // LWJGL2 的鼠标事件使用从 0 开始的按钮编号；GuiScreen 会原样把该编号传到这里。
    private static final int MOUSE_BUTTON_LEFT = 0;
    private static final int MOUSE_BUTTON_RIGHT = 1;

    private final BuilderScreenInputHost host;
    private final PlacementStateWheel placementStateWheel;
    private final BuilderModeWheel modeWheel;

    BuilderScreenPointerClickRouter(
            BuilderScreenInputHost host,
            PlacementStateWheel placementStateWheel,
            BuilderModeWheel modeWheel) {
        this.host = host;
        this.placementStateWheel = placementStateWheel;
        this.modeWheel = modeWheel;
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (handlePlacementStateWheelClick(mouseX, mouseY, button)) {
            return true;
        }
        if (handleModeWheelClick(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleOverlayClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleBlueprintCaptureClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleHomeSelectionClicks(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleRangeCullingSelectionClick(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleAreaMineClickBlock(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleLeftClickInteractions(mouseX, mouseY, button)) {
            return true;
        }
        if (host.handleWorldClickActions(mouseX, mouseY, button)) {
            return true;
        }
        return host.forwardUnhandledMouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePlacementStateWheelClick(
            double mouseX, double mouseY, int button) {
        if (!this.placementStateWheel.isOpen()) {
            return false;
        }
        if (button == MOUSE_BUTTON_LEFT) {
            if (this.placementStateWheel.handlePlacementPageClick(mouseX, mouseY)) {
                return true;
            }
            PlacementStateWheel.PlacementChoice choice =
                    this.placementStateWheel.hoveredChoice(mouseX, mouseY);
            if (choice != null) {
                host.selectPlacementStateFromWheel(choice, button);
            }
        } else if (button == MOUSE_BUTTON_RIGHT) {
            host.closePlacementStateWheelFromPointer(button);
        }
        return true;
    }

    private boolean handleModeWheelClick(double mouseX, double mouseY, int button) {
        if (!this.modeWheel.isOpen()) {
            return false;
        }
        if (button == MOUSE_BUTTON_LEFT) {
            BuilderMode selectedMode = this.modeWheel.hoveredMode(mouseX, mouseY);
            if (selectedMode != null) {
                host.selectModeFromWheelPointer(selectedMode, button);
            }
        } else if (button == MOUSE_BUTTON_RIGHT) {
            host.closeModeWheelFromPointer(button);
        }
        return true;
    }
}
