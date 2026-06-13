package com.rtsbuilding.rtsbuilding.client.service;

import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;
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
    private static final float ROT_EMA_ALPHA = 0.28F;
    private static final float ROT_EMA_DECAY = 0.78F;
    private static final double MIN_CAMERA_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_CAMERA_HEIGHT_OFFSET = 110.0D;
    private static final float MIN_CAMERA_PITCH = -90.0F;
    private static final float MAX_CAMERA_PITCH = 90.0F;
    private static final float CAMERA_INPUT_EPSILON = 1.0e-4F;
    private static final int CAMERA_IDLE_HEARTBEAT_TICKS = 20;
    private static final int CAMERA_RESTORE_COOLDOWN_TICKS = 10;
    private static final long NANOS_PER_TICK = 50_000_000L;
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
    private int pendingRotateSteps;
    private float pendingRawRotateX;
    private float pendingRawRotateY;

    // =========================================================================
    //  Fields — EMA smoothing
    // =========================================================================

    private float emaRotateX;
    private float emaRotateY;
    private int cameraMoveHeartbeatTicks;
    private int cameraRestoreCooldownTicks;
    private long lastSmoothCameraFrameNanos;

    // =========================================================================
    //  Fields — sensitivity & smoothing preferences
    // =========================================================================

    private float rotateSensitivity = 5.00F;
    private int inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
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
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
    }

    /** Called by the controller when disabling the camera (normal disable). */
    public void clearState() {
        this.previousCameraEntity = null;
        this.localMirrorCamera = null;
        this.localStateReady = false;
        this.lastSmoothCameraFrameNanos = 0L;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        this.pendingPanX = 0.0F;
        this.pendingPanY = 0.0F;
        this.pendingScroll = 0.0F;
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
    }

    /** Called by the controller when disabling on death. */
    public void clearStateOnDeath() {
        this.localStateReady = false;
        this.cameraMoveHeartbeatTicks = 0;
        this.cameraRestoreCooldownTicks = 0;
        this.lastSmoothCameraFrameNanos = 0L;
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
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
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
        return String.format(java.util.Locale.ROOT, "x%.2f", getInputSensitivityScale());
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
        double clamped = Mth.clamp(fraction, 0.0D, 1.0D);
        int next = (int) Math.round(clamped * (INPUT_SENS_PRESETS.length - 1));
        this.inputSensitivityIndex = Mth.clamp(next, 0, INPUT_SENS_PRESETS.length - 1);
    }

    public void cycleInputSensitivity() {
        this.inputSensitivityIndex = (this.inputSensitivityIndex + 1) % INPUT_SENS_PRESETS.length;
    }

    private float getInputSensitivityScale() {
        if (this.inputSensitivityIndex < 0 || this.inputSensitivityIndex >= INPUT_SENS_PRESETS.length) {
            this.inputSensitivityIndex = INPUT_SENS_DEFAULT_INDEX;
        }
        return INPUT_SENS_PRESETS[this.inputSensitivityIndex];
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
        float panX = this.invertPanDragX ? signedDragX : -signedDragX;
        float panY = this.invertPanDragY ? signedDragY : -signedDragY;
        this.pendingPanX += panX;
        this.pendingPanY += panY;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, panX, panY, 0.0F, 0.0F, 0.0F, 0, false);
        }
    }

    public void queueRotateDrag(double dragX, double dragY) {
        this.pendingRawRotateX += (float) dragX;
        this.pendingRawRotateY += (float) dragY;
        if (this.smoothCamera) {
            applyImmediateRotation((float) dragX, (float) dragY);
        }
    }

    public void queueScroll(double scrollY) {
        this.pendingScroll += (float) scrollY;
        if (this.smoothCamera) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F,
                    (float) scrollY * getInputSensitivityScale(), 0, false);
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
        float sens = getInputSensitivityScale() * this.rotateSensitivity;
        float yawDelta = Mth.clamp(dragX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP) * sens;
        float pitchDelta = Mth.clamp(dragY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP) * sens;
        applyImmediateCameraInput(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, yawDelta, pitchDelta, 0.0F, 0, false);
    }

    private void applyImmediateCameraInput(float forward, float strafe, float vertical,
                                            float panX, float panY, float rotateX, float rotateY,
                                            float scroll, int rotateSteps, boolean fast) {
        if (!this.localStateReady) {
            return;
        }
        applyLocalPrediction(forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll, rotateSteps, fast);
        snapLocalMirrorCameraPose();
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
        CameraInput cameraInput = readCameraInput(minecraft);
        float forward = cameraInput.forward;
        float strafe = cameraInput.strafe;
        float vertical = cameraInput.vertical;
        boolean fast = cameraInput.fast;

        float safeRawX = Mth.clamp(this.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float safeRawY = Mth.clamp(this.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        this.emaRotateX += (safeRawX - this.emaRotateX) * ROT_EMA_ALPHA;
        this.emaRotateY += (safeRawY - this.emaRotateY) * ROT_EMA_ALPHA;

        if (Math.abs(safeRawX) < 0.0001F) {
            this.emaRotateX *= ROT_EMA_DECAY;
        }
        if (Math.abs(safeRawY) < 0.0001F) {
            this.emaRotateY *= ROT_EMA_DECAY;
        }

        float inputSensScale = getInputSensitivityScale();
        float rotateXForTick = Mth.clamp(this.emaRotateX * this.rotateSensitivity * inputSensScale, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rotateYForTick = Mth.clamp(this.emaRotateY * this.rotateSensitivity * inputSensScale, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float scrollForTick = this.pendingScroll * inputSensScale;
        if (Math.abs(rotateXForTick) < CAMERA_INPUT_EPSILON) {
            rotateXForTick = 0.0F;
            this.emaRotateX = 0.0F;
        }
        if (Math.abs(rotateYForTick) < CAMERA_INPUT_EPSILON) {
            rotateYForTick = 0.0F;
            this.emaRotateY = 0.0F;
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
                    rotateXForTick, rotateYForTick,
                    scrollForTick, this.pendingRotateSteps, fast);
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
        this.pendingRotateSteps = 0;
        this.pendingRawRotateX = 0.0F;
        this.pendingRawRotateY = 0.0F;
    }

    // =========================================================================
    //  Mirror camera & sync visual frame
    // =========================================================================

    /**
     * Ensures the local mirror camera exists and syncs the visual camera frame.
     * Called from {@code tick()} and {@code applyServerCameraState}.
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

        if (this.smoothCamera) {
            applySmoothFrameMovement(minecraft);
        } else {
            this.lastSmoothCameraFrameNanos = 0L;
        }

        snapLocalMirrorCameraPose();

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

    private void applySmoothFrameMovement(Minecraft minecraft) {
        long now = System.nanoTime();
        if (this.lastSmoothCameraFrameNanos == 0L) {
            this.lastSmoothCameraFrameNanos = now;
            return;
        }

        long elapsed = now - this.lastSmoothCameraFrameNanos;
        this.lastSmoothCameraFrameNanos = now;
        if (elapsed <= 0L) {
            return;
        }

        float tickDelta = Mth.clamp(elapsed / (float) NANOS_PER_TICK, 0.0F, MAX_SMOOTH_FRAME_TICKS);
        if (tickDelta <= CAMERA_INPUT_EPSILON) {
            return;
        }

        CameraInput input = readCameraInput(minecraft);
        if (!input.hasMovement()) {
            return;
        }

        applyLocalPrediction(
                input.forward * tickDelta,
                input.strafe * tickDelta,
                input.vertical * tickDelta,
                0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0, input.fast);
    }

    private void snapLocalMirrorCameraPose() {
        if (this.localMirrorCamera != null) {
            this.localMirrorCamera.snapTo(this.localX, this.localY, this.localZ, this.localYawDeg, this.localPitchDeg);
        }
    }

    private void ensureLocalMirrorCamera(Minecraft minecraft) {
        if (minecraft.level == null) {
            this.localMirrorCamera = null;
            return;
        }
        if (this.localMirrorCamera != null && this.localMirrorCamera.level() == minecraft.level) {
            return;
        }
        this.localMirrorCamera = new RtsCameraEntity(RtsbuildingMod.RTS_CAMERA_ENTITY.get(), minecraft.level);
        this.localMirrorCamera.snapTo(this.localX, this.localY, this.localZ, this.localYawDeg, this.localPitchDeg);
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

        float safeVertical = Mth.clamp(vertical, -1.0F, 1.0F);
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

        boolean hasMovement() {
            return this.forward != 0.0F || this.strafe != 0.0F || this.vertical != 0.0F;
        }
    }
}
