package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

/**
 * BuilderScreen 的PointerGestureOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。
 */
final class BuilderScreenPointerGestureOwner {
  private final BuilderScreen screen;

  BuilderScreenPointerGestureOwner(BuilderScreen screen) {
    this.screen = screen;
  }

  /**
   * Handles mouse release with RTS GUI scale remapping. Routes release events to open dialogs,
   * dragging state, floating windows, and camera input handlers.
   */
  public boolean mouseReleased(double mouseX, double mouseY, int button) {
    try (RtsGuiScaleCoordinator.InputFrame frame = screen.guiScaleCoordinator.beginInput()) {
      if (frame.requiresRemap()) {
        return screen.mouseReleased(mouseX / frame.scale(), mouseY / frame.scale(), button);
      }
    }
    screen.endFunnelMouseHold(button);
    screen.topBarPanel.mouseReleased(button);
    if (button == screen.placementStateWheelConsumedMouseButton) {
      screen.placementStateWheelConsumedMouseButton = -1;
      return true;
    }
    if (screen.placementStateWheel.isOpen()) {
      return true;
    }
    if (button == screen.modeWheelConsumedMouseButton) {
      screen.modeWheelConsumedMouseButton = -1;
      return true;
    }
    if (screen.modeWheel.isOpen()) {
      return true;
    }
    if (screen.cameraInput.isLeftMiningActive()
        && !screen.cameraInput.isKeyboardMining()
        && button == screen.cameraInput.getActiveMiningMouseButton()) {
      screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.POINTER_RELEASE);
      return true;
    }
    if (screen.handleFloatingWindowRelease(mouseX, mouseY, button)) {
      return true;
    }
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        && BlueprintPanel.releaseCaptureActiveHandleIfDragged()) {
      return true;
    }
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        && screen.cullingManager.isManagementMode()
        && screen.cullingManager.releaseActiveHandleIfDragged()) {
      return true;
    }
    if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT
        && screen.shapeController.releaseAdvancedRangeDestroyHandleIfDragged()) {
      return true;
    }
    if (screen.cameraInput.isRightDragActive(button)) {
      boolean runPrimary = screen.cameraInput.endRightPress(mouseX, mouseY, button);
      boolean consumed = runPrimary ? screen.runPrimaryActionAt(mouseX, mouseY, button) : true;
      return consumed;
    }
    if (screen.cameraInput.endMiddlePress(mouseX, mouseY, button)) {
      return true;
    }
    return screen.forwardUnhandledMouseReleased(mouseX, mouseY, button);
  }

  /**
   * Handles mouse drag with RTS GUI scale remapping. Routes drag events to open dialogs,
   * sensitivity slider dragging, floating windows, camera drag handlers, and search box focus
   * logic.
   */
  public boolean mouseDragged(
      double mouseX, double mouseY, int button, double dragX, double dragY) {
    try (RtsGuiScaleCoordinator.InputFrame frame = screen.guiScaleCoordinator.beginInput()) {
      if (frame.requiresRemap()) {
        return screen.mouseDragged(
            mouseX / frame.scale(),
            mouseY / frame.scale(),
            button,
            dragX / frame.scale(),
            dragY / frame.scale());
      }
    }
    if (screen.placementStateWheel.isOpen()) {
      return true;
    }
    if (screen.modeWheel.isOpen()) {
      return true;
    }
    if (screen.handleFloatingWindowDrag(mouseX, mouseY, button, dragX, dragY)) {
      return true;
    }
    if (screen.handleBoxHandleDrag(button, dragX, dragY)) {
      return true;
    }
    if (screen.cullingManager.isManagementMode() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      return true;
    }

    // Block all mouse drag operations while area mine selection is active
    if (!BlueprintPanel.isCaptureModeActive()
        && screen.controller.getAreaMinePhase() != MiningOperationService.AREA_MINE_PHASE_NONE) {
      return true;
    }

    if (screen.cameraInput.handleRightDrag(mouseX, mouseY, button, dragX, dragY)) {
      return true;
    }
    if (screen.cameraInput.handleMiddleDrag(mouseX, mouseY, button, dragX, dragY)) {
      return true;
    }
    if (screen.cameraInput.handleKeyboardPanDragAt(mouseX, mouseY, dragX, dragY)) {
      return true;
    }
    if (screen.isSearchFocused()) {
      return true;
    }
    return screen.forwardUnhandledMouseDragged(mouseX, mouseY, button, dragX, dragY);
  }

  /** Handles mouse movement with RTS GUI scale remapping. Updates keyboard-pan drag state. */
  public void mouseMoved(double mouseX, double mouseY) {
    try (RtsGuiScaleCoordinator.InputFrame frame = screen.guiScaleCoordinator.beginInput()) {
      if (frame.requiresRemap()) {
        screen.mouseMoved(mouseX / frame.scale(), mouseY / frame.scale());
        return;
      }
    }
    if (screen.placementStateWheel.isOpen()) {
      return;
    }
    screen.cameraInput.updateKeyboardPanDrag(mouseX, mouseY);
    screen.forwardUnhandledMouseMoved(mouseX, mouseY);
  }

  boolean isCameraUpActionHeld() {
    return screen.cameraInput.isCameraUpActionHeld();
  }

  boolean isCameraDownActionHeld() {
    return screen.cameraInput.isCameraDownActionHeld();
  }

  boolean runPrimaryActionAt(double mouseX, double mouseY) {
    return screen.primaryActionRouter.run(mouseX, mouseY, -1);
  }

  boolean runPrimaryActionAt(double mouseX, double mouseY, int mouseButton) {
    return screen.primaryActionRouter.run(mouseX, mouseY, mouseButton);
  }

  boolean openPlacementStateWheel(double mouseX, double mouseY) {
    if (screen.getMinecraft() == null || screen.getMinecraft().level == null) {
      return false;
    }
    ItemStack selected = screen.controller.getSelectedItemPreview();
    if (!(selected.getItem() instanceof BlockItem)
        && (screen.getMinecraft().player == null
            || !(screen.getMinecraft().player.getMainHandItem().getItem() instanceof BlockItem))) {
      return false;
    }
    BlockHitResult hit = screen.cursorPicker.pickBlockHit();
    BlockPos targetPos =
        hit == null
            ? null
            : screen.getMinecraft().level.getBlockState(hit.getBlockPos()).canBeReplaced()
                ? hit.getBlockPos()
                : hit.getBlockPos().relative(hit.getDirection());
    BlockState state = BuildGhostBlockStateResolver.resolve(screen.getMinecraft(), targetPos);
    if (state == null) {
      return false;
    }
    var camera = screen.getMinecraft().gameRenderer.getMainCamera();
    int uiWidth = screen.guiScaleCoordinator.viewportWidth();
    int uiHeight = screen.guiScaleCoordinator.viewportHeight();
    if (!screen.placementStateWheel.open(
        state, mouseX, mouseY, uiWidth, uiHeight, camera.getYRot(), camera.getXRot())) {
      if (screen.getMinecraft().player != null) {
        screen
            .getMinecraft()
            .player
            .displayClientMessage(
                Component.translatable("screen.rtsbuilding.placement_state_wheel.unsupported"),
                true);
      }
      return true;
    }
    RtsPlacementRayFreeze.clear();
    screen.placementWheelRestoreMouseX = screen.getMinecraft().mouseHandler.xpos();
    screen.placementWheelRestoreMouseY = screen.getMinecraft().mouseHandler.ypos();
    RtsPlacementRayFreeze.freeze(
        screen.cursorPicker.currentRayOrigin(), screen.cursorPicker.computeCursorRayDirection());
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.PLACEMENT_WHEEL_OPENED);
    screen.cameraInput.cancelPointerGestures();
    screen.rotationHandles.clear();
    screen.modeWheel.close();
    return true;
  }

  void closePlacementStateWheel() {
    screen.placementStateWheel.close();
    screen.releasePlacementWheelPointer();
  }

  void closePlacementStateWheelImmediately() {
    screen.placementStateWheel.closeImmediately();
    screen.releasePlacementWheelPointer();
  }

  void releasePlacementWheelPointer() {
    RtsPlacementRayFreeze.clear();
    if (screen.getMinecraft() != null
        && screen.getMinecraft().getWindow() != null
        && Double.isFinite(screen.placementWheelRestoreMouseX)
        && Double.isFinite(screen.placementWheelRestoreMouseY)) {
      GLFW.glfwSetCursorPos(
          screen.getMinecraft().getWindow().getWindow(),
          screen.placementWheelRestoreMouseX,
          screen.placementWheelRestoreMouseY);
    }
    screen.placementWheelRestoreMouseX = Double.NaN;
    screen.placementWheelRestoreMouseY = Double.NaN;
  }

  boolean canUseMainHandItemInAir() {
    return screen.hasMainHandItem()
        && !screen.controller.hasSelectedItem()
        && !screen.controller.hasSelectedFluid()
        && !screen.controller.isEmptyHandSelected()
        && screen.controller.getBuildShape() == BuildShape.BLOCK;
  }
}
