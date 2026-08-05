package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.settings.KeyBinding;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的KeyboardSessionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
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
            if (keyCode == Keyboard.KEY_LMENU || keyCode == Keyboard.KEY_RMENU) {
                screen.modeWheel.close();
                screen.modeWheelAltWasDown = screen.isAltDown();
                return true;
            }
            if (matches(ClientKeyMappings.QUICK_FUNNEL, keyCode) && screen.funnelHotkeyHeld) {
                screen.funnelHotkeyHeld = false;
                screen.deactivateFunnelHotkey();
                return true;
            }
            if (screen.cameraInput.isLeftMiningActive()
                    && screen.cameraInput.isKeyboardMining()
                    && matches(ClientKeyMappings.ACTION_BREAK, keyCode)) {
                screen.cameraInput.stopActiveMining();
                return true;
            }
            if (screen.cameraInput.updateCameraVerticalHeldState(keyCode, scanCode, false)) {
                return true;
            }
            return screen.forwardUnhandledKeyReleased(keyCode, scanCode, modifiers);
        }

    void handleRtsFlightToggle() {
            if (screen.getMinecraft() == null || screen.getMinecraft().thePlayer == null) return;
            if (!screen.getMinecraft().thePlayer.capabilities.allowFlying) return;

            boolean wasFlying = screen.getMinecraft().thePlayer.capabilities.isFlying;
            screen.getMinecraft().thePlayer.capabilities.isFlying = !wasFlying;

            // When enabling flight while on ground, apply a jump impulse to lift off.
            // Vanilla MC won't actually start flying if the player stays on ground.
            if (!wasFlying && screen.getMinecraft().thePlayer.onGround) {
                screen.getMinecraft().thePlayer.jump();
            }

            screen.getMinecraft().thePlayer.sendPlayerAbilities();
        }

    boolean handleModeKeyPressed(int keyCode, int scanCode) {
            boolean modeKey = matches(ClientKeyMappings.MODE_INTERACT, keyCode)
                    || matches(ClientKeyMappings.MODE_LINK_STORAGE, keyCode)
                    || matches(ClientKeyMappings.MODE_ROTATE, keyCode)
                    || matches(ClientKeyMappings.MODE_FUNNEL, keyCode);
            if (screen.isBlueprintPlacementModeLocked() && modeKey) {
                screen.enforceBlueprintPlacementModeLock();
                return true;
            }
            if (matches(ClientKeyMappings.MODE_INTERACT, keyCode)) {
                return screen.switchToModeFromKey(BuilderMode.INTERACT, false);
            }
            if (matches(ClientKeyMappings.MODE_LINK_STORAGE, keyCode)) {
                return screen.switchToModeFromKey(BuilderMode.LINK_STORAGE, false);
            }
            if (matches(ClientKeyMappings.MODE_ROTATE, keyCode)) {
                return screen.switchToModeFromKey(BuilderMode.ROTATE, false);
            }
            if (matches(ClientKeyMappings.MODE_FUNNEL, keyCode)) {
                return screen.switchToModeFromKey(BuilderMode.FUNNEL, false);
            }
            return false;
        }

    boolean switchToModeFromKey(BuilderMode mode, boolean funnelEnabled) {
            if (mode == null || (screen.controller.getMode() == mode && screen.controller.isFunnelEnabled() == funnelEnabled)) {
                return false;
            }
            screen.cameraInput.stopActiveMining();
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

    /** Handles character-typed input, routing to quantity dialog, blueprint name dialog, search boxes, and ultimine limit input. */
        public boolean charTyped(char codePoint, int modifiers) {
            if (screen.floatingWindowLayer.charTyped(codePoint, modifiers)) {
                return true;
            }
            if (screen.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS
                    && BlueprintPanel.charTyped(codePoint, screen.controller)) {
                return true;
            }
            if (screen.searchBox != null && screen.searchBox.isFocused()) {
                if (screen.searchBox.textboxKeyTyped(codePoint, 0)) {
                    screen.bottomPanel.handleStorageSearchChanged(screen.searchBox.getText());
                }
                return true;
            }
            if (screen.craftSearchBox != null && screen.craftSearchBox.isFocused()) {
                screen.craftSearchBox.textboxKeyTyped(codePoint, 0);
                return true;
            }
            return screen.forwardUnhandledCharTyped(codePoint, modifiers);
        }

    private static boolean matches(KeyBinding binding, int keyCode) {
        return binding != null && binding.getKeyCode() == keyCode;
    }
}
