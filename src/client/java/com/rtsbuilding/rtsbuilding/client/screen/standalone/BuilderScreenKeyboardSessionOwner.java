package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import org.lwjgl.glfw.GLFW;

/**
 * BuilderScreen 的KeyboardSessionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。
 */
final class BuilderScreenKeyboardSessionOwner {
  private final BuilderScreen screen;

  BuilderScreenKeyboardSessionOwner(BuilderScreen screen) {
    this.screen = screen;
  }

  /** Handles key release for funnel hotkey and camera vertical movement states. */
  public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
    if (screen.placementStateWheel.isOpen()) {
      return true;
    }
    if (keyCode == GLFW.GLFW_KEY_LEFT_ALT || keyCode == GLFW.GLFW_KEY_RIGHT_ALT) {
      screen.modeWheel.close();
      screen.modeWheelAltWasDown = screen.isAltDown();
      return true;
    }
    if (ClientKeyMappings.QUICK_FUNNEL.matches(keyCode, scanCode) && screen.funnelHotkeyHeld) {
      screen.funnelHotkeyHeld = false;
      screen.deactivateFunnelHotkey();
      return true;
    }
    if (screen.cameraInput.isLeftMiningActive()
        && screen.cameraInput.isKeyboardMining()
        && ClientKeyMappings.ACTION_BREAK.matches(keyCode, scanCode)) {
      screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.KEY_RELEASE);
      return true;
    }
    if (screen.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, false)) {
      return true;
    }
    return screen.forwardUnhandledKeyReleased(keyCode, scanCode, modifiers);
  }

  void handleRtsFlightToggle() {
    if (screen.getMinecraft() == null || screen.getMinecraft().player == null) return;
    if (!screen.getMinecraft().player.getAbilities().mayfly) return;

    boolean wasFlying = screen.getMinecraft().player.getAbilities().flying;
    screen.getMinecraft().player.getAbilities().flying = !wasFlying;

    // When enabling flight while on ground, apply a jump impulse to lift off.
    // Vanilla MC won't actually start flying if the player stays on ground.
    if (!wasFlying && screen.getMinecraft().player.onGround()) {
      screen.getMinecraft().player.jumpFromGround();
    }

    screen.getMinecraft().player.onUpdateAbilities();
  }

  boolean handleModeKeyPressed(int keyCode, int scanCode) {
    boolean modeKey =
        ClientKeyMappings.MODE_INTERACT.matches(keyCode, scanCode)
            || ClientKeyMappings.MODE_LINK_STORAGE.matches(keyCode, scanCode)
            || ClientKeyMappings.MODE_ROTATE.matches(keyCode, scanCode)
            || ClientKeyMappings.MODE_FUNNEL.matches(keyCode, scanCode);
    if (screen.isBlueprintPlacementModeLocked() && modeKey) {
      screen.enforceBlueprintPlacementModeLock();
      return true;
    }
    if (ClientKeyMappings.MODE_INTERACT.matches(keyCode, scanCode)) {
      return screen.switchToModeFromKey(BuilderMode.INTERACT, false);
    }
    if (ClientKeyMappings.MODE_LINK_STORAGE.matches(keyCode, scanCode)) {
      return screen.switchToModeFromKey(BuilderMode.LINK_STORAGE, false);
    }
    if (ClientKeyMappings.MODE_ROTATE.matches(keyCode, scanCode)) {
      return screen.switchToModeFromKey(BuilderMode.ROTATE, false);
    }
    if (ClientKeyMappings.MODE_FUNNEL.matches(keyCode, scanCode)) {
      return screen.switchToModeFromKey(BuilderMode.FUNNEL, false);
    }
    return false;
  }

  boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) {
    if (mode == null
        || (screen.controller.getMode() == mode
            && screen.controller.isFunnelEnabled() == funnelEnabled)) {
      return false;
    }
    screen.cameraInput.stopActiveMining(RtsMiningStopOrigin.MODE_SWITCH);
    screen.shapeController.clearShapeBuildSession();
    screen.controller.setMode(mode);
    screen.controller.setFunnelEnabled(funnelEnabled);
    screen.funnelHotkeyHeld = false;
    screen.funnelHotkeyTemporaryMode = false;
    screen.funnelMouseHoldButton = -1;
    screen.rotationHandles.clear();
    screen.closePlacementStateWheel();
    return true;
  }

  /**
   * Handles character-typed input, routing to quantity dialog, blueprint name dialog, search boxes,
   * and ultimine limit input.
   */
  public boolean charTyped(char codePoint, int modifiers) {
    if (screen.floatingWindowLayer.charTyped(codePoint, modifiers)) {
      return true;
    }
    if (screen.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
        && BlueprintPanel.charTyped(codePoint, screen.controller)) {
      return true;
    }
    if (screen.searchBox != null && screen.searchBox.isFocused()) {
      if (screen.searchBox.charTyped(codePoint, modifiers)) {
        screen.bottomPanel.handleStorageSearchChanged(screen.searchBox.getValue());
      }
      return true;
    }
    if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
      screen.craftSearchBox.charTyped(codePoint, modifiers);
      return true;
    }
    return screen.forwardUnhandledCharTyped(codePoint, modifiers);
  }
}
