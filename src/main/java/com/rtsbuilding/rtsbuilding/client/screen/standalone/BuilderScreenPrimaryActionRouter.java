package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * RTS 主操作（普通点击或主操作键）的优先级路由。
 *
 * <p>本类负责把一次已归属世界区域的主操作，按既有优先级分发给 GUI 绑定、蓝图捕获、
 * 范围剔除、储存绑定、蓝图放置、选中物品、工具槽或空手交互。它不拥有模式、选择、预览
 * 或网络状态，也不改变任何判定规则；实际副作用仍由 Controller、ShapeController 与
 * BlueprintPanel 执行。</p>
 *
 * <p>这条优先级链曾埋在 BuilderScreen 生命周期中。独立后，Screen 只负责确定“何时触发”，
 * 本类负责确定“交给谁”，便于后续版本共用同一顺序并对交互优先级做集中回归。</p>
 */
final class BuilderScreenPrimaryActionRouter {
    private final BuilderScreenPrimaryActionHost host;
    private final ClientRtsController controller;
    private final BottomPanel bottomPanel;
    private final RtsCullingManager cullingManager;
    private final ScreenShapeController shapeController;
    private final ScreenCursorPicker cursorPicker;
    private final BuilderScreenItemActionHandler itemActions;

    BuilderScreenPrimaryActionRouter(
            BuilderScreenPrimaryActionHost host,
            ClientRtsController controller,
            BottomPanel bottomPanel,
            RtsCullingManager cullingManager,
            ScreenShapeController shapeController,
            ScreenCursorPicker cursorPicker) {
        this.host = host;
        this.controller = controller;
        this.bottomPanel = bottomPanel;
        this.cullingManager = cullingManager;
        this.shapeController = shapeController;
        this.cursorPicker = cursorPicker;
        this.itemActions = new BuilderScreenItemActionHandler(
                host, controller, shapeController);
    }

    boolean run(double mouseX, double mouseY, int mouseButton) {
        host.enforceBlueprintPlacementModeLock();
        if (host.pendingGuiBindSlot() >= 0) {
            return true;
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                && BlueprintPanel.isCaptureModeActive()) {
            if (mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    && host.isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = this.cursorPicker.pickBlockHit();
                BlueprintPanel.handleCaptureWorldAction(
                        hit,
                        this.cursorPicker.currentRayOrigin(),
                        this.cursorPicker.computeCursorRayDirection());
            }
            return true;
        }
        if (host.isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleRightClick(mouseX, mouseY);
        }
        if (!host.isWorldArea(mouseX, mouseY)) {
            return true;
        }
        if (host.handleStorageBatchWorldClick(mouseX, mouseY)) {
            return true;
        }
        if (this.cullingManager.isManagementMode()) {
            return mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseButton < 0
                    ? host.handleRangeCullingWorldAction(mouseX, mouseY)
                    : false;
        }
        if (this.controller.getMode() == BuilderMode.LINK_STORAGE) {
            this.shapeController.clearShapeBuildSession();
            BlockHitResult hit = this.cursorPicker.pickBlockHit();
            if (hit != null) {
                this.controller.linkStorage(
                        hit.getBlockPos(), mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT);
            }
            return true;
        }
        if (this.controller.getMode() == BuilderMode.FUNNEL) {
            this.shapeController.clearShapeBuildSession();
            return true;
        }
        if (this.controller.getMode() == BuilderMode.ROTATE) {
            // 旋转箭头只响应左键；主操作中的右键完整保留给相机拖拽。
            return true;
        }
        boolean forcePlace = Screen.hasShiftDown();
        boolean rangeDestroyMode = host.isRangeDestroyMode();
        if (host.isSmartFillMode()) {
            return host.handleSmartFillWorldAction();
        }
        if ((mouseButton == GLFW.GLFW_MOUSE_BUTTON_LEFT || mouseButton < 0)
                && !rangeDestroyMode
                && host.isAdvancedShapeMode()
                && this.shapeController.clickAdvancedRangeDestroyHandle(
                        this.cursorPicker.currentRayOrigin(),
                        this.cursorPicker.computeCursorRayDirection())) {
            return true;
        }
        if (!rangeDestroyMode && this.shapeController.isAwaitingBatchPlaceConfirm()) {
            if (Config.isKeyboardBatchConfirmEnabled()) {
                return true;
            }
            return this.shapeController.tryConfirmPendingShapeBuild(forcePlace);
        }
        if (this.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                && BlueprintPanel.hasSelectedBlueprint()) {
            if (BlueprintPanel.hasPinnedPreview()) {
                BlueprintPanel.confirmPinnedPreview();
                return true;
            }
            BlockHitResult blueprintHit = this.cursorPicker.pickBlueprintPlacementHit();
            if (blueprintHit != null) {
                BlockPos anchor = BlueprintPanel.anchorForCursorTarget(
                        this.cursorPicker.resolveBlueprintAnchor(blueprintHit));
                if (anchor != null) {
                    BlueprintPanel.pinSelected(anchor);
                }
            }
            return true;
        }
        InteractionTypes.InteractionTarget target =
                this.cursorPicker.pickInteractionTarget(false);
        if (target == null) {
            host.tryUseMainHandItemInAir(forcePlace);
            return true;
        }
        if (this.controller.hasSelectedFluid()) {
            if (target.blockHit() != null) {
                if (rangeDestroyMode) {
                    this.controller.placeSelectedFluid(
                            target.blockHit(), forcePlace,
                            target.rayOrigin(), target.rayDir());
                } else {
                    this.shapeController.placeWithShape(
                            target.blockHit(),
                            forcePlace,
                            target.rayOrigin(),
                            target.rayDir(),
                            mouseY,
                            true,
                            InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                            "",
                            -1);
                }
            }
            return true;
        }
        if (this.controller.hasSelectedItem()) {
            return this.itemActions.runSelectedItem(
                    target, forcePlace, rangeDestroyMode, mouseY);
        }
        if (target.blockHit() != null
                && this.controller.getBuildShape() != BuildShape.BLOCK
                && !rangeDestroyMode
                && host.canUseToolSlotShapeSource()) {
            this.shapeController.placeWithShape(
                    target.blockHit(),
                    forcePlace,
                    target.rayOrigin(),
                    target.rayDir(),
                    mouseY,
                    false,
                    InteractionTypes.PlacementReplayKind.TOOL_SLOT,
                    "",
                    host.selectedToolSlot());
            return true;
        }
        return this.itemActions.runToolOrEmptyHand(target, forcePlace);
    }
}
