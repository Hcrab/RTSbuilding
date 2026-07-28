package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.registry.ForgeRegistries;


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
            if (button != 0) {
                return false;
            }
            EnumFacing blueprintDirection = BlueprintPanel.getCaptureActiveHandleDirection();
            if (BlueprintPanel.isCaptureModeActive() && blueprintDirection != null) {
                double[] axis = screen.screenAxisForDirection(blueprintDirection);
                return BlueprintPanel.mouseDraggedCaptureHandle(dragX, dragY, axis[0], axis[1]);
            }
            EnumFacing cullingDirection = screen.cullingManager.activeHandleDirection();
            if (screen.cullingManager.isManagementMode() && cullingDirection != null) {
                double[] axis = screen.screenAxisForDirection(cullingDirection);
                return screen.cullingManager.handleActiveHandleDrag(dragX, dragY, axis[0], axis[1]);
            }
            EnumFacing advancedBoxDirection = screen.shapeController.advancedRangeDestroyActiveHandle();
            if (advancedBoxDirection != null) {
                double[] axis = screen.screenAxisForDirection(advancedBoxDirection);
                return screen.shapeController.dragAdvancedRangeDestroyHandle(dragX, dragY, axis[0], axis[1]);
            }
            return false;
        }

    double[] screenAxisForDirection(EnumFacing direction) {
            if (direction == null || screen.getMinecraft() == null || screen.getMinecraft().entityRenderer == null) {
                return new double[] {0.0D, -1.0D};
            }
            net.minecraft.entity.Entity camera = screen.getMinecraft().getRenderViewEntity();
            if (camera == null) return new double[] {0.0D, -1.0D};
            float yawDeg = camera.rotationYaw;
            float pitchDeg = camera.rotationPitch;
            double yaw = Math.toRadians(yawDeg);
            double pitch = Math.toRadians(pitchDeg);
            Vec3d forward = new Vec3d(
                    -Math.sin(yaw) * Math.cos(pitch),
                    -Math.sin(pitch),
                    Math.cos(yaw) * Math.cos(pitch)).normalize();
            Vec3d right = new Vec3d(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
            Vec3d up = forward.crossProduct(right).normalize();
            net.minecraft.util.math.Vec3i axis = direction.getDirectionVec();
            Vec3d normal = new Vec3d(axis.getX(), axis.getY(), axis.getZ());
            return new double[] {-normal.dotProduct(right), -normal.dotProduct(up)};
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
            if (screen.getMinecraft() == null || screen.getMinecraft().getRenderViewEntity() == null) {
                return;
            }
            String dropItemId = "";
            if (screen.controller.hasSelectedItem() && !screen.controller.getSelectedItemId().trim().isEmpty()) {
                dropItemId = screen.controller.getSelectedItemId();
            } else {
                ItemStack toolStack = screen.getSelectedToolStack();
                if (toolStack.isEmpty()) {
                    return;
                }
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(toolStack.getItem());
                if (id == null) {
                    return;
                }
                dropItemId = id.toString();
            }
            Vec3d origin = screen.cursorPicker.currentRayOrigin();
            Vec3d dir = screen.cursorPicker.computeCursorRayDirection();
            Vec3d dropPos = origin.add(dir.scale(3.25D));
            RayTraceResult hit = screen.cursorPicker.pickBlockHit(true);
            if (hit != null) {
                BlockPos p = hit.getBlockPos();
                dropPos = new Vec3d(p.getX() + 0.5D, p.getY() + 1.55D, p.getZ() + 0.5D);
            }
            screen.controller.quickDropSelectedItem(dropItemId, 1, dropPos);
        }

}
