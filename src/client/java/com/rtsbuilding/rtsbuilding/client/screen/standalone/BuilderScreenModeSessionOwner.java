package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * BuilderScreen 的ModeSessionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。
 */
final class BuilderScreenModeSessionOwner {
  private final BuilderScreen screen;

  BuilderScreenModeSessionOwner(BuilderScreen screen) {
    this.screen = screen;
  }

  boolean canUseRangeCulling() {
    return !screen.controller.isProgressionEnabled()
        || screen.controller.hasInstalledPlugin(
            BuiltInRtsPluginCatalog.RANGE_CULLING_PLUGIN.toString());
  }

  boolean isRangeCullingManagementActive() {
    return screen.cullingManager.isManagementMode();
  }

  void toggleRangeCullingManagement() {
    if (!screen.canUseRangeCulling()) {
      return;
    }
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.MODE_SWITCH);
    screen.shapeController.clearShapeBuildSession();
    screen.cullingManager.toggleManagementMode();
    screen.cullingPanel.setOpen(screen.cullingManager.isManagementMode());
    screen.persistUiState();
  }

  void openBottomGuide(int x, int y) {
    screen.guidePanel.open(GuideUiContext.BOTTOM, x, y);
  }

  boolean isGuideOpen() {
    return screen.guidePanel.isOpen();
  }

  boolean isGearMenuOpen() {
    return screen.gearMenuPanel.isOpen();
  }

  boolean isCraftQuantityDialogOpen() {
    return screen.craftQuantityWindowPanel.isOpen();
  }

  void activateFunnelHotkey() {
    if (screen.isBlueprintPlacementModeLocked()) {
      screen.enforceBlueprintPlacementModeLock();
      return;
    }
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.MODE_SWITCH);
    screen.shapeController.clearShapeBuildSession();
    screen.funnelHotkeyTemporaryMode = screen.controller.getMode() != BuilderMode.FUNNEL;
    if (screen.funnelHotkeyTemporaryMode) {
      screen.funnelMouseHoldButton = -1;
      screen.modeBeforeFunnelHotkey = screen.controller.getMode();
    }
    screen.controller.setMode(BuilderMode.FUNNEL);
    screen.controller.setFunnelEnabled(true);
  }

  void deactivateFunnelHotkey() {
    if (screen.controller.getMode() == BuilderMode.FUNNEL || screen.controller.isFunnelEnabled()) {
      if (screen.funnelHotkeyTemporaryMode) {
        screen.funnelMouseHoldButton = -1;
        screen.controller.setFunnelEnabled(false);
        screen.controller.setMode(screen.modeBeforeFunnelHotkey);
      } else {
        screen.syncFunnelHoldState();
      }
    }
    screen.funnelHotkeyTemporaryMode = false;
  }

  void beginFunnelMouseHold(int button) {
    if (screen.controller.getMode() != BuilderMode.FUNNEL) {
      return;
    }
    screen.funnelMouseHoldButton = button;
    screen.syncFunnelHoldState();
  }

  void endFunnelMouseHold(int button) {
    if (button != screen.funnelMouseHoldButton) {
      return;
    }
    screen.funnelMouseHoldButton = -1;
    screen.syncFunnelHoldState();
  }

  void syncFunnelHoldState() {
    boolean enabled =
        screen.controller.getMode() == BuilderMode.FUNNEL
            && (screen.funnelHotkeyHeld || screen.funnelMouseHoldButton >= 0);
    screen.controller.setFunnelEnabled(enabled);
  }

  void updateModeWheelAltState() {
    boolean altDown = screen.isAltDown();
    if (altDown && !screen.modeWheelAltWasDown && screen.canOpenModeWheel()) {
      screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.WINDOW_OPENED);
      screen.cameraInput.cancelPointerGestures();
      screen.funnelMouseHoldButton = -1;
      screen.syncFunnelHoldState();
      screen.rotationHandles.clear();
      screen.closePlacementStateWheelImmediately();
      int uiWidth = screen.guiScaleCoordinator.viewportWidth();
      int uiHeight = screen.guiScaleCoordinator.viewportHeight();
      screen.modeWheel.open(screen.currentMouseX(), screen.currentMouseY(), uiWidth, uiHeight);
    } else if (!altDown && screen.modeWheelAltWasDown) {
      screen.modeWheel.close();
    }
    screen.modeWheelAltWasDown = altDown;
  }

  boolean canOpenModeWheel() {
    return screen.controller.isEnabled()
        && !screen.controller.isHomeSelectionMode()
        && !screen.isSearchFocused()
        && !screen.isBlueprintPlacementModeLocked()
        && !BlueprintPanel.isCaptureModeActive()
        && !screen.cullingManager.isManagementMode()
        && screen.controller.getAreaMinePhase() == MiningOperationService.AREA_MINE_PHASE_NONE
        && screen.shapeController.advancedRangeDestroyActiveHandle() == null;
  }

  void selectModeFromWheel(BuilderMode mode) {
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.MODE_SWITCH);
    screen.shapeController.clearShapeBuildSession();
    screen.funnelHotkeyHeld = false;
    screen.funnelHotkeyTemporaryMode = false;
    screen.funnelMouseHoldButton = -1;
    screen.controller.setFunnelEnabled(false);
    screen.controller.setMode(mode);
    screen.rotationHandles.clear();
    screen.closePlacementStateWheel();
  }

  boolean handleBoxHandleDrag(int button, double dragX, double dragY) {
    if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
      return false;
    }
    Direction blueprintDirection = BlueprintPanel.getCaptureActiveHandleDirection();
    if (BlueprintPanel.isCaptureModeActive() && blueprintDirection != null) {
      double[] axis = screen.screenAxisForDirection(blueprintDirection);
      return BlueprintPanel.mouseDraggedCaptureHandle(dragX, dragY, axis[0], axis[1]);
    }
    Direction cullingDirection = screen.cullingManager.activeHandleDirection();
    if (screen.cullingManager.isManagementMode() && cullingDirection != null) {
      double[] axis = screen.screenAxisForDirection(cullingDirection);
      return screen.cullingManager.handleActiveHandleDrag(dragX, dragY, axis[0], axis[1]);
    }
    Direction advancedBoxDirection = screen.shapeController.advancedRangeDestroyActiveHandle();
    if (advancedBoxDirection != null) {
      double[] axis = screen.screenAxisForDirection(advancedBoxDirection);
      return screen.shapeController.dragAdvancedRangeDestroyHandle(dragX, dragY, axis[0], axis[1]);
    }
    return false;
  }

  double[] screenAxisForDirection(Direction direction) {
    if (direction == null
        || screen.getMinecraft() == null
        || screen.getMinecraft().gameRenderer == null) {
      return new double[] {0.0D, -1.0D};
    }
    float yawDeg = screen.getMinecraft().gameRenderer.getMainCamera().getYRot();
    float pitchDeg = screen.getMinecraft().gameRenderer.getMainCamera().getXRot();
    double yaw = Math.toRadians(yawDeg);
    double pitch = Math.toRadians(pitchDeg);
    Vec3 forward =
        new Vec3(
                -Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch))
            .normalize();
    Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
    Vec3 up = forward.cross(right).normalize();
    Vec3 normal =
        new Vec3(
            direction.getNormal().getX(),
            direction.getNormal().getY(),
            direction.getNormal().getZ());
    return new double[] {-normal.dot(right), -normal.dot(up)};
  }

  void updateRangeCullingHover(double mouseX, double mouseY) {
    if (!screen.cullingManager.isManagementMode()) {
      screen.cullingManager.updateHover(null, null);
    } else if (!screen.isWorldArea(mouseX, mouseY)
        || screen.isMouseOverFloatingWindow(mouseX, mouseY)) {
      screen.cullingManager.updateHover(null, null);
    } else {
      screen.cullingManager.updateHover(
          screen.cursorPicker.currentRayOrigin(), screen.cursorPicker.computeCursorRayDirection());
    }
    screen.updateAdvancedRangeDestroyHover(mouseX, mouseY);
  }

  void updateAdvancedRangeDestroyHover(double mouseX, double mouseY) {
    if (!screen.isAdvancedShapeMode()) {
      screen.shapeController.updateAdvancedRangeDestroyHover(null, null, false);
      return;
    }
    boolean enabled =
        screen.isWorldArea(mouseX, mouseY) && !screen.isMouseOverFloatingWindow(mouseX, mouseY);
    screen.shapeController.updateAdvancedRangeDestroyHover(
        enabled ? screen.cursorPicker.currentRayOrigin() : null,
        enabled ? screen.cursorPicker.computeCursorRayDirection() : null,
        enabled);
  }

  boolean isBlueprintPlacementModeLocked() {
    return BlueprintPanel.isPlacementSessionActive();
  }

  void enforceBlueprintPlacementModeLock() {
    if (!screen.isBlueprintPlacementModeLocked()) {
      return;
    }
    if (screen.controller.getMode() == BuilderMode.INTERACT
        && !screen.controller.isFunnelEnabled()) {
      return;
    }
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.MODE_SWITCH);
    screen.shapeController.clearShapeBuildSession();
    screen.controller.setFunnelEnabled(false);
    screen.controller.setMode(BuilderMode.INTERACT);
    screen.funnelHotkeyHeld = false;
    screen.funnelHotkeyTemporaryMode = false;
    screen.funnelMouseHoldButton = -1;
    screen.rotationHandles.clear();
    screen.closePlacementStateWheel();
  }

  void quickDropSelectedAtCursor() {
    if (screen.getMinecraft() == null || screen.getMinecraft().getCameraEntity() == null) {
      return;
    }
    String dropItemId = "";
    if (screen.controller.hasSelectedItem() && !screen.controller.getSelectedItemId().isBlank()) {
      dropItemId = screen.controller.getSelectedItemId();
    } else {
      ItemStack toolStack = screen.getSelectedToolStack();
      if (toolStack.isEmpty()) {
        return;
      }
      ResourceLocation id = BuiltInRegistries.ITEM.getKey(toolStack.getItem());
      if (id == null) {
        return;
      }
      dropItemId = id.toString();
    }
    Vec3 origin = screen.getMinecraft().gameRenderer.getMainCamera().getPosition();
    Vec3 dir = screen.cursorPicker.computeCursorRayDirection();
    Vec3 dropPos = origin.add(dir.scale(3.25D));
    BlockHitResult hit = screen.cursorPicker.pickBlockHit(true);
    if (hit != null) {
      dropPos = Vec3.atCenterOf(hit.getBlockPos()).add(0.0D, 1.05D, 0.0D);
    }
    screen.controller.quickDropSelectedItem(dropItemId, 1, dropPos);
  }
}
