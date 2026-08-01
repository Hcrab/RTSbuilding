package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.RtsAiChatPanel;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.TooltipStyle;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;

import java.util.List;

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
                screen.cameraInput.stopActiveMining();
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
            boolean modeKey = ClientKeyMappings.MODE_INTERACT.matches(keyCode, scanCode)
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
