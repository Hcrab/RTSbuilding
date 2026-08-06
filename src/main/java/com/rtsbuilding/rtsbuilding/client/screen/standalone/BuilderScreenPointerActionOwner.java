package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;


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
            if (button == 0) {
                screen.cameraInput.stopActiveMining();
                if (screen.isWorldArea(mouseX, mouseY)) {
                    RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                    BlueprintPanel.handleCaptureWorldAction(
                            hit,
                            screen.cursorPicker.currentRayOrigin(),
                            screen.cursorPicker.computeCursorRayDirection());
                }
                return true;
            }
            if (button == 1 || button == 2) {
                return false;
            }
            return true;
        }

    boolean handleHomeSelectionClicks(double mouseX, double mouseY, int button) {
            if (!screen.controller.isHomeSelectionMode()) {
                return false;
            }
            if (button == 0 && screen.isWorldArea(mouseX, mouseY)) {
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.setHome(hit.getBlockPos());
                }
                return true;
            }
            if (button == 1 || button == 2) {
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
            if (button != 0) {
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
            if (screen.handleStorageBatchWorldClick(mouseX, mouseY)) {
                return true;
            }
            if (screen.pendingGuiBindSlot >= 0 && screen.isWorldArea(mouseX, mouseY)) {
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.setGuiBinding(
                            screen.pendingGuiBindSlot,
                            hit.getBlockPos(),
                            hit.sideHit,
                            screen.resolveGuiBindingItemId(hit));
                    screen.pendingGuiBindSlot = -1;
                }
                return true;
            }
            if (screen.isWorldArea(mouseX, mouseY)
                    && screen.controller.getMode() == BuilderMode.ROTATE) {
                Vec3d origin = screen.cursorPicker.currentRayOrigin();
                Vec3d direction = screen.cursorPicker.computeCursorRayDirection();
                EnumFacing cameraForward = screen.currentCameraHorizontalDirection();
                PlacedBlockRotationGesture gesture = screen.rotationHandles.hitGesture(
                        screen.getMinecraft().world, origin, direction, cameraForward);
                if (gesture != null && screen.rotationHandles.targetPos() != null) {
                    screen.controller.rotateBlockStep(
                            screen.rotationHandles.targetPos(),
                            gesture.axisDirection(cameraForward),
                            gesture.quarterTurns());
                    return true;
                }
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit == null || !screen.rotationHandles.select(
                        screen.getMinecraft().world,
                        hit.getBlockPos(),
                        cameraForward)) {
                    screen.rotationHandles.clear();
                    if (hit != null && screen.getMinecraft().player != null) {
                        screen.getMinecraft().player.sendStatusMessage(
                                new TextComponentTranslation(
                                        "screen.rtsbuilding.rotation_wheel.unsupported"),
                                true);
                    }
                }
                screen.shapeController.clearShapeBuildSession();
                return true;
            }
            if (screen.isWorldArea(mouseX, mouseY) && screen.controller.getMode() == BuilderMode.LINK_STORAGE) {
                RayTraceResult hit = screen.cursorPicker.pickBlockHit();
                if (hit != null) {
                    screen.controller.linkStorage(hit.getBlockPos());
                    return true;
                }
            }
            return false;
        }

    boolean handleStorageBatchWorldClick(double mouseX, double mouseY) {
        if (!screen.storageBatchSelection.isActive()
                || screen.controller.getMode() != BuilderMode.LINK_STORAGE
                || !screen.isWorldArea(mouseX, mouseY)) {
            return false;
        }
        RayTraceResult hit = screen.cursorPicker.pickBlockHit();
        return screen.storageBatchSelection.click(
                screen.getMinecraft(), hit == null ? null : hit.getBlockPos());
    }

    boolean handleWorldClickActions(double mouseX, double mouseY, int button) {
            if (screen.handleAdvancedShapeHandleClick(mouseX, mouseY, button)) {
                return true;
            }
            if (screen.handleBatchConfirmMouse(mouseX, mouseY, button)) {
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
            if (button != 0
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
            if (screen.shapeController.isAwaitingBatchDestroyConfirm()
                    && ClientKeyMappings.CONFIRM_BATCH_DESTROY.getKeyCode() == button - 100) {
                screen.shapeController.tryConfirmPendingRangeDestroy();
                return true;
            }
            if (screen.shapeController.isAwaitingBatchPlaceConfirm()
                    && ClientKeyMappings.CONFIRM_BATCH_PLACE.getKeyCode() == button - 100) {
                screen.shapeController.tryConfirmPendingShapeBuild(GuiScreen.isShiftKeyDown());
                return true;
            }
            return false;
        }

}
