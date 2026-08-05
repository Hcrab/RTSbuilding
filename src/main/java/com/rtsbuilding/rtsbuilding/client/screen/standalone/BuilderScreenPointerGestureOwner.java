package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import net.minecraft.util.ChatComponentTranslation;
import org.lwjgl.input.Mouse;


import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的PointerGestureOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenPointerGestureOwner {
    private final BuilderScreen screen;

    BuilderScreenPointerGestureOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    /**
         * Handles mouse release with RTS GUI scale remapping. Routes release events to
         * open dialogs, dragging state, floating windows, and camera input handlers.
         */
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            try (RtsGuiScaleCoordinator.InputFrame frame =
                         screen.guiScaleCoordinator.beginInput()) {
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
            if (screen.cameraInput.isLeftMiningActive() && !screen.cameraInput.isKeyboardMining() && button == screen.cameraInput.getActiveMiningMouseButton()) {
                screen.cameraInput.stopActiveMining();
                return true;
            }
            if (screen.handleFloatingWindowRelease(mouseX, mouseY, button)) {
                return true;
            }
            if (button == 0
                    && BlueprintPanel.releaseCaptureActiveHandleIfDragged()) {
                return true;
            }
            if (button == 0
                    && screen.cullingManager.isManagementMode()
                    && screen.cullingManager.releaseActiveHandleIfDragged()) {
                return true;
            }
            if (button == 0
                    && screen.shapeController.releaseAdvancedRangeDestroyHandleIfDragged()) {
                return true;
            }
            if (screen.cameraInput.isRightDragActive(button)) {
                boolean runPrimary = screen.cameraInput.endRightPress(mouseX, mouseY, button);
                boolean consumed = runPrimary
                        ? screen.runPrimaryActionAt(mouseX, mouseY, button)
                        : true;
                return consumed;
            }
            if (screen.cameraInput.endMiddlePress(mouseX, mouseY, button)) {
                return true;
            }
            return screen.forwardUnhandledMouseReleased(mouseX, mouseY, button);
        }

    /**
         * Handles mouse drag with RTS GUI scale remapping. Routes drag events to
         * open dialogs, sensitivity slider dragging, floating windows, camera drag handlers,
         * and search box focus logic.
         */
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            try (RtsGuiScaleCoordinator.InputFrame frame =
                         screen.guiScaleCoordinator.beginInput()) {
                if (frame.requiresRemap()) {
                    return screen.mouseDragged(mouseX / frame.scale(), mouseY / frame.scale(), button, dragX / frame.scale(), dragY / frame.scale());
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
            if (screen.cullingManager.isManagementMode() && button == 0) {
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
            try (RtsGuiScaleCoordinator.InputFrame frame =
                         screen.guiScaleCoordinator.beginInput()) {
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
            if (screen.getMinecraft() == null || screen.getMinecraft().theWorld == null) {
                return false;
            }
            ItemStack selected = screen.controller.getSelectedItemPreview();
            if (!(selected.getItem() instanceof ItemBlock)
                    && (screen.getMinecraft().thePlayer == null
                    || !(screen.getMinecraft().thePlayer.getHeldItem().getItem() instanceof ItemBlock))) {
                return false;
            }
            RayTraceResult hit = screen.cursorPicker.pickBlockHit();
            BlockPos targetPos = hit == null
                    ? null
                    : BlockState.fromWorld(screen.getMinecraft().theWorld, hit.getBlockPos()).getBlock()
                            .isReplaceable(screen.getMinecraft().theWorld,
                                    hit.getBlockPos().getX(), hit.getBlockPos().getY(), hit.getBlockPos().getZ())
                            ? hit.getBlockPos()
                            : hit.getBlockPos().offset(hit.sideHit);
            BlockState state = BuildGhostBlockStateResolver.resolve(screen.getMinecraft(), targetPos);
            if (state == null) {
                return false;
            }
            net.minecraft.entity.Entity camera = screen.getMinecraft().renderViewEntity;
            if (camera == null) return false;
            int uiWidth = screen.guiScaleCoordinator.viewportWidth();
            int uiHeight = screen.guiScaleCoordinator.viewportHeight();
            if (!screen.placementStateWheel.open(
                    state, mouseX, mouseY, uiWidth, uiHeight, camera.rotationYaw, camera.rotationPitch)) {
                if (screen.getMinecraft().thePlayer != null) {
                    com.rtsbuilding.rtsbuilding.platform.chat.ChatMessages.sendStatus(screen.getMinecraft().thePlayer,
                            new ChatComponentTranslation("screen.rtsbuilding.placement_state_wheel.unsupported"), true);
                }
                return true;
            }
            RtsPlacementRayFreeze.clear();
            screen.placementWheelRestoreMouseX = Mouse.getX();
            screen.placementWheelRestoreMouseY = Mouse.getY();
            RtsPlacementRayFreeze.freeze(
                    screen.cursorPicker.currentRayOrigin(),
                    screen.cursorPicker.computeCursorRayDirection());
            screen.cameraInput.stopActiveMining();
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
                    && Double.isFinite(screen.placementWheelRestoreMouseX)
                    && Double.isFinite(screen.placementWheelRestoreMouseY)) {
                Mouse.setCursorPosition(
                        (int) Math.round(screen.placementWheelRestoreMouseX),
                        (int) Math.round(screen.placementWheelRestoreMouseY));
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
