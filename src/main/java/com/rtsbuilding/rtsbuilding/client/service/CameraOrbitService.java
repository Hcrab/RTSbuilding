package com.rtsbuilding.rtsbuilding.client.service;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;

/**
 * Manages camera orbit, pan, dolly, rotation sensitivity, smoothing, and
 * the local render-mirror camera entity on the client side.
 * <p>
 * Extracted from {@link com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController}
 * to reduce its size and isolate camera-specific concerns.
 */
public final class CameraOrbitService {

    // =========================================================================
    //  Constants
    // =========================================================================

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float MAX_SMOOTH_ROTATE_ACCUMULATION = 160.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final float ROT_SENS_MIN = 1.00F;
    private static final float ROT_SENS_MAX = 10.00F;
    private static final float ROT_SENS_STEP = 0.50F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final float[] INPUT_SENS_PRESETS = new float[]{0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F};
    private static final int INPUT_SENS_DEFAULT_INDEX = 2;
    private static final float SMOOTH_TICK_SECONDS = 0.05F;
    private static final float MOVE_ACCELERATION_SECONDS = 0.055F;
    private static final float MOVE_DECELERATION_SECONDS = 0.050F;
    private static final float MOVE_SMOOTH_EPSILON = 0.002F;
    private static final float SCROLL_RESPONSE_SECONDS = 0.045F;
    private static final float SCROLL_SMOOTH_EPSILON = 0.0005F;
    private static final float MAX_SMOOTH_SCROLL_REMAINING = 16.0F;
    private static final float VISUAL_POSITION_RESPONSE_SECONDS = 0.018F;
    private static final float VISUAL_ROTATION_RESPONSE_SECONDS = 0.014F;
    private static final double VISUAL_POSITION_EPSILON = 1.0e-4D;
    private static final float VISUAL_ROTATION_EPSILON = 1.0e-3F;
    private static final double MIN_CAMERA_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_CAMERA_HEIGHT_OFFSET = 110.0D;
    private static final float MIN_CAMERA_PITCH = -90.0F;
    private static final float MAX_CAMERA_PITCH = 90.0F;
    private static final float CAMERA_INPUT_EPSILON = 1.0e-4F;
    private static final int CAMERA_IDLE_HEARTBEAT_TICKS = 20;
    private static final int CAMERA_RESTORE_COOLDOWN_TICKS = 10;
    private static final float MAX_SMOOTH_FRAME_TICKS = 2.00F;

    // =========================================================================
    //  Fields — rotate capture
    // =========================================================================

    private boolean rotateCaptured;
    private double restoreCursorX;
    private double restoreCursorY;

    // =========================================================================
    //  Fields — pending input accumulation
    // =========================================================================

    private float pendingPanX;
    private float pendingPanY;
    private float pendingScroll;
    private float pendingNetworkScroll;
    private float smoothScrollRemaining;
    private int pendingRotateSteps;
    private float pendingSmoothRotateX;
    private float pendingSmoothRotateY;

    // =========================================================================
    //  Fields — movement smoothing
    // =========================================================================

    private float smoothForward;
    private float smoothStrafe;
    private float smoothVertical;
    private int cameraMoveHeartbeatTicks;
    private int cameraRestoreCooldownTicks;
    private long lastSmoothCameraFrameNanos;

    // =========================================================================
    //  Fields — sensitivity & smoothing preferences
    // =========================================================================

    private float rotateSensitivity = 5.00F;
    private int inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
    private int panDragSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
    private int rotateViewSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
    private int keyboardMoveSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
    private int wheelZoomSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
    private boolean smoothCamera;
    private boolean invertPanDragX;
    private boolean invertPanDragY;

    // =========================================================================
    //  Fields — local camera pose
    // =========================================================================

    private boolean localStateReady;
    private double localX;
    private double localY;
    private double localZ;
    private double localHeightOffset;
    private float localYawDeg;
    private float localPitchDeg;

    // 渲染姿态与逻辑姿态分离：逻辑姿态和服务端保持同一份输入积分，
    // 镜像相机只用这组视觉姿态做很短的帧间追随，避免双重积分造成漂移。
    private boolean visualPoseReady;
    private double visualX;
    private double visualY;
    private double visualZ;
    private float visualYawDeg;
    private float visualPitchDeg;

    // =========================================================================
    //  Fields — mirror camera & previous view restoration
    // =========================================================================

    private RtsCameraEntity localMirrorCamera;
    private Entity previousCameraEntity;
    private CameraType previousCameraType = CameraType.FIRST_PERSON;
    private boolean previousBobView = true;
    private double previousFovEffectScale = 1.0D;
    private int serverCameraEntityId = -1;

