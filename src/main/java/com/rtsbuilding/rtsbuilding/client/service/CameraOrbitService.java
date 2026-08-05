package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.RtsEntities;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import org.lwjgl.input.Mouse;

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
    private static final float SMOOTH_TICK_SECONDS = 0.05F;
    private static final float MOVE_ACCELERATION_SECONDS = 0.055F;
    private static final float MOVE_DECELERATION_SECONDS = 0.050F;
    private static final float MOVE_SMOOTH_EPSILON = 0.002F;
    private static final float SCROLL_RESPONSE_SECONDS = 0.045F;
    private static final float SCROLL_SMOOTH_EPSILON = 0.0005F;
    private static final float MAX_SMOOTH_SCROLL_REMAINING = 16.0F;
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

    private final CameraSensitivitySettings settings = new CameraSensitivitySettings();

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
    private final CameraVisualPoseState visualPose = new CameraVisualPoseState();

    // =========================================================================
    //  Fields — mirror camera & previous view restoration
    // =========================================================================

    private RtsCameraEntity localMirrorCamera;
    private final CameraViewRestoration viewRestoration = new CameraViewRestoration();
    private int serverCameraEntityId = -1;

    // =========================================================================
    //  Lifecycle — enable / disable
    // =========================================================================

    /** Called by the controller when receiving an enabled camera state. */
    public void capturePreviousView(Minecraft minecraft) {
        this.viewRestoration.capture(minecraft);
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
        this.viewRestoration.clear();
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
        this.visualPose.clear();
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
        this.visualPose.clear();
        this.viewRestoration.clear();
        this.localMirrorCamera = null;
    }

    /** Restores the player's previous camera entity and view settings. */
    public void restorePreviousView(Minecraft minecraft, Entity fallbackEntity) {
        this.viewRestoration.restore(minecraft, fallbackEntity);
    }

    /** Sets the RTS camera view (FPP, no bobbing, no FOV effect). */
    public void applyRtsView(Minecraft minecraft) {
        this.viewRestoration.applyRts(minecraft);
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

    public int getServerCameraEntityId() { return this.serverCameraEntityId; }

    public void resetServerCameraEntityId() {
        this.serverCameraEntityId = -1;
    }

    // =========================================================================
    //  Local state ready
    // =========================================================================

    public boolean isLocalStateReady() { return this.localStateReady; }

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
        Minecraft minecraft = Minecraft.getMinecraft();
        this.rotateCaptured = true;
        this.restoreCursorX = cursorX;
        this.restoreCursorY = cursorY;
        minecraft.mouseHelper.grabMouseCursor();
    }

    public void endRotateCapture(double fallbackX, double fallbackY) {
        if (!this.rotateCaptured) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        this.rotateCaptured = false;
        minecraft.mouseHelper.ungrabMouseCursor();
        double x = this.restoreCursorX == 0.0D ? fallbackX : this.restoreCursorX;
        double y = this.restoreCursorY == 0.0D ? fallbackY : this.restoreCursorY;
        Mouse.setCursorPosition((int) Math.round(x), (int) Math.round(y));
    }

    public boolean isRotateCaptured() {
        return this.rotateCaptured;
    }

    // =========================================================================
    //  Sensitivity
    // =========================================================================

    public float getRotateSensitivity() {
        return this.settings.rotate();
    }

    public void increaseRotateSensitivity() {
        this.settings.increaseRotate();
    }

    public void decreaseRotateSensitivity() {
        this.settings.decreaseRotate();
    }

    public String getInputSensitivityLabel() {
        return this.settings.inputLabel();
    }

    public int getInputSensitivityIndex() {
        return this.settings.inputIndex();
    }

    public int getInputSensitivityPresetCount() {
        return this.settings.presetCount();
    }

    public void setInputSensitivityByFraction(double fraction) {
        this.settings.setInputFraction(fraction);
    }

    public void cycleInputSensitivity() {
        this.settings.cycleInput();
    }

    private float getInputSensitivityScale() {
        return this.settings.inputScale();
    }

    public String getPanDragSensitivityLabel() {
        return this.settings.panLabel();
    }

    public int getPanDragSensitivityIndex() {
        return this.settings.panIndex();
    }

    public void setPanDragSensitivityByFraction(double fraction) {
        this.settings.setPanFraction(fraction);
    }

    public String getRotateViewSensitivityLabel() {
        return this.settings.viewLabel();
    }

    public int getRotateViewSensitivityIndex() {
        return this.settings.viewIndex();
    }

    public void setRotateViewSensitivityByFraction(double fraction) {
        this.settings.setViewFraction(fraction);
    }

    public String getKeyboardMoveSensitivityLabel() {
        return this.settings.keyboardLabel();
    }

    public int getKeyboardMoveSensitivityIndex() {
        return this.settings.keyboardIndex();
    }

    public void setKeyboardMoveSensitivityByFraction(double fraction) {
        this.settings.setKeyboardFraction(fraction);
    }

    public String getWheelZoomSensitivityLabel() {
        return this.settings.wheelLabel();
    }

    public int getWheelZoomSensitivityIndex() {
        return this.settings.wheelIndex();
    }

    public void setWheelZoomSensitivityByFraction(double fraction) {
        this.settings.setWheelFraction(fraction);
    }

    private float getPanDragSensitivityScale() {
        return this.settings.panScale();
    }

    private float getRotateViewSensitivityScale() {
        return this.settings.viewScale();
    }

    private float getKeyboardMoveSensitivityScale() {
        return this.settings.keyboardScale();
    }

    private float getWheelZoomSensitivityScale() {
        return this.settings.wheelScale();
    }

    // =========================================================================
    //  Smooth camera
    // =========================================================================

    public boolean isSmoothCamera() {
        return this.settings.smooth();
    }

    public void setSmoothCamera(boolean smoothCamera) {
        if (this.settings.smooth() != smoothCamera) {
            this.lastSmoothCameraFrameNanos = 0L;
            this.smoothForward = 0.0F;
            this.smoothStrafe = 0.0F;
            this.smoothVertical = 0.0F;
            if (smoothCamera) {
                this.smoothScrollRemaining = MathHelper.clamp(
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
        this.settings.setSmooth(smoothCamera);
    }

    public void toggleSmoothCamera() {
        setSmoothCamera(!this.settings.smooth());
    }

    // =========================================================================
    //  Invert pan drag
    // =========================================================================

    public boolean isInvertPanDragX() {
        return this.settings.invertPanX();
    }

    public void setInvertPanDragX(boolean invert) {
        this.settings.setInvertPanX(invert);
    }

    public void toggleInvertPanDragX() {
        this.settings.setInvertPanX(!this.settings.invertPanX());
    }

    public boolean isInvertPanDragY() {
        return this.settings.invertPanY();
    }

    public void setInvertPanDragY(boolean invert) {
        this.settings.setInvertPanY(invert);
    }

    public void toggleInvertPanDragY() {
        this.settings.setInvertPanY(!this.settings.invertPanY());
    }

    // =========================================================================
    //  Local camera pose getters
    // =========================================================================

    public double getLocalX() { return this.localX; }

    public double getLocalY() { return this.localY; }

    public double getLocalZ() { return this.localZ; }

    public double getLocalHeightOffset() { return this.localHeightOffset; }

    public float getLocalYawDeg() { return this.localYawDeg; }

    public float getLocalPitchDeg() { return this.localPitchDeg; }

    public RtsCameraEntity getLocalMirrorCamera() { return this.localMirrorCamera; }

    // =========================================================================
    //  Input queueing
    // =========================================================================

    public void queuePanDrag(double dragX, double dragY) {
        float signedDragX = (float) dragX;
        float signedDragY = (float) dragY;
        float scale = getPanDragSensitivityScale();
        float panX = (this.settings.invertPanX() ? signedDragX : -signedDragX) * scale;
        float panY = (this.settings.invertPanY() ? signedDragY : -signedDragY) * scale;
        this.pendingPanX += panX;
        this.pendingPanY += panY;
        if (this.settings.smooth()) {
            applyImmediateCameraInput(0.0F, 0.0F, 0.0F, panX, panY, 0.0F, 0.0F, 0.0F, 0, false);
        }
    }

    public void queueRotateDrag(double dragX, double dragY) {
        // 鼠标旋转不再经过速度 EMA：每个输入事件立刻更新目标朝向，松手即停。
        applyImmediateRotation((float) dragX, (float) dragY);
    }

    public void queueScroll(double scrollY) {
        float scroll = (float) scrollY * getWheelZoomSensitivityScale();
        if (this.settings.smooth()) {
            this.smoothScrollRemaining = MathHelper.clamp(
                    this.smoothScrollRemaining + scroll,
                    -MAX_SMOOTH_SCROLL_REMAINING,
                    MAX_SMOOTH_SCROLL_REMAINING);
        } else {
            this.pendingScroll += scroll;
        }
    }

    public void queueRotateQuarter(int direction) {
        this.pendingRotateSteps += direction;
        if (this.settings.smooth()) {
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
        float sens = getRotateViewSensitivityScale() * this.settings.rotate();
        float requestedYaw = MathHelper.clamp(dragX * sens, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float requestedPitch = MathHelper.clamp(dragY * sens, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float nextYawTotal = MathHelper.clamp(
                this.pendingSmoothRotateX + requestedYaw,
                -MAX_SMOOTH_ROTATE_ACCUMULATION, MAX_SMOOTH_ROTATE_ACCUMULATION);
        float nextPitchTotal = MathHelper.clamp(
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

        CameraInputSampler.Input cameraInput = CameraInputSampler.read(minecraft);
        float keyboardScale = getKeyboardMoveSensitivityScale();
        if (this.settings.smooth()) {
            this.smoothForward = RtsCameraSmoothingMath.approachAxis(
                    this.smoothForward, cameraInput.forward(), SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
            this.smoothStrafe = RtsCameraSmoothingMath.approachAxis(
                    this.smoothStrafe, cameraInput.strafe(), SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
            this.smoothVertical = RtsCameraSmoothingMath.approachAxis(
                    this.smoothVertical, cameraInput.vertical(), SMOOTH_TICK_SECONDS,
                    MOVE_ACCELERATION_SECONDS, MOVE_DECELERATION_SECONDS, MOVE_SMOOTH_EPSILON);
        } else {
            this.smoothForward = cameraInput.forward();
            this.smoothStrafe = cameraInput.strafe();
            this.smoothVertical = cameraInput.vertical();
        }
        float forward = this.smoothForward * keyboardScale;
        float strafe = this.smoothStrafe * keyboardScale;
        float vertical = this.smoothVertical * keyboardScale;
        boolean fast = cameraInput.fast();

        // 本地目标已在鼠标事件到达时更新；tick 只把同一批有界输入同步给服务端。
        float rotateXForTick = this.pendingSmoothRotateX;
        float rotateYForTick = this.pendingSmoothRotateY;
        float localScrollForTick = this.settings.smooth() ? 0.0F : this.pendingScroll;
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
        if (hasCameraInput && !this.settings.smooth()) {
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
        if (minecraft.theWorld == null) {
            return;
        }

        this.ensureLocalMirrorCamera(minecraft);
        if (this.localMirrorCamera == null) {
            return;
        }

        float frameSeconds = smoothFrameDeltaSeconds();
        if (this.settings.smooth()) {
            applySmoothFrameMovement(minecraft, frameSeconds);
            updateVisualPose(frameSeconds);
        } else {
            this.lastSmoothCameraFrameNanos = 0L;
            snapVisualPoseToLocal();
        }

        snapVisualMirrorCameraPose();

        if (minecraft.renderViewEntity != this.localMirrorCamera) {
            if (this.cameraRestoreCooldownTicks <= 0) {
                com.rtsbuilding.rtsbuilding.platform.client.MinecraftCompat.setRenderViewEntity(minecraft, this.localMirrorCamera);
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
        return MathHelper.clamp(
                elapsed / 1_000_000_000.0F,
                0.0F,
                MAX_SMOOTH_FRAME_TICKS * SMOOTH_TICK_SECONDS);
    }

    private void applySmoothFrameMovement(Minecraft minecraft, float frameSeconds) {
        float tickDelta = frameSeconds / SMOOTH_TICK_SECONDS;
        if (tickDelta <= CAMERA_INPUT_EPSILON) {
            return;
        }

        CameraInputSampler.Input input = CameraInputSampler.read(minecraft);
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
                0.0F, 0.0F, 0.0F, 0.0F, scrollForFrame, 0, input.fast());
    }

    private void updateVisualPose(float frameSeconds) {
        this.visualPose.update(localX, localY, localZ, localYawDeg, localPitchDeg, localStateReady, frameSeconds);
    }

    private void snapVisualPoseToLocal() {
        this.visualPose.snap(localX, localY, localZ, localYawDeg, localPitchDeg, localStateReady);
    }

    private void snapVisualMirrorCameraPose() {
        if (this.localMirrorCamera != null) {
            if (!this.visualPose.ready()) {
                snapVisualPoseToLocal();
            }
            this.localMirrorCamera.snapTo(
                    this.visualPose.x(), this.visualPose.y(), this.visualPose.z(),
                    this.visualPose.yaw(), this.visualPose.pitch());
        }
    }

    private void ensureLocalMirrorCamera(Minecraft minecraft) {
        if (minecraft.theWorld == null) {
            this.localMirrorCamera = null;
            return;
        }
        if (this.localMirrorCamera != null && this.localMirrorCamera.worldObj == minecraft.theWorld) {
            return;
        }
        this.localMirrorCamera = new RtsCameraEntity(RtsEntities.RTS_CAMERA_ENTITY.get(), minecraft.theWorld);
        if (!this.visualPose.ready()) {
            snapVisualPoseToLocal();
        }
        this.localMirrorCamera.snapTo(
                this.visualPose.x(), this.visualPose.y(), this.visualPose.z(),
                this.visualPose.yaw(), this.visualPose.pitch());
    }

    // =========================================================================
    //  Local prediction
    // =========================================================================

    private void applyLocalPrediction(float forward, float strafe, float vertical,
                                       float panX, float panY, float rotateX, float rotateY,
                                       float scroll, int rotateSteps, boolean fast) {
        CameraMotionSolver.Pose pose = CameraMotionSolver.solve(
                new CameraMotionSolver.Pose(localX, localY, localZ, localHeightOffset, localYawDeg, localPitchDeg),
                new CameraMotionSolver.Bounds(anchorX, anchorY, anchorZ, maxRadius),
                new CameraMotionSolver.Input(forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll, rotateSteps, fast));
        this.localX = pose.x();
        this.localY = pose.y();
        this.localZ = pose.z();
        this.localHeightOffset = pose.heightOffset();
        this.localYawDeg = pose.yawDeg();
        this.localPitchDeg = pose.pitchDeg();
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

}
