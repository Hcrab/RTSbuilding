package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.settings.KeyBinding;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的KeyboardActionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenKeyboardActionOwner {
    private final BuilderScreen screen;

    BuilderScreenKeyboardActionOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    void closePlacementStateWheelFromKey() {
            screen.closePlacementStateWheel();
        }

    boolean handleBlueprintKeys(int keyCode, int scanCode, int modifiers) {
            if (BlueprintPanel.isCaptureModeActive() && BlueprintPanel.keyPressed(keyCode, scanCode, screen.controller)) {
                return true;
            }
            if (BlueprintPanel.isPlacementSessionActive() && BlueprintPanel.keyPressed(keyCode, scanCode, screen.controller)) {
                return true;
            }
            if (screen.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                    && BlueprintPanel.keyPressed(keyCode, scanCode, screen.controller)) {
                return true;
            }
            return false;
        }

    boolean handleHomeSelectionKey(int keyCode) {
            if (!screen.controller.isHomeSelectionMode()) {
                return false;
            }
        if (keyCode == Keyboard.KEY_ESCAPE) {
                RtsClientPacketGateway.sendToggleCamera(screen.controller.isStartCameraAtPlayerHead());
            }
            return true;
        }

    boolean handleOverlayKeys(int keyCode, int scanCode, int modifiers) {
            if (screen.floatingWindowLayer.keyPressed(keyCode, scanCode, modifiers)) {
                screen.submitCraftQuantityWindowIfReady();
                return true;
            }
            return false;
        }

    boolean handleWorldInteractionKeys(int keyCode, int scanCode, int modifiers) {
            if (keyCode == Keyboard.KEY_ESCAPE && screen.cancelQuickBuildSmartFillAnchor()) {
                return true;
            }
            if (screen.cullingManager.isManagementMode()) {
                return true;
            }
            // While area mine selection is active, block all world interaction keys except the break key
            if (!BlueprintPanel.isCaptureModeActive()
                    && screen.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
            if (!matches(ClientKeyMappings.ACTION_BREAK, keyCode)) {
                    return true;
                }
            }
        if (screen.pendingGuiBindSlot >= 0 && keyCode == Keyboard.KEY_ESCAPE) {
                screen.pendingGuiBindSlot = -1;
                return true;
            }
        if (GuiScreen.isCtrlKeyDown() && keyCode == Keyboard.KEY_Z) {
                return screen.shapeController.undoLastPlacementBatch();
            }
            // Alt+Space: toggle creative flight for the player entity in RTS mode
        if (!screen.isSearchFocused() && screen.isAltDownForInput() && keyCode == Keyboard.KEY_SPACE) {
                if (screen.rtsFlightToggleCooldownTicks <= 0) {
                    screen.rtsFlightToggleCooldownTicks = 10;
                    screen.handleRtsFlightToggle();
                }
                return true;
            }
            if (!screen.isSearchFocused() && screen.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, true)) {
                return true;
            }
            if (!screen.isSearchFocused() && screen.handlePlacedBlockRotationKey(keyCode)) {
                return true;
            }
            if (!screen.isSearchFocused() && screen.handleBatchConfirmKey(keyCode, scanCode)) {
                return true;
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.ACTION_BREAK, keyCode)) {
                if (screen.cameraInput.startMiningAt(screen.currentMouseX(), screen.currentMouseY(), -1, true)) {
                    return true;
                }
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.PICK_BLOCK, keyCode)) {
                if (screen.isWorldArea(screen.currentMouseX(), screen.currentMouseY())) {
                    screen.cameraInput.tryPickHoveredBlockForPlacement();
                }
                return true;
            }
            if (!screen.isSearchFocused() && screen.isMovePlayerActionKey(keyCode, scanCode)) {
                return screen.handleMovePlayerActionAt(screen.currentMouseX(), screen.currentMouseY());
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.ACTION_PRIMARY, keyCode)) {
                return screen.runPrimaryActionAt(screen.currentMouseX(), screen.currentMouseY());
            }
            if (!screen.isSearchFocused()
                    && screen.isBlueprintPlacementModeLocked()
                    && matches(ClientKeyMappings.QUICK_FUNNEL, keyCode)) {
                return true;
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.QUICK_FUNNEL, keyCode)) {
                if (screen.funnelHotkeyHeld) {
                    return true;
                }
                screen.activateFunnelHotkey();
                screen.funnelHotkeyHeld = true;
                return true;
            }
            if (!screen.isSearchFocused()
                    && matches(ClientKeyMappings.ROTATE_SHAPE, keyCode)
                && !GuiScreen.isCtrlKeyDown()
                    && screen.controller.getBuildShape() == BuildShape.BLOCK
                    && screen.openPlacementStateWheel(screen.currentMouseX(), screen.currentMouseY())) {
                return true;
            }
            if (!screen.isSearchFocused() && screen.handleModeKeyPressed(keyCode, scanCode)) {
                return true;
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.QUICK_DROP, keyCode)) {
                screen.quickDropSelectedAtCursor();
                return true;
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.ROTATE_SHAPE, keyCode) && !GuiScreen.isCtrlKeyDown()) {
                if (screen.hasRecipeViewerLoaded()) {
                    return false; // let super handle it for recipe viewer keybinds
                }
            screen.shapeController.rotateShapeByStep(GuiScreen.isShiftKeyDown() ? -1 : 1);
                return true;
            }
            if (!screen.isSearchFocused()
                    && matches(ClientKeyMappings.OPEN_CRAFT_TERMINAL, keyCode)
                && !GuiScreen.isCtrlKeyDown()) {
                screen.persistUiState();
                screen.controller.openCraftTerminal();
                return true;
            }
            return false;
        }

    boolean handlePlacedBlockRotationKey(int keyCode) {
            if (screen.controller.getMode() != BuilderMode.ROTATE
                    || !screen.rotationHandles.hasTarget()
                    || screen.getMinecraft() == null
                || screen.getMinecraft().world == null) {
                return false;
            }
            PlacedBlockRotationGesture gesture =
                    PlacedBlockRotationGesture.fromKey(keyCode);
            if (gesture == null) {
                return false;
            }
        EnumFacing cameraForward = screen.currentCameraHorizontalDirection();
            boolean supported = screen.rotationHandles.arcs(
                screen.getMinecraft().world, cameraForward)
                    .stream()
                    .anyMatch(arc -> arc.gesture() == gesture);
            if (supported && screen.rotationHandles.targetPos() != null) {
                screen.controller.rotateBlockStep(
                        screen.rotationHandles.targetPos(),
                        gesture.axisDirection(cameraForward),
                        gesture.quarterTurns());
            }
            return true;
        }

    boolean handleSelectionBoxKeys(int keyCode, int scanCode, int modifiers) {
            if (screen.isSearchFocused()) {
                return false;
            }
            if (screen.cullingManager.isManagementMode() && screen.cullingManager.handleKey(keyCode, scanCode, modifiers)) {
                return true;
            }
            RtsSelectionNudge.Delta delta = RtsSelectionNudge.fromKey(keyCode, scanCode);
            if (delta == null) {
                return false;
            }
            if (screen.cullingManager.isManagementMode()) {
                screen.cullingManager.nudgeSelectedBox(delta.dx(), delta.dy(), delta.dz());
                return true;
            }
            if (screen.shapeController.nudgeCurrentShapeSelection(delta.dx(), delta.dy(), delta.dz())) {
                return true;
            }
            return false;
        }

    boolean handleStorageBatchSelectionKey(int keyCode) {
        if (screen.isSearchFocused()) return false;
        if (keyCode == Keyboard.KEY_LCONTROL || keyCode == Keyboard.KEY_RCONTROL) {
            if (screen.controller.getMode() != BuilderMode.LINK_STORAGE
                    && !screen.storageBatchSelection.isActive()) {
                return false;
            }
            screen.storageBatchSelection.toggle(screen.getMinecraft(), screen.controller.getMode());
            return true;
        }
        if (!screen.storageBatchSelection.isActive()) return false;
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return screen.storageBatchSelection.cancelOrExit(screen.getMinecraft());
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            return screen.storageBatchSelection.confirm(screen.getMinecraft());
        }
        return false;
    }

    boolean handleBatchConfirmKey(int keyCode, int scanCode) {
            if (!Config.isKeyboardBatchConfirmEnabled()) {
                return false;
            }
            if (screen.shapeController.isAwaitingBatchDestroyConfirm()
                    && matches(ClientKeyMappings.CONFIRM_BATCH_DESTROY, keyCode)) {
                screen.shapeController.tryConfirmPendingRangeDestroy();
                return true;
            }
            if (screen.shapeController.isAwaitingBatchPlaceConfirm()
                    && matches(ClientKeyMappings.CONFIRM_BATCH_PLACE, keyCode)) {
            screen.shapeController.tryConfirmPendingShapeBuild(GuiScreen.isShiftKeyDown());
                return true;
            }
            return false;
        }

    boolean handleSearchFocusKeys(int keyCode, int scanCode, int modifiers) {
        if (screen.isSearchFocused() && keyCode == Keyboard.KEY_ESCAPE) {
                if (screen.searchBox != null && screen.searchBox.isFocused()) {
                screen.searchBox.setText("");
                    screen.bottomPanel.handleStorageSearchChanged("");
                    screen.blurSearchFocus();
                    return true;
                }
                if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                    screen.bottomPanel.craftSearchDraft = "";
                screen.craftSearchBox.setText("");
                    screen.controller.setCraftablesSearch("");
                    screen.blurSearchFocus();
                    return true;
                }
                return true;
            }
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
            if (screen.searchBox.textboxKeyTyped((char) 0, keyCode)) {
                screen.bottomPanel.handleStorageSearchChanged(screen.searchBox.getText());
                }
                return true;
            }
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                    screen.bottomPanel.applyCraftSearchDraft();
                    screen.blurSearchFocus();
                    return true;
                }
            screen.craftSearchBox.textboxKeyTyped((char) 0, keyCode);
                return true;
            }
            return false;
        }

    boolean handleToolSlotKeys(int keyCode, int scanCode, int modifiers) {
        if (!screen.isSearchFocused() && keyCode >= Keyboard.KEY_1 && keyCode <= Keyboard.KEY_9) {
            int slot = keyCode - Keyboard.KEY_1;
                screen.setSelectedToolSlot(slot);
                screen.controller.clearPlacementSelectionPreserveMode();
                return true;
            }
        if (!screen.isSearchFocused() && matches(ClientKeyMappings.PIN_QUICK_SLOT, keyCode)) {
                if (screen.bottomPanel.hoveredPinPageButton) {
                    return true;
                }
                if (screen.bottomPanel.hoveredPinIndex >= 0) {
                    if (screen.controller.hasSelectedItem()) {
                        screen.controller.assignQuickSlotFromSelected(screen.bottomPanel.hoveredPinIndex);
                        return true;
                    }
                    if (screen.tryAssignQuickSlotFromToolSelection(screen.bottomPanel.hoveredPinIndex)) {
                        return true;
                    }
                }
            }
            return false;
        }

    boolean handleSensitivityKeys(int keyCode, int scanCode) {
            if (matches(ClientKeyMappings.DECREASE_SENSITIVITY, keyCode)) {
                screen.controller.decreaseRotateSensitivity();
                return true;
            }
            if (matches(ClientKeyMappings.INCREASE_SENSITIVITY, keyCode)) {
                screen.controller.increaseRotateSensitivity();
                return true;
            }
            return false;
    }

    private static boolean matches(KeyBinding binding, int keyCode) {
        return binding != null && binding.getKeyCode() == keyCode;
    }

}