    // =========================================================================
    //  Lifecycle — enable / disable
    // =========================================================================

    /** Called by the controller when receiving an enabled camera state. */
    public void capturePreviousView(Minecraft minecraft) {
        this.previousCameraEntity = minecraft.getCameraEntity();
        this.previousCameraType = minecraft.options.getCameraType();
        this.previousBobView = minecraft.options.bobView().get();
        this.previousFovEffectScale = minecraft.options.fovEffectScale().get();
    }

    /** Called by the controller when receiving an enabled camera state. */
    public void applyEnabledPose(double anchorX, double anchorY, double anchorZ,
                                  double heightOffset, float yawDeg, float pitchDeg) {
        this.localHeightOffset = heightOffset;
        this.localYawDeg = yawDeg;
        this.localPitchDeg = pitchDeg;
        this.localX = anchorX;
        this.localY = anchorY + heightOffset;
        this.localZ = anchorZ;
        this.localStateReady = true;

        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingNetworkScroll = 0.0F;
        this.smoothScrollRemaining = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingSmoothRotateX = 0.0F;
        this.pendingSmoothRotateY = 0.0F;
        this.smoothForward = 0.0F;
        this.smoothStrafe = 0.0F;
        this.smoothVertical = 0.0F;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
        snapVisualPoseToLocal();
    }

    /** Called by the controller when disabling the camera (normal disable). */
    public void clearState() {
        this.previousCameraEntity = null;
        this.localMirrorCamera = null;
        this.localStateReady = false;
        this.lastSmoothCameraFrameNanos = 0L;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingNetworkScroll = 0.0F;
        this.smoothScrollRemaining = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingSmoothRotateX = 0.0F;
        this.pendingSmoothRotateY = 0.0F;
        this.smoothForward = 0.0F;
        this.smoothStrafe = 0.0F;
        this.smoothVertical = 0.0F;
        this.visualPoseReady = false;
    }

    /** Called by the controller when disabling on death. */
    public void clearStateOnDeath() {
        this.localStateReady = false;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
        this.smoothForward = 0.0F;
        this.smoothStrafe = 0.0F;
        this.smoothVertical = 0.0F;
        this.smoothScrollRemaining = 0.0F;
        this.pendingNetworkScroll = 0.0F;
        this.pendingSmoothRotateX = 0.0F;
        this.pendingSmoothRotateY = 0.0F;
        this.visualPoseReady = false;
        this.previousCameraEntity = null;
        this.localMirrorCamera = null;
    }

    /** Restores the player's previous camera entity and view settings. */
    public void restorePreviousView(Minecraft minecraft, Entity fallbackEntity) {
        Entity restore = this.previousCameraEntity != null ? this.previousCameraEntity : fallbackEntity;
        minecraft.setCameraEntity(restore);
        minecraft.options.setCameraType(this.previousCameraType);
        minecraft.options.bobView().set(this.previousBobView);
        minecraft.options.fovEffectScale().set(this.previousFovEffectScale);
    }

    /** Sets the RTS camera view (FPP, no bobbing, no FOV effect). */
    public void applyRtsView(Minecraft minecraft) {
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.options.bobView().set(false);
        minecraft.options.fovEffectScale().set(0.0D);
    }

    /**
     * Clears the pending cursor position so that on the next enable the
     * previous captured state is empty.
     */
    public void clearRestoreCursor() {
        this.restoreCursorX = 0.0D;
        this.restoreCursorY = 0.0D;
    }

    /**
     * Resets input accumulation (pan, scroll, rotate) and smooth-camera timestamp.
     */
    public void resetInputAccumulation() {
        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingNetworkScroll = 0.0F;
        this.smoothScrollRemaining = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingSmoothRotateX = 0.0F;
        this.pendingSmoothRotateY = 0.0F;
        this.smoothForward = 0.0F;
        this.smoothStrafe = 0.0F;
        this.smoothVertical = 0.0F;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
    }

    // =========================================================================
    //  Server entity ID
    // =========================================================================

    public void setServerCameraEntityId(int id) {
        this.serverCameraEntityId = id;
    }

    public int getServerCameraEntityId() {
        return this.serverCameraEntityId;
    }

    public void resetServerCameraEntityId() {
        this.serverCameraEntityId = -1;
    }

    // =========================================================================
    //  Local state ready
    // =========================================================================

    public boolean isLocalStateReady() {
        return this.localStateReady;
    }

    public void setLocalStateReady(boolean ready) {
        this.localStateReady = ready;
    }

