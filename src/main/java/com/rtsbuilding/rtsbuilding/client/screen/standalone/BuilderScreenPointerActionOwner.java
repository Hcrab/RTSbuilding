package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.compat.create.RtsCreateGlueCompat;
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
 * BuilderScreen 的PointerActionOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenPointerActionOwner {
    private final BuilderScreen screen;

    BuilderScreenPointerActionOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    void selectPlacementStateFromWheel(
                PlacementStateWheel.PlacementChoice choice, int button) {
            screen.controller.copyPlacementState(choice.state());
            screen.closePlacementStateWheel();
            screen.placementStateWheelConsumedMouseButton = button;
        }

    void closePlacementStateWheelFromPointer(int button) {
            screen.closePlacementStateWheel();
            screen.placementStateWheelConsumedMouseButton = button;
        }

    void selectModeFromWheelPointer(BuilderMode selectedMode, int button) {
            screen.selectModeFromWheel(selectedMode);
            screen.modeWheel.close();
            screen.modeWheelConsumedMouseButton = button;
        }

    void closeModeWheelFromPointer(int button) {
            screen.modeWheel.close();
            screen.modeWheelConsumedMouseButton = button;
        }

    boolean handleBlueprintCaptureClicks(double mouseX, double mouseY, int button) {
            if (!BlueprintPanel.isCaptureModeActive()) {
                return false;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                screen.cameraInput.stopActiveMining();
                if (screen.isWorldArea(mouseX, mouseY)) {
                    BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                    BlueprintPanel.handleCaptureWorldAction(
                            hit,
                            screen.cursorPicker.currentRayOrigin(),
                            screen.cursorPicker.computeCursorRayDirection());
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                return false;
            }
            return true;
        }

    boolean handleHomeSelectionClicks(double mouseX, double mouseY, int button) {
            if (!screen.controller.isHomeSelectionMode()) {
                return false;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && screen.isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.setHome(hit.getBlockPos());
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT || button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                RtsClientPacketGateway.sendToggleCamera(screen.controller.isStartCameraAtPlayerHead());
                return true;
            }
            return true;
        }

    boolean handleOverlayClicks(double mouseX, double mouseY, int button) {
            if (screen.handleFloatingWindowClick(mouseX, mouseY, button)) {
                screen.submitCraftQuantityWindowIfReady();
                return true;
            }
            return false;
        }

    boolean handleAreaMineClickBlock(double mouseX, double mouseY, int button) {
            if (BlueprintPanel.isCaptureModeActive()) {
                return false;
            }
            if (screen.controller.getAreaMinePhase() == MiningOperationService.AREA_MINE_PHASE_NONE) {
                return false;
            }
            if (screen.isWorldArea(mouseX, mouseY) && !CameraInputHandler.isBreakActionMouse(button)) {
                return true;
            }
            return false;
        }

    boolean handleLeftClickInteractions(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            screen.enforceBlueprintPlacementModeLock();
            if (screen.topBarPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (screen.funnelBufferPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (screen.bottomPanel.handleClick(mouseX, mouseY)) {
                return true;
            }
            if (screen.pendingGuiBindSlot >= 0 && screen.isWorldArea(mouseX, mouseY)) {
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.setGuiBinding(
                            screen.pendingGuiBindSlot,
                            hit.getBlockPos(),
                            hit.getDirection(),
                            screen.resolveGuiBindingItemId(hit));
                    screen.pendingGuiBindSlot = -1;
                }
                return true;
            }
            if (screen.isWorldArea(mouseX, mouseY)
                    && screen.controller.getMode() == BuilderMode.ROTATE) {
                Vec3 origin = screen.cursorPicker.currentRayOrigin();
                Vec3 direction = screen.cursorPicker.computeCursorRayDirection();
                Direction cameraForward = screen.currentCameraHorizontalDirection();
                PlacedBlockRotationGesture gesture = screen.rotationHandles.hitGesture(
                        screen.getMinecraft().level, origin, direction, cameraForward);
                if (gesture != null && screen.rotationHandles.targetPos() != null) {
                    screen.controller.rotateBlockStep(
                            screen.rotationHandles.targetPos(),
                            gesture.axisDirection(cameraForward),
                            gesture.quarterTurns());
                    return true;
                }
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit == null || !screen.rotationHandles.select(
                        screen.getMinecraft().level,
                        hit.getBlockPos(),
                        cameraForward)) {
                    screen.rotationHandles.clear();
                    if (hit != null && screen.getMinecraft().player != null) {
                        screen.getMinecraft().player.displayClientMessage(
                                Component.translatable(
                                        "screen.rtsbuilding.rotation_wheel.unsupported"),
                                true);
                    }
                }
                screen.shapeController.clearShapeBuildSession();
                return true;
            }
            if (screen.isWorldArea(mouseX, mouseY) && screen.controller.getMode() == BuilderMode.LINK_STORAGE) {
                BlockHitResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.linkStorage(hit.getBlockPos());
                    return true;
                }
            }
            return false;
        }

    boolean handleWorldClickActions(double mouseX, double mouseY, int button) {
            if (screen.handleAdvancedShapeHandleClick(mouseX, mouseY, button)) {
                return true;
            }
            if (screen.handleBatchConfirmMouse(mouseX, mouseY, button)) {
                return true;
            }
            if (RtsCreateGlueCompat.handleWorldClick(screen, mouseX, mouseY, button)) {
                return true;
            }
            if (CameraInputHandler.isBreakActionMouse(button)
                    && CameraInputHandler.canStartBreakActionOnMouse(button)
                    && screen.cameraInput.startMiningAt(mouseX, mouseY, button, false)) {
                return true;
            }
            boolean primaryMouse = CameraInputHandler.isPrimaryActionMouse(button);
            boolean rotateMouse = CameraInputHandler.isRotateDragActionMouse(button);
            boolean panMouse = CameraInputHandler.isPanDragActionMouse(button);
            boolean pickMouse = CameraInputHandler.isPickBlockActionMouse(button);
            if (primaryMouse
                    && screen.controller.getMode() == BuilderMode.FUNNEL
                    && !screen.isMovePlayerActionMouse(button)
                    && screen.isWorldArea(mouseX, mouseY)) {
                screen.beginFunnelMouseHold(button);
            }
            /*
             * After key binding swap:
             *   Right button → primary action + camera pan (movement)
             *   Middle button → camera rotation + pick block
             */
            if (primaryMouse || rotateMouse) {
                if (screen.isSearchFocused()) {
                    screen.blurSearchFocus();
                }
                if (primaryMouse && screen.pendingGuiBindSlot >= 0 && screen.isWorldArea(mouseX, mouseY)) {
                    return true;
                }
                if (primaryMouse && screen.isInsideBottomPanel(mouseX, mouseY)) {
                    return screen.bottomPanel.handleRightClick(mouseX, mouseY);
                }
                if (screen.isMovePlayerActionMouse(button) && screen.isWorldArea(mouseX, mouseY)) {
                    return screen.handleMovePlayerActionAt(mouseX, mouseY);
                }
                if (screen.isWorldArea(mouseX, mouseY)) {
                    screen.cameraInput.beginRightPress(mouseX, mouseY, button, primaryMouse, rotateMouse);
                    return true;
                }
                return true;
            }
            if (panMouse || pickMouse) {
                screen.cameraInput.beginMiddlePress(screen.isWorldArea(mouseX, mouseY), button, panMouse, pickMouse);
                return true;
            }
            return false;
        }

    boolean handleAdvancedShapeHandleClick(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || !screen.isAdvancedShapeMode()
                    || !screen.isWorldArea(mouseX, mouseY)
                    || screen.isMouseOverFloatingWindow(mouseX, mouseY)) {
                return false;
            }
            return screen.shapeController.clickAdvancedRangeDestroyHandle(
                    screen.cursorPicker.currentRayOrigin(),
                    screen.cursorPicker.computeCursorRayDirection());
        }

    boolean handleBatchConfirmMouse(double mouseX, double mouseY, int button) {
            if (!Config.isKeyboardBatchConfirmEnabled() || !screen.isWorldArea(mouseX, mouseY) || screen.isSearchFocused()) {
                return false;
            }
            InputConstants.Key mouseKey = InputConstants.Type.MOUSE.getOrCreate(button);
            if (screen.shapeController.isAwaitingBatchDestroyConfirm()
                    && ClientKeyMappings.CONFIRM_BATCH_DESTROY.isActiveAndMatches(mouseKey)) {
                screen.shapeController.tryConfirmPendingRangeDestroy();
                return true;
            }
            if (screen.shapeController.isAwaitingBatchPlaceConfirm()
                    && ClientKeyMappings.CONFIRM_BATCH_PLACE.isActiveAndMatches(mouseKey)) {
                screen.shapeController.tryConfirmPendingShapeBuild(screen.hasShiftDown());
                return true;
            }
            return false;
        }

}
