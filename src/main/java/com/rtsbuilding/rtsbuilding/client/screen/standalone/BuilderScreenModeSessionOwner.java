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
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的ModeSessionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenModeSessionOwner {
    private final BuilderScreen screen;

    BuilderScreenModeSessionOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    boolean canUseRangeCulling() {
            return !screen.controller.isProgressionEnabled()
                    || screen.controller.hasInstalledPlugin(BuiltInRtsPluginCatalog.RANGE_CULLING_PLUGIN.toString());
        }

    boolean isRangeCullingManagementActive() {
            return screen.cullingManager.isManagementMode();
        }

    void toggleRangeCullingManagement() {
            if (!screen.canUseRangeCulling()) {
                return;
            }
            screen.cameraInput.stopActiveMining();
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
            screen.cameraInput.stopActiveMining();
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
            boolean enabled = screen.controller.getMode() == BuilderMode.FUNNEL
                    && (screen.funnelHotkeyHeld || screen.funnelMouseHoldButton >= 0);
            screen.controller.setFunnelEnabled(enabled);
        }

    void updateModeWheelAltState() {
            boolean altDown = screen.isAltDown();
            if (altDown && !screen.modeWheelAltWasDown && screen.canOpenModeWheel()) {
                screen.cameraInput.stopActiveMining();
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
            screen.cameraInput.stopActiveMining();
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
            if (direction == null || screen.getMinecraft() == null || screen.getMinecraft().gameRenderer == null) {
                return new double[] {0.0D, -1.0D};
            }
            float yawDeg = screen.getMinecraft().gameRenderer.getMainCamera().getYRot();
            float pitchDeg = screen.getMinecraft().gameRenderer.getMainCamera().getXRot();
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            Vec3 forward = new Vec3(
                    -Math.sin(yaw) * Math.cos(pitch),
                    -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(pitch)).normalize();
            Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
            Vec3 up = forward.cross(right).normalize();
            Vec3 normal = new Vec3(
                    direction.getNormal().getX(),
                    direction.getNormal().getY(),
                    direction.getNormal().getZ());
            return new double[] {-normal.dot(right), -normal.dot(up)};
        }

    void updateRangeCullingHover(double mouseX, double mouseY) {
            if (!screen.cullingManager.isManagementMode()) {
                screen.cullingManager.updateHover(null, null);
            } else if (!screen.isWorldArea(mouseX, mouseY) || screen.isMouseOverFloatingWindow(mouseX, mouseY)) {
                screen.cullingManager.updateHover(null, null);
            } else {
                screen.cullingManager.updateHover(
                        screen.cursorPicker.currentRayOrigin(),
                        screen.cursorPicker.computeCursorRayDirection());
            }
            screen.updateAdvancedRangeDestroyHover(mouseX, mouseY);
        }

    void updateAdvancedRangeDestroyHover(double mouseX, double mouseY) {
            if (!screen.isAdvancedShapeMode()) {
                screen.shapeController.updateAdvancedRangeDestroyHover(null, null, false);
                return;
            }
            boolean enabled = screen.isWorldArea(mouseX, mouseY) && !screen.isMouseOverFloatingWindow(mouseX, mouseY);
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
            if (screen.controller.getMode() == BuilderMode.INTERACT && !screen.controller.isFunnelEnabled()) {
                return;
            }
            screen.cameraInput.stopActiveMining();
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