    // =========================================================================
    //  Rotate capture
    // =========================================================================

    public void beginRotateCapture(double cursorX, double cursorY) {
        if (this.rotateCaptured) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        this.rotateCaptured = true;
        this.restoreCursorX = cursorX;
        this.restoreCursorY = cursorY;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    public void endRotateCapture(double fallbackX, double fallbackY) {
        if (!this.rotateCaptured) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        this.rotateCaptured = false;
        GLFW.glfwSetInputMode(minecraft.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        double x = this.restoreCursorX == 0.0D ? fallbackX : this.restoreCursorX;
        double y = this.restoreCursorY == 0.0D ? fallbackY : this.restoreCursorY;
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), x, y);
    }

    public boolean isRotateCaptured() {
        return this.rotateCaptured;
    }

    // =========================================================================
    //  Sensitivity
    // =========================================================================

    public float getRotateSensitivity() {
        return this.rotateSensitivity;
    }

    public void increaseRotateSensitivity() {
        this.rotateSensitivity = Mth.clamp(this.rotateSensitivity + ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public void decreaseRotateSensitivity() {
        this.rotateSensitivity = Mth.clamp(this.rotateSensitivity - ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public String getInputSensitivityLabel() {
        return sensitivityLabel(this.rotateViewSensitivityIndex);
    }

    public int getInputSensitivityIndex() {
        if (this.inputSensitivityIndex < 0 || this.inputSensitivityIndex >= INPUT_SENS_PRESETS.length) {
            this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        }
        return this.inputSensitivityIndex;
    }

    public int getInputSensitivityPresetCount() {
        return INPUT_SENS_PRESETS.length;
    }

    public void setInputSensitivityByFraction(double fraction) {
        int next = sensitivityIndexFromFraction(fraction);
        this.inputSensitivityIndex = next;
        this.panDragSensitivityIndex = next;
        this.rotateViewSensitivityIndex = next;
        this.keyboardMoveSensitivityIndex = next;
        this.wheelZoomSensitivityIndex = next;
    }

    public void cycleInputSensitivity() {
        int next = (getInputSensitivityIndex() + 1) % INPUT_SENS_PRESETS.length;
        this.inputSensitivityIndex = next;
        this.panDragSensitivityIndex = next;
        this.rotateViewSensitivityIndex = next;
        this.keyboardMoveSensitivityIndex = next;
        this.wheelZoomSensitivityIndex = next;
    }

    private float getInputSensitivityScale() {
        return getRotateViewSensitivityScale();
    }

    public String getPanDragSensitivityLabel() {
        return sensitivityLabel(this.panDragSensitivityIndex);
    }

    public int getPanDragSensitivityIndex() {
        this.panDragSensitivityIndex = sanitizeSensitivityIndex(this.panDragSensitivityIndex);
        return this.panDragSensitivityIndex;
    }

    public void setPanDragSensitivityByFraction(double fraction) {
        this.panDragSensitivityIndex = sensitivityIndexFromFraction(fraction);
    }

    public String getRotateViewSensitivityLabel() {
        return sensitivityLabel(this.rotateViewSensitivityIndex);
    }

    public int getRotateViewSensitivityIndex() {
        this.rotateViewSensitivityIndex = sanitizeSensitivityIndex(this.rotateViewSensitivityIndex);
        return this.rotateViewSensitivityIndex;
    }

    public void setRotateViewSensitivityByFraction(double fraction) {
        this.rotateViewSensitivityIndex = sensitivityIndexFromFraction(fraction);
        this.inputSensitivityIndex = this.rotateViewSensitivityIndex;
    }

    public String getKeyboardMoveSensitivityLabel() {
        return sensitivityLabel(this.keyboardMoveSensitivityIndex);
    }

    public int getKeyboardMoveSensitivityIndex() {
        this.keyboardMoveSensitivityIndex = sanitizeSensitivityIndex(this.keyboardMoveSensitivityIndex);
        return this.keyboardMoveSensitivityIndex;
    }

    public void setKeyboardMoveSensitivityByFraction(double fraction) {
        this.keyboardMoveSensitivityIndex = sensitivityIndexFromFraction(fraction);
    }

    public String getWheelZoomSensitivityLabel() {
        return sensitivityLabel(this.wheelZoomSensitivityIndex);
    }

    public int getWheelZoomSensitivityIndex() {
        this.wheelZoomSensitivityIndex = sanitizeSensitivityIndex(this.wheelZoomSensitivityIndex);
        return this.wheelZoomSensitivityIndex;
    }

    public void setWheelZoomSensitivityByFraction(double fraction) {
        this.wheelZoomSensitivityIndex = sensitivityIndexFromFraction(fraction);
    }

    private float getPanDragSensitivityScale() {
        return INPUT_SENS_PRESETS[getPanDragSensitivityIndex()];
    }

    private float getRotateViewSensitivityScale() {
        return INPUT_SENS_PRESETS[getRotateViewSensitivityIndex()];
    }

    private float getKeyboardMoveSensitivityScale() {
        return INPUT_SENS_PRESETS[getKeyboardMoveSensitivityIndex()];
    }

    private float getWheelZoomSensitivityScale() {
        return INPUT_SENS_PRESETS[getWheelZoomSensitivityIndex()];
    }

    private static String sensitivityLabel(int index) {
        return String.format(java.util.Locale.ROOT, "x%.2f", INPUT_SENS_PRESETS[sanitizeSensitivityIndex(index)]);
    }

    private static int sensitivityIndexFromFraction(double fraction) {
        double clamped = Mth.clamp(fraction, 0.0D, 1.0D);
        int next = (int) Math.round(clamped * (INPUT_SENS_PRESETS.length - 1));
        return Mth.clamp(next, 0, INPUT_SENS_PRESETS.length - 1);
    }

    private static int sanitizeSensitivityIndex(int index) {
        return Mth.clamp(index, 0, INPUT_SENS_PRESETS.length - 1);
    }

    // =========================================================================
    //  Smooth camera
    // =========================================================================

    public boolean isSmoothCamera() {
        return this.smoothCamera;
    }

    public void setSmoothCamera(boolean smoothCamera) {
        if (this.smoothCamera != smoothCamera) {
            this.lastSmoothCameraFrameNanos = 0L;
            this.smoothForward = 0.0F;
            this.smoothStrafe = 0.0F;
            this.smoothVertical = 0.0F;
            if (smoothCamera) {
                this.smoothScrollRemaining = Mth.clamp(
                        this.smoothScrollRemaining + this.pendingScroll,
                        -MAX_SMOOTH_SCROLL_REMAINING,
                        MAX_SMOOTH_SCROLL_REMAINING);
                this.pendingScroll = 0.0F;
            } else {
                this.pendingScroll += this.smoothScrollRemaining;
                this.smoothScrollRemaining = 0.0F;
            }
            snapVisualPoseToLocal();
        }
        this.smoothCamera = smoothCamera;
    }

    public void toggleSmoothCamera() {
        setSmoothCamera(!this.smoothCamera);
    }

    // =========================================================================
    //  Invert pan drag
    // =========================================================================

    public boolean isInvertPanDragX() {
        return this.invertPanDragX;
    }

    public void setInvertPanDragX(boolean invert) {
        this.invertPanDragX = invert;
    }

    public void toggleInvertPanDragX() {
        this.invertPanDragX = !this.invertPanDragX;
    }

    public boolean isInvertPanDragY() {
        return this.invertPanDragY;
    }

    public void setInvertPanDragY(boolean invert) {
        this.invertPanDragY = invert;
    }

    public void toggleInvertPanDragY() {
        this.invertPanDragY = !this.invertPanDragY;
    }

    // =========================================================================
    //  Local camera pose getters
    // =========================================================================

    public double getLocalX() {
        return this.localX;
    }

    public double getLocalY() {
        return this.localY;
    }

    public double getLocalZ() {
        return this.localZ;
    }

    public double getLocalHeightOffset() {
        return this.localHeightOffset;
    }

    public float getLocalYawDeg() {
        return this.localYawDeg;
    }

    public float getLocalPitchDeg() {
        return this.localPitchDeg;
    }

    public RtsCameraEntity getLocalMirrorCamera() {
        return this.localMirrorCamera;
    }

    // =========================================================================
    //  Input queueing
    // =========================================================================

    public void queuePanDrag(double dragX, double dragY) {
        float signedDragX = (float) dragX;
        float signedDragY = (float) dragY;
        float scale = getPanDragSensitivityScale();
        float panX = (this.invertPanDragX ? signedDragX : -signedDragX) * scale;
        float panY = (this.invertPanDragY ? signedDragY : -signedDragY) * scale;
        this.pendingPanX += panX;
        this.pendingPanY += panY;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, panX, panY, 0.0F, 0.0F, 0.0F, 0, false);
        }
    }

    public void queueRotateDrag(double dragX, double dragY) {
        // 鼠标旋转不再经过速度 EMA：每个输入事件立刻更新目标朝向，松手即停。
        applyImmediateRotation((float) dragX, (float) dragY);
    }

    public void queueScroll(double scrollY) {
        float scroll = (float) scrollY * getWheelZoomSensitivityScale();
        if (this.smoothCamera) {
            this.smoothScrollRemaining = Mth.clamp(
                    this.smoothScrollRemaining + scroll,
                    -MAX_SMOOTH_SCROLL_REMAINING,
                    MAX_SMOOTH_SCROLL_REMAINING);
        } else {
            this.pendingScroll += scroll;
        }
    }

    public void queueRotateQuarter(int direction) {
        this.pendingRotateSteps += direction;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, direction, false);
        }
    }

