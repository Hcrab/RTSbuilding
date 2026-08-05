package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import net.minecraft.item.ItemBlock;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import org.lwjgl.input.Mouse;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的LifecycleOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenLifecycleOwner {
    private final BuilderScreen screen;

    BuilderScreenLifecycleOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    void syncQuickBuildActiveState() {
            if (!screen.quickBuildPanel.isOpen() || !screen.canUseQuickBuild()) {
                screen.controller.setBuildShape(BuildShape.BLOCK);
                screen.controller.clearAreaMineSession();
                screen.shapeController.clearShapeBuildSession();
                screen.ensureFillModeForShape(BuildShape.BLOCK);
                return;
            }
            if (screen.quickBuildPanel.isRangeDestroyMode()) {
                screen.shapeController.applyDestroyStateAsActive();
            } else {
                screen.shapeController.applyBuildStateAsActive();
            }
            screen.ensureFillModeForShape(screen.controller.getBuildShape());
        }

    void init() {

            // Enter the RTS scale frame so that clamps in applyStoredUiState use the virtual coordinate space (rather than the GUI-scaled width)
            RtsUiScaleFrame frame = screen.guiScaleCoordinator.enterLayoutFrame();
            try {
                screen.uiStateManager.applyStoredUiState();
            } finally {
                if (frame != null) {
                    frame.close();
                }
            }
            // 持久化加载后，将当前模式的独立状态同步到活跃字段
            screen.syncQuickBuildActiveState();
            WindowTextBox storageSearchBox = new WindowTextBox(screen.font(), 8, screen.uiHeight() - 52, 150, 14);
            storageSearchBox.setPlaceholder("Search");
            screen.searchBox = storageSearchBox;
            screen.searchBox.setMaxStringLength(128);
            screen.searchBox.setCanLoseFocus(true);
            screen.searchBox.setText(screen.controller.getStorageSearch());
            WindowTextBox craftBox = new WindowTextBox(screen.font(), 8, screen.uiHeight() - 52, 74, 10);
            craftBox.setPlaceholder("Craft Search");
            screen.craftSearchBox = craftBox;
            screen.craftSearchBox.setMaxStringLength(128);
            screen.craftSearchBox.setEnableBackgroundDrawing(false);
            screen.craftSearchBox.setCanLoseFocus(true);
            screen.craftSearchBox.setTextColor(BottomPanelCraftStyle.SEARCH_TEXT.toArgb());
            screen.craftSearchBox.setDisabledTextColour(
                    BottomPanelCraftStyle.SEARCH_UNEDITABLE_TEXT.toArgb());
            if (screen.bottomPanel.craftSearchDraft == null) {
                screen.bottomPanel.craftSearchDraft = screen.controller.getCraftablesSearch();
            }
            screen.craftSearchBox.setText(screen.bottomPanel.craftSearchDraft);
            craftBox.onTextChanged(value -> screen.bottomPanel.craftSearchDraft = value == null ? "" : value);
            screen.controller.requestCraftables();
        }

    void onClose() {
            screen.floatingWindowLayer.clearTransientInputState();
            screen.topBarPanel.clearTransientInputState();
            screen.shapeController.clearShapeBuildSession();
            screen.cullingManager.closeManagementMode();
            screen.controller.clearAreaMineSession();
            screen.persistUiState();
            screen.uiStateManager.flush();
            screen.pendingGuiBindSlot = -1;
            if (screen.funnelHotkeyTemporaryMode && screen.controller.getMode() == BuilderMode.FUNNEL) {
                screen.controller.setMode(screen.modeBeforeFunnelHotkey);
            }
            screen.funnelHotkeyHeld = false;
            screen.funnelHotkeyTemporaryMode = false;
            screen.funnelMouseHoldButton = -1;
            screen.modeWheel.close();
            screen.modeWheelConsumedMouseButton = -1;
            screen.rotationHandles.clear();
            screen.closePlacementStateWheelImmediately();
            screen.placementStateWheelConsumedMouseButton = -1;
            screen.cameraInput.resetCameraVerticalHeld();
            screen.cameraInput.stopActiveMining();
            if (screen.controller.isFunnelEnabled()) {
                screen.controller.setFunnelEnabled(false);
            }
            if (screen.controller.isEnabled()) {
                RtsClientPacketGateway.sendToggleCamera(screen.controller.isStartCameraAtPlayerHead());
            }
            screen.aiChatPanel.close();
            screen.craftQuantityWindowPanel.close();
            screen.overlayRenderer.updateNativeCursorVisibility(false);
            RtsCullingClientState.clearActiveManager(screen.cullingManager);
        }

    void removed() {

            screen.aiChatPanel.close();
            screen.floatingWindowLayer.clearTransientInputState();
            screen.topBarPanel.clearTransientInputState();
            screen.cameraInput.resetCameraVerticalHeld();
            // 屏幕被箱子替换或 RTS 被关闭时都终止按住式单块挖掘，但这里不切换 RTS 相机。
            // stopActiveMining 是幂等的，主动 Esc 已在 onClose 中停止时再次调用也不会重复发包。
            screen.cameraInput.stopActiveMining();
            screen.modeWheel.close();
            screen.modeWheelAltWasDown = false;
            screen.modeWheelConsumedMouseButton = -1;
            screen.rotationHandles.clear();
            screen.closePlacementStateWheelImmediately();
            screen.placementStateWheelConsumedMouseButton = -1;
            boolean restoreModeAfterFunnel = screen.funnelHotkeyTemporaryMode;
            screen.funnelHotkeyHeld = false;
            screen.funnelMouseHoldButton = -1;
            if (screen.controller.isFunnelEnabled()) {
                screen.controller.setFunnelEnabled(false);
            }
            if (restoreModeAfterFunnel && screen.controller.getMode() == BuilderMode.FUNNEL) {
                screen.controller.setMode(screen.modeBeforeFunnelHotkey);
            }
            screen.funnelHotkeyTemporaryMode = false;
            screen.overlayRenderer.updateNativeCursorVisibility(false);
            RtsCullingClientState.clearActiveManager(screen.cullingManager);
        }

    void tick() {

            // 每 tick 写入脏状态（无脏时零开销）
            screen.uiStateManager.flush();
            screen.enforceBlueprintPlacementModeLock();
            screen.updateModeWheelAltState();
            if (screen.controller.getMode() != BuilderMode.ROTATE
                    || screen.getMinecraft() == null
                    || !screen.rotationHandles.targetStillMatches(screen.getMinecraft().theWorld)) {
                screen.rotationHandles.clear();
            }
            if (screen.placementStateWheel.isOpen()
                    && !(screen.controller.getSelectedItemPreview().getItem() instanceof ItemBlock)
                    && !(screen.getMinecraft().thePlayer != null
                    && screen.getMinecraft().thePlayer.getHeldItem().getItem() instanceof ItemBlock)) {
                screen.closePlacementStateWheel();
            }
            if (screen.rtsFlightToggleCooldownTicks > 0) {
                screen.rtsFlightToggleCooldownTicks--;
            }
            if (screen.controller.getMode() == BuilderMode.FUNNEL && screen.controller.isFunnelEnabled()) {
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.updateFunnelTarget(hit.getBlockPos());
                }
            }
            screen.bottomPanel.syncCraftablesPanelState();
            if (!screen.cameraInput.isLeftMiningActive()) {
                return;
            }
            if (screen.getMinecraft() == null || !screen.controller.isEnabled()) {
                screen.cameraInput.stopActiveMining();
                return;
            }
            boolean miningInputDown = screen.cameraInput.isKeyboardMining()
                    ? ClientKeyMappings.ACTION_BREAK.getIsKeyPressed()
                    : screen.cameraInput.getActiveMiningMouseButton() >= 0
                            && Mouse.isButtonDown(screen.cameraInput.getActiveMiningMouseButton());
            if (!miningInputDown) {
                screen.cameraInput.stopActiveMining();
                return;
            }
        }

}
