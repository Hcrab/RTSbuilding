package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.CullingUiAdapter;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.mode.internal.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.internal.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;

/**
 * BuilderScreen 的滚轮所有权路由。
 *
 * <p>本类只按前后层级分配滚轮：轮盘与浮窗优先，其次是蓝图/剔除/形状手柄和底栏，
 * 最后才交给世界形状高度、范围挖掘高度或相机缩放。它不维护滚动位置，也不解释业务
 * 数据；每个接收者仍负责自己的状态。</p>
 *
 * <p>把这条链集中后，UI 即使滚到边界也会继续吞掉滚轮，不会把同一事件泄漏给相机。</p>
 */
final class BuilderScreenScrollRouter {
    private final BuilderScreenInputHost host;
    private final ClientRtsController controller;
    private final PlacementStateWheel placementStateWheel;
    private final BuilderModeWheel modeWheel;
    private final RtsFloatingWindowLayer floatingWindowLayer;
    private final RtsCullingManager cullingManager;
    private final ScreenShapeController shapeController;
    private final BottomPanel bottomPanel;

    BuilderScreenScrollRouter(
            BuilderScreenInputHost host,
            ClientRtsController controller,
            PlacementStateWheel placementStateWheel,
            BuilderModeWheel modeWheel,
            RtsFloatingWindowLayer floatingWindowLayer,
            RtsCullingManager cullingManager,
            ScreenShapeController shapeController,
            BottomPanel bottomPanel) {
        this.host = host;
        this.controller = controller;
        this.placementStateWheel = placementStateWheel;
        this.modeWheel = modeWheel;
        this.floatingWindowLayer = floatingWindowLayer;
        this.cullingManager = cullingManager;
        this.shapeController = shapeController;
        this.bottomPanel = bottomPanel;
    }

    boolean mouseScrolled(
            double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.placementStateWheel.isOpen() || this.modeWheel.isOpen()) {
            return true;
        }
        if (this.floatingWindowLayer.mouseScrolled(
                mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (host.handleStorageBatchSelectionScroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        boolean fast = host.isAltDown();
        if (BlueprintPanel.mouseScrolledCaptureHeight(scrollY, fast)) {
            return true;
        }
        if (this.cullingManager.isManagementMode()
                && (this.cullingManager.activeHandleDirection() != null
                || host.isWorldArea(mouseX, mouseY))
                && CullingUiAdapter.handleScroll(
                this.cullingManager, scrollY, fast)) {
            return true;
        }
        if (this.shapeController.advancedRangeDestroyActiveHandle() != null
                && this.shapeController.scrollAdvancedRangeDestroyHandle(
                scrollY, fast)) {
            return true;
        }
        if (host.isInsideBottomPanel(mouseX, mouseY)) {
            return this.bottomPanel.handleMouseScrolled(
                    mouseX, mouseY, scrollY);
        }
        if (!host.isSearchFocused()
                && this.shapeController.handleShapeHeightMouseScrolled(scrollY)) {
            return true;
        }
        if (!BlueprintPanel.isCaptureModeActive()
                && this.controller.getAreaMinePhase()
                != MiningOperationService.AREA_MINE_PHASE_NONE) {
            if (this.controller.getAreaMinePhase()
                    == MiningOperationService.AREA_MINE_PHASE_NEED_HEIGHT) {
                int delta = scrollY > 0.0D ? 1 : -1;
                this.controller.adjustAreaMineHeightOffset(
                        fast ? delta * 4 : delta);
            }
            return true;
        }
        this.controller.queueScroll(scrollY);
        return true;
    }
}