    // =========================================================================
    //  Immediate camera input (used by smooth camera)
    // =========================================================================

    private void applyImmediateRotation(float dragX, float dragY) {
        if (!this.localStateReady) {
            return;
        }
        float sens = getRotateViewSensitivityScale() * this.rotateSensitivity;
        float requestedYaw = Mth.clamp(dragX * sens, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float requestedPitch = Mth.clamp(dragY * sens, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float nextYawTotal = Mth.clamp(
                this.pendingSmoothRotateX + requestedYaw,
                -MAX_SMOOTH_ROTATE_ACCUMULATION, MAX_SMOOTH_ROTATE_ACCUMULATION);
        float nextPitchTotal = Mth.clamp(
                this.pendingSmoothRotateY + requestedPitch,
                -MAX_SMOOTH_ROTATE_ACCUMULATION, MAX_SMOOTH_ROTATE_ACCUMULATION);
        float yawDelta = nextYawTotal - this.pendingSmoothRotateX;
        float pitchDelta = nextPitchTotal - this.pendingSmoothRotateY;
        this.pendingSmoothRotateX = nextYawTotal;
        this.pendingSmoothRotateY = nextPitchTotal;
        applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, yawDelta, pitchDelta, 0.0F, 0, false);
    }

    private void applyImmediateCameraInput(float forward, float strafe, float vertical,
                                            float panX, float panY, float rotateX, float rotateY,
                                            float scroll, int rotateSteps, boolean fast) {
        if (!this.localStateReady) {
            return;
        }
        applyLocalPrediction(forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll, rotateSteps, fast);
        snapVisualMirrorCameraPose();
    }

    // =========================================================================
    //  Tick
    // =========================================================================

    /**
     * Processes accumulated camera input for this tick and sends camera-move
     * packets to the server.
     *
     * @param minecraft Minecraft instance
     * @param anchorX   current RTS anchor X
     * @param anchorY   current RTS anchor Y
     * @param anchorZ   current RTS anchor Z
     * @param maxRadius current RTS max radius
     */
    public void tick(Minecraft minecraft, double anchorX, double anchorY, double anchorZ, double maxRadius) {
        // Keep the service's internal anchor fields in sync with the latest
        // values from the controller so that applyLocalPrediction and
        // visual-frame syncing use the correct, up-to-date bounds.
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.maxRadius = maxRadius;

        CameraInput cameraInput = readCameraInput(minecraft);
        float keyboardScale = getKeyboardMoveSensitivityScale();
        if (this.smoothCamera) {
            this.smoothForward = RtsCameraSmoothingMath.approachAxis(
                    this.smoothForward, cameraInput.forward, SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
            this.smoothStrafe = RtsCameraSmoothingMath.approachAxis(
                    this.smoothStrafe, cameraInput.strafe, SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
            this.smoothVertical = RtsCameraSmoothingMath.approachAxis(
                    this.smoothVertical, cameraInput.vertical, SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
        } else {
            this.smoothForward = cameraInput.forward;
            this.smoothStrafe = cameraInput.strafe;
            this.smoothVertical = cameraInput.vertical;
        }
        float forward = this.smoothForward * keyboardScale;
        float strafe = this.smoothStrafe * keyboardScale;
        float vertical = this.smoothVertical * keyboardScale;
        boolean fast = cameraInput.fast;

        // 本地目标已在鼠标事件到达时更新；tick 只把同一批有界输入同步给服务端。
        float rotateXForTick = this.pendingSmoothRotateX;
        float rotateYForTick = this.pendingSmoothRotateY;
        float localScrollForTick = this.smoothCamera ? 0.0F : this.pendingScroll;
        float scrollForTick = this.pendingScroll + this.pendingNetworkScroll;
        if (Math.abs(rotateXForTick) < CAMERA_INPUT_EPSILON) {
            rotateXForTick = 0.0F;
        }
        if (Math.abs(rotateYForTick) < CAMERA_INPUT_EPSILON) {
            rotateYForTick = 0.0F;
        }
        if (Math.abs(scrollForTick) < CAMERA_INPUT_EPSILON) {
            scrollForTick = 0.0F;
        }

        boolean hasCameraInput = forward != 0.0F || strafe != 0.0F || vertical != 0.0F
                || Math.abs(this.pendingPanX) > CAMERA_INPUT_EPSILON
                || Math.abs(this.pendingPanY) > CAMERA_INPUT_EPSILON
                || rotateXForTick != 0.0F || rotateYForTick != 0.0F
                || scrollForTick != 0.0F || this.pendingRotateSteps != 0;
        if (hasCameraInput && !this.smoothCamera) {
            this.applyLocalPrediction(
                    forward, strafe, vertical,
                    this.pendingPanX, this.pendingPanY,
                    0.0F, 0.0F,
                    localScrollForTick, this.pendingRotateSteps, fast);
        }

        if (hasCameraInput || ++this.cameraMoveHeartbeatTicks >= CAMERA_IDLE_HEARTBEAT_TICKS) {
            RtsClientPacketGateway.sendCameraMove(
                    forward, strafe,
                    hasCameraInput ? vertical : 0.0F,
                    hasCameraInput ? this.pendingPanX : 0.0F,
                    hasCameraInput ? this.pendingPanY : 0.0F,
                    hasCameraInput ? rotateXForTick : 0.0F,
                    hasCameraInput ? rotateYForTick : 0.0F,
                    hasCameraInput ? scrollForTick : 0.0F,
                    hasCameraInput ? this.pendingRotateSteps : 0,
                    fast);
            this.cameraMoveHeartbeatTicks = 0;
        }

        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingNetworkScroll = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingSmoothRotateX = 0.0F;
        this.pendingSmoothRotateY = 0.0F;
    }

    // =========================================================================
    //  Mirror camera & sync visual frame
    // =========================================================================

    /**
     * Ensures the local mirror camera exists and syncs the visual camera frame.
     * 由逐帧事件以及服务端初始姿态同步调用；普通客户端 tick 不再推进视觉时间基。
     */
    public void syncVisualCameraFrame(Minecraft minecraft, double anchorX, double anchorY, double anchorZ,
                                       double maxRadius, boolean rtsEnabled) {
        if (!rtsEnabled || !this.localStateReady) {
            return;
        }
        if (minecraft.level == null) {
            return;
        }

        this.ensureLocalMirrorCamera(minecraft);
        if (this.localMirrorCamera == null) {
            return;
        }

        float frameSeconds = smoothFrameDeltaSeconds();
        if (this.smoothCamera) {
            applySmoothFrameMovement(minecraft, frameSeconds);
            updateVisualPose(frameSeconds);
        } else {
            this.lastSmoothCameraFrameNanos = 0L;
            snapVisualPoseToLocal();
        }

        snapVisualMirrorCameraPose();

        if (minecraft.getCameraEntity() != this.localMirrorCamera) {
            if (this.cameraRestoreCooldownTicks <= 0) {
                minecraft.setCameraEntity(this.localMirrorCamera);
                this.cameraRestoreCooldownTicks = CAMERA_RESTORE_COOLDOWN_TICKS;
            } else {
                this.cameraRestoreCooldownTicks--;
            }
        } else if (this.cameraRestoreCooldownTicks > 0) {
            this.cameraRestoreCooldownTicks--;
        }
    }

    private float smoothFrameDeltaSeconds() {
        long now = System.nanoTime();
        if (this.lastSmoothCameraFrameNanos == 0L) {
            this.lastSmoothCameraFrameNanos = now;
            return 0.0F;
        }

        long elapsed = now - this.lastSmoothCameraFrameNanos;
        this.lastSmoothCameraFrameNanos = now;
        if (elapsed <= 0L) {
            return 0.0F;
        }
        return Mth.clamp(
                elapsed / 1_000_000_000.0F,
                0.0F,
                MAX_SMOOTH_FRAME_TICKS * SMOOTH_TICK_SECONDS);
    }

    private void applySmoothFrameMovement(Minecraft minecraft, float frameSeconds) {
        float tickDelta = frameSeconds / SMOOTH_TICK_SECONDS;
        if (tickDelta <= CAMERA_INPUT_EPSILON) {
            return;
        }

        CameraInput input = readCameraInput(minecraft);
        float scrollForFrame = 0.0F;
        if (Math.abs(this.smoothScrollRemaining) > SCROLL_SMOOTH_EPSILON) {
            RtsCameraSmoothingMath.DecayStep scrollStep =
                    RtsCameraSmoothingMath.consumeRemaining(
                            this.smoothScrollRemaining,
                            frameSeconds,
                            SCROLL_RESPONSE_SECONDS,
                            SCROLL_SMOOTH_EPSILON);
            scrollForFrame = scrollStep.consumed();
            this.smoothScrollRemaining = scrollStep.remaining();
            this.pendingNetworkScroll += scrollForFrame;
        }

        if (Math.abs(this.smoothForward) <= MOVE_SMOOTH_EPSILON
                && Math.abs(this.smoothStrafe) <= MOVE_SMOOTH_EPSILON
                && Math.abs(this.smoothVertical) <= MOVE_SMOOTH_EPSILON
                && Math.abs(scrollForFrame) <= SCROLL_SMOOTH_EPSILON) {
            return;
        }

        applyLocalPrediction(
                this.smoothForward * getKeyboardMoveSensitivityScale() * tickDelta,
                this.smoothStrafe * getKeyboardMoveSensitivityScale() * tickDelta,
                this.smoothVertical * getKeyboardMoveSensitivityScale() * tickDelta,
                0.0F, 0.0F, 0.0F, 0.0F, scrollForFrame, 0, input.fast);
    }

    private void updateVisualPose(float frameSeconds) {
        if (!this.visualPoseReady) {
            snapVisualPoseToLocal();
            return;
        }
        if (!(frameSeconds > 0.0F)) {
            return;
        }

        float positionAlpha = RtsCameraSmoothingMath.exponentialAlpha(
                frameSeconds, VISUAL_POSITION_RESPONSE_SECONDS);
        float rotationAlpha = RtsCameraSmoothingMath.exponentialAlpha(
                frameSeconds, VISUAL_ROTATION_RESPONSE_SECONDS);
        this.visualX = approachVisualPosition(this.visualX, this.localX, positionAlpha);
        this.visualY = approachVisualPosition(this.visualY, this.localY, positionAlpha);
        this.visualZ = approachVisualPosition(this.visualZ, this.localZ, positionAlpha);
        this.visualYawDeg = approachVisualAngle(this.visualYawDeg, this.localYawDeg, rotationAlpha);
        this.visualPitchDeg = approachVisualAngle(this.visualPitchDeg, this.localPitchDeg, rotationAlpha);
    }

    private void snapVisualPoseToLocal() {
        if (!this.localStateReady) {
            this.visualPoseReady = false;
            return;
        }
        this.visualX = this.localX;
        this.visualY = this.localY;
        this.visualZ = this.localZ;
        this.visualYawDeg = this.localYawDeg;
        this.visualPitchDeg = this.localPitchDeg;
        this.visualPoseReady = true;
    }

    private void snapVisualMirrorCameraPose() {
        if (this.localMirrorCamera != null) {
            if (!this.visualPoseReady) {
                snapVisualPoseToLocal();
            }
            this.localMirrorCamera.snapTo(
                    this.visualX, this.visualY, this.visualZ,
                    this.visualYawDeg, this.visualPitchDeg);
        }
    }

    private static double approachVisualPosition(double current, double target, float alpha) {
        double next = current + ((target - current) * alpha);
        return Math.abs(target - next) <= VISUAL_POSITION_EPSILON ? target : next;
    }

    private static float approachVisualAngle(float current, float target, float alpha) {
        float next = RtsCameraSmoothingMath.interpolateAngleDegrees(current, target, alpha);
        return Math.abs(Mth.wrapDegrees(target - next)) <= VISUAL_ROTATION_EPSILON ? target : next;
    }

    private void ensureLocalMirrorCamera(Minecraft minecraft) {
        if (minecraft.level == null) {
            this.localMirrorCamera = null;
            return;
        }
        if (this.localMirrorCamera != null && this.localMirrorCamera.level() == minecraft.level) {
            return;
        }
        this.localMirrorCamera = new RtsCameraEntity(RtsEntities.RTS_CAMERA_ENTITY.get(), minecraft.level);
        if (!this.visualPoseReady) {
            snapVisualPoseToLocal();
        }
        this.localMirrorCamera.snapTo(
                this.visualX, this.visualY, this.visualZ,
                this.visualYawDeg, this.visualPitchDeg);
    }

    // =========================================================================
    //  Local prediction
    // =========================================================================

    private void applyLocalPrediction(float forward, float strafe, float vertical,
                                       float panX, float panY, float rotateX, float rotateY,
                                       float scroll, int rotateSteps, boolean fast) {
        this.localYawDeg += rotateX * ROTATE_GAIN_X;
        if (rotateSteps != 0) {
            this.localYawDeg = snapQuarter(this.localYawDeg + (90.0F * rotateSteps));
        }
        this.localPitchDeg = Mth.clamp(this.localPitchDeg + (rotateY * ROTATE_GAIN_Y), MIN_CAMERA_PITCH, MAX_CAMERA_PITCH);

        double speed = fast ? 0.80D : 0.45D;
        double yawRad = Math.toRadians(this.localYawDeg);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double targetX = this.localX;
        double targetY = this.localY;
        double targetZ = this.localZ;

        float safeVertical = Mth.clamp(vertical, -4.0F, 4.0F);
        double dx = (-sin * forward + cos * strafe) * speed;
        double dz = (cos * forward + sin * strafe) * speed;

        double dragScale = 0.020D * Math.max(8.0D, this.localHeightOffset);
        double moveRight = panX * dragScale;
        double moveForward = -panY * dragScale;

        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);
        double fwdX = -Math.sin(yawRad);
        double fwdZ = Math.cos(yawRad);

        dx += rightX * moveRight + fwdX * moveForward;
        dz += rightZ * moveRight + fwdZ * moveForward;

        targetX += dx;
        targetY += safeVertical * (fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        targetZ += dz;

        if (scroll != 0.0F) {
            double pitchRad = Math.toRadians(this.localPitchDeg);
            double lookX = -Math.sin(yawRad) * Math.cos(pitchRad);
            double lookY = -Math.sin(pitchRad);
            double lookZ = Math.cos(yawRad) * Math.cos(pitchRad);

            double dolly = scroll * DOLLY_PER_SCROLL;
            targetX += lookX * dolly;
            targetY += lookY * dolly;
            targetZ += lookZ * dolly;
        }

        double halfExtent = maxRadius;
        targetX = Mth.clamp(targetX, anchorX - halfExtent, anchorX + halfExtent);
        targetZ = Mth.clamp(targetZ, anchorZ - halfExtent, anchorZ + halfExtent);

        targetY = Mth.clamp(targetY, anchorY + MIN_CAMERA_HEIGHT_OFFSET, anchorY + MAX_CAMERA_HEIGHT_OFFSET);

        this.localX = targetX;
        this.localY = targetY;
        this.localZ = targetZ;
        this.localHeightOffset = this.localY - anchorY;
    }

    // Note: anchorX, anchorY, anchorZ, maxRadius are stored as service fields
    // and set via a dedicated method.
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private double maxRadius;

    /**
     * Updates the bounding anchor used for camera position clamping.
     */
    public void setBounds(double anchorX, double anchorY, double anchorZ, double maxRadius) {
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.maxRadius = maxRadius;
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private static float snapQuarter(float yaw) {
        int quarter = Math.round(yaw / 90.0F);
        return quarter * 90.0F;
    }

    private CameraInput readCameraInput(Minecraft minecraft) {
        BuilderScreen builderScreen = minecraft.screen instanceof BuilderScreen screen ? screen : null;
        boolean suppressMoveKeys = builderScreen != null && builderScreen.isSearchFocused();
        if (suppressMoveKeys) {
            return CameraInput.NONE;
        }

        long window = minecraft.getWindow().getWindow();
        boolean w = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W);
        boolean s = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S);
        boolean a = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A);
        boolean d = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D);
        boolean up = ClientKeyMappings.CAMERA_UP.isDown()
                || ClientKeyMappings.CAMERA_UP_SECONDARY.isDown()
                || (builderScreen != null && builderScreen.isCameraUpActionHeld());
        boolean down = ClientKeyMappings.CAMERA_DOWN.isDown()
                || (builderScreen != null && builderScreen.isCameraDownActionHeld());
        boolean fast = minecraft.options.keySprint.isDown();

        return new CameraInput(
                (w ? 1.0F : 0.0F) - (s ? 1.0F : 0.0F),
                (a ? 1.0F : 0.0F) - (d ? 1.0F : 0.0F),
                (up ? 1.0F : 0.0F) - (down ? 1.0F : 0.0F),
                fast);
    }

    // =========================================================================
    //  CameraInput record (formerly private inner class of ClientRtsController)
    // =========================================================================

    private static final class CameraInput {
        private static final CameraInput NONE = new CameraInput(0.0F, 0.0F, 0.0F, false);

        final float forward;
        final float strafe;
        final float vertical;
        final boolean fast;

        CameraInput(float forward, float strafe, float vertical, boolean fast) {
            this.forward = forward;
            this.strafe = strafe;
            this.vertical = vertical;
            this.fast = fast;
        }
    }
}
