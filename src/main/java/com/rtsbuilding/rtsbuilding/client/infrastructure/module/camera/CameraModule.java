package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

public final class CameraModule implements FeatureModule {

    
    
    

    @Override
    public void init(RtsClientKernel kernel) {
        kernel.inputPipeline().registerLayer(new CameraInputLayer(kernel));
    }

    
    
    

    private final CameraState state = new CameraState();
    private final FreeCameraMode freeCamera = new FreeCameraMode();
    private final PlayerOrbitCameraMode playerOrbit = new PlayerOrbitCameraMode();
    private final CameraPoseComputer poseComputer = new CameraPoseComputer();
    private final CameraEntitySync entitySync = new CameraEntitySync();
    private final CameraViewManager viewManager = new CameraViewManager();
    private final CameraModeController modeController = new CameraModeController(state, poseComputer, playerOrbit);

    
    
    

    @Override
    public String moduleId() {
        return "camera";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) disableCamera();
        } else if (event instanceof StateEvent.AnchorUpdated e) {
            state.setBounds(e.x(), e.y(), e.z(), e.maxRadius());
        } else if (event instanceof StateEvent.PlayerDied) {
            disableCamera();
        }
    }

    
    
    

    public void disableCamera() {
        if (!state.enabled) return;
        shutdownCamera();
        RtsClientPacketGateway.sendToggleCamera(false);
    }

    
    
    

    public boolean enableOrbitMode() { return modeController.enableOrbitMode(); }
    public boolean enableOrbitMode(BlockPos pos) { return modeController.enableOrbitMode(pos); }
    public void disableOrbitMode() { modeController.disableOrbitMode(); }
    public boolean toggleOrbitMode() { return modeController.toggleOrbitMode(); }
    public boolean isOrbitMode() { return modeController.isOrbitMode(); }
    public void restoreOrbitMode(double x, double y, double z) { modeController.restoreOrbitMode(x, y, z); }

    public boolean enablePlayerOrbitMode() { return modeController.enablePlayerOrbitMode(); }
    public void disablePlayerOrbitMode() { modeController.disablePlayerOrbitMode(); }
    public boolean togglePlayerOrbitMode() { return modeController.togglePlayerOrbitMode(); }
    public boolean isPlayerOrbitMode() { return modeController.isPlayerOrbitMode(); }

    
    
    

    public void applyServerCameraState(S2CRtsCameraStatePayload payload) {
        Minecraft mc = mc();
        if (mc.player == null) return;

        if (payload.enabled()) {
            enableCamera(mc, payload);
        } else {
            shutdownCamera();
        }
    }

    public void applyServerCameraAnchor(S2CRtsCameraAnchorPayload payload) {
        if (!state.enabled) return;
        state.anchorX = payload.anchorX();
        state.anchorY = payload.anchorY();
        state.anchorZ = payload.anchorZ();
        state.maxRadius = payload.maxRadius();
    }

    
    
    

    @Override
    public void tick(long epochMs, int tickIndex) {
        if (!state.enabled || !state.localReady) return;

        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        entitySync.ensureMirrorCamera(mc);
    }

    public void onRenderFrame(float partialTick) {
        if (!state.enabled || !state.localReady) return;

        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        if (state.playerOrbitMode) {
            playerOrbit.processInput(state, partialTick);
        } else if (state.orbitMode) {
            poseComputer.processOrbitInput(state);
        } else {
            FreeCameraMode.CameraInput input = freeCamera.readCameraInput();
            freeCamera.processInput(state, input);
            freeCamera.resetAccumulation(state);
        }
        entitySync.snapToState(state);
    }

    
    
    

    public void queuePanDrag(double dx, double dy) {
        float panX = state.invertPanX ? (float) dx : -(float) dx;
        float panY = state.invertPanY ? (float) dy : -(float) dy;
        state.pendingPanX += panX;
        state.pendingPanY += panY;
    }

    public void queueRotateDrag(double dx, double dy) {
        state.pendingRawRotateX += (float) dx;
        state.pendingRawRotateY += (float) dy;
    }

    public void queueDragMove(double dx, double dy) {
        if (state.orbitMode && !state.playerOrbitMode) {
            double yawRad = Math.toRadians(state.localYaw);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double scale = 0.005D * Math.max(4.0D, state.orbitRadius) * state.inputSensitivity;
            state.orbitTargetX += (cos * dx - sin * dy) * scale;
            state.orbitTargetZ += (sin * dx + cos * dy) * scale;
            return;
        }
        state.pendingPanX += (float)(dy);
        state.pendingPanY += (float)(-dx);
    }

    public void queueScroll(double scrollY) {
        state.pendingScroll += (float) scrollY;
    }

    public void queueRotateQuarter(int direction) {
        state.pendingRotateSteps += direction;
    }

    
    
    

    public CameraState getState() { return this.state; }
    public float getRotateSensitivity() { return this.state.rotateSensitivity; }
    public float getInputSensitivity() { return state.inputSensitivity; }

    public void setInputSensitivity(float val) {
        state.inputSensitivity = Mth.clamp(val, 0.1F, 2.0F);
    }

    
    
    

    private void enableCamera(Minecraft mc, S2CRtsCameraStatePayload payload) {
        boolean freshEnable = !state.enabled;
        state.enabled = true;
        state.anchorX = payload.anchorX();
        state.anchorY = payload.anchorY();
        state.anchorZ = payload.anchorZ();
        state.maxRadius = payload.maxRadius();

        if (freshEnable) {
            viewManager.capture(mc);
            if (mc.player instanceof LocalPlayer lp) {
                lp.input.forwardImpulse = 0.0F;
                lp.input.leftImpulse = 0.0F;
                lp.input.jumping = false;
                lp.input.shiftKeyDown = false;
            }
            freeCamera.resetEma();
        }

        viewManager.applyRtsView(mc);

        state.localHeightOffset = payload.heightOffset();
        state.localYaw = payload.yawDeg();
        state.localPitch = payload.pitchDeg();
        state.localX = payload.anchorX();
        state.localY = payload.anchorY() + payload.heightOffset();
        state.localZ = payload.anchorZ();
        state.localReady = true;

        if (freshEnable) {
            state.orbitTargetX = state.anchorX;
            state.orbitTargetY = state.anchorY + state.localHeightOffset;
            state.orbitTargetZ = state.anchorZ;
            poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        }

        entitySync.ensureMirrorCamera(mc);
        entitySync.setAsCameraEntity(mc);
        entitySync.snapToState(state);
    }

    private void shutdownCamera() {
        state.enabled = false;
        state.localReady = false;
        viewManager.restore(mc());
        clearState();
    }

    private void clearState() {
        state.prevX = state.prevY = state.prevZ = 0.0D;
        state.prevYaw = state.prevPitch = 0.0F;
        modeController.clearModeState();
        viewManager.clear();
        entitySync.clear();
    }
}
