package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

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
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * BuilderScreen 的KeyboardActionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。
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
    if (BlueprintPanel.isCaptureModeActive()
        && BlueprintPanel.keyPressed(keyCode, scanCode, screen.controller)) {
      return true;
    }
    if (BlueprintPanel.isPlacementSessionActive()
        && BlueprintPanel.keyPressed(keyCode, scanCode, screen.controller)) {
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
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
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
    if (screen.cullingManager.isManagementMode()) {
      return true;
    }
    // While area mine selection is active, block all world interaction keys except the break key
    if (!BlueprintPanel.isCaptureModeActive()
        && screen.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
      if (!ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
        return true;
      }
    }
    if (screen.pendingGuiBindSlot >= 0 && keyCode == GLFW.GLFW_KEY_ESCAPE) {
      screen.pendingGuiBindSlot = -1;
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_ESCAPE && screen.cancelQuickBuildSmartFillAnchor()) {
      return true;
    }
    if (!screen.isSearchFocused() && screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Z) {
      return screen.shapeController.undoLastPlacementBatch();
    }
    if (!screen.isSearchFocused() && screen.hasControlDown() && keyCode == GLFW.GLFW_KEY_Y) {
      return screen.shapeController.redoLastPlacementBatch();
    }
    // Alt+Space: toggle creative flight for the player entity in RTS mode
    if (!screen.isSearchFocused()
        && (modifiers & GLFW.GLFW_MOD_ALT) != 0
        && keyCode == GLFW.GLFW_KEY_SPACE) {
      if (screen.rtsFlightToggleCooldownTicks <= 0) {
        screen.rtsFlightToggleCooldownTicks = 10;
        screen.handleRtsFlightToggle();
      }
      return true;
    }
    if (!screen.isSearchFocused()
        && screen.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, true)) {
      return true;
    }
    if (!screen.isSearchFocused() && screen.handlePlacedBlockRotationKey(keyCode)) {
      return true;
    }
    if (!screen.isSearchFocused() && screen.handleBatchConfirmKey(keyCode, scanCode)) {
      return true;
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
      if (screen.cameraInput.startMiningAt(
          screen.currentMouseX(), screen.currentMouseY(), -1, true)) {
        return true;
      }
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.PICK_BLOCK.matches(keyCode, scanCode)) {
      if (screen.isWorldArea(screen.currentMouseX(), screen.currentMouseY())) {
        screen.cameraInput.tryPickHoveredBlockForPlacement();
      }
      return true;
    }
    if (!screen.isSearchFocused() && screen.isMovePlayerActionKey(keyCode, scanCode)) {
      return screen.handleMovePlayerActionAt(screen.currentMouseX(), screen.currentMouseY());
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.ACTION_PRIMARY.matches(keyCode, scanCode)) {
      return screen.runPrimaryActionAt(screen.currentMouseX(), screen.currentMouseY());
    }
    if (!screen.isSearchFocused()
        && screen.isBlueprintPlacementModeLocked()
        && ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode)) {
      return true;
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode)) {
      if (screen.funnelHotkeyHeld) {
        return true;
      }
      screen.activateFunnelHotkey();
      screen.funnelHotkeyHeld = true;
      return true;
    }
    if (!screen.isSearchFocused()
        && ClientKeyMappings.ROTATE_SHAPE.matches(keyCode, scanCode)
        && !screen.hasControlDown()
        && screen.controller.getBuildShape() == BuildShape.BLOCK
        && screen.openPlacementStateWheel(screen.currentMouseX(), screen.currentMouseY())) {
      return true;
    }
    if (!screen.isSearchFocused() && screen.handleModeKeyPressed(keyCode, scanCode)) {
      return true;
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.QUICK_DROP.matches(keyCode, scanCode)) {
      screen.quickDropSelectedAtCursor();
      return true;
    }
    if (!screen.isSearchFocused()
        && ClientKeyMappings.ROTATE_SHAPE.matches(keyCode, scanCode)
        && !screen.hasControlDown()) {
      if (screen.hasRecipeViewerLoaded()) {
        return false; // let super handle it for recipe viewer keybinds
      }
      screen.shapeController.rotateShapeByStep(screen.hasShiftDown() ? -1 : 1);
      return true;
    }
    if (!screen.isSearchFocused()
        && ClientKeyMappings.OPEN_CRAFT_TERMINAL.matches(keyCode, scanCode)
        && !screen.hasControlDown()) {
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
        || screen.getMinecraft().level == null) {
      return false;
    }
    PlacedBlockRotationGesture gesture = PlacedBlockRotationGesture.fromKey(keyCode);
    if (gesture == null) {
      return false;
    }
    Direction cameraForward = screen.currentCameraHorizontalDirection();
    boolean supported =
        screen.rotationHandles.arcs(screen.getMinecraft().level, cameraForward).stream()
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
    if (screen.cullingManager.isManagementMode()
        && screen.cullingManager.handleKey(keyCode, scanCode, modifiers)) {
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
    if (screen.isSearchFocused()) {
      return false;
    }
    if (keyCode == GLFW.GLFW_KEY_LEFT_CONTROL || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL) {
      if (screen.controller.getMode() != BuilderMode.LINK_STORAGE
          && !screen.storageBatchSelection.isActive()) {
        return false;
      }
      screen.storageBatchSelection.toggle(screen.getMinecraft(), screen.controller.getMode());
      return true;
    }
    if (!screen.storageBatchSelection.isActive()) {
      return false;
    }
    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
      return screen.storageBatchSelection.cancelOrExit(screen.getMinecraft());
    }
    if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
      return screen.storageBatchSelection.confirm(screen.getMinecraft());
    }
    return false;
  }

  boolean handleBatchConfirmKey(int keyCode, int scanCode) {
    if (!Config.isKeyboardBatchConfirmEnabled()) {
      return false;
    }
    if (screen.shapeController.isAwaitingBatchDestroyConfirm()
        && ClientKeyMappings.CONFIRM_BATCH_DESTROY.matches(keyCode, scanCode)) {
      screen.shapeController.tryConfirmPendingRangeDestroy(RtsTraceInputKind.KEYBOARD);
      return true;
    }
    if (screen.shapeController.isAwaitingBatchPlaceConfirm()
        && ClientKeyMappings.CONFIRM_BATCH_PLACE.matches(keyCode, scanCode)) {
      screen.shapeController.tryConfirmPendingShapeBuild(screen.hasShiftDown());
      return true;
    }
    return false;
  }

  boolean handleSearchFocusKeys(int keyCode, int scanCode, int modifiers) {
    if (screen.isSearchFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
      if (screen.searchBox != null && screen.searchBox.isFocused()) {
        screen.searchBox.setValue("");
        screen.bottomPanel.handleStorageSearchChanged("");
        screen.blurSearchFocus();
        return true;
      }
      if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
        screen.bottomPanel.craftSearchDraft = "";
        screen.craftSearchBox.setValue("");
        screen.controller.setCraftablesSearch("");
        screen.blurSearchFocus();
        return true;
      }
      return true;
    }
    if (screen.searchBox != null && screen.searchBox.isFocused()) {
      if (screen.searchBox.keyPressed(keyCode, scanCode, modifiers)) {
        screen.bottomPanel.handleStorageSearchChanged(screen.searchBox.getValue());
      }
      return true;
    }
    if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
      if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
        screen.bottomPanel.applyCraftSearchDraft();
        screen.blurSearchFocus();
        return true;
      }
      screen.craftSearchBox.keyPressed(keyCode, scanCode, modifiers);
      return true;
    }
    return false;
  }

  boolean handleToolSlotKeys(int keyCode, int scanCode, int modifiers) {
    if (!screen.isSearchFocused() && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
      int slot = keyCode - GLFW.GLFW_KEY_1;
      screen.setSelectedToolSlot(slot);
      screen.controller.clearPlacementSelectionPreserveMode();
      return true;
    }
    if (!screen.isSearchFocused() && ClientKeyMappings.PIN_QUICK_SLOT.matches(keyCode, scanCode)) {
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
    if (ClientKeyMappings.DECREASE_SENSITIVITY.matches(keyCode, scanCode)) {
      screen.controller.decreaseRotateSensitivity();
      return true;
    }
    if (ClientKeyMappings.INCREASE_SENSITIVITY.matches(keyCode, scanCode)) {
      screen.controller.increaseRotateSensitivity();
      return true;
    }
    return false;
  }
}
