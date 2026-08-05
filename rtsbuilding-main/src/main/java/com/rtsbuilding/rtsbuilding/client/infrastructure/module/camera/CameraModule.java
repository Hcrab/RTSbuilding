package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.RtsItems;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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

    /** 开启当前 RTS 模式的那把终端的 UUID（服务端下发），用于锁定网格中的拿去/启用操作 */
    private String activeTerminalUuid;

    
    
    

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

    /**
     * 查询 RTS 相机是否已激活（服务端开启后通过状态包同步到本地状态）。
     * <p>与服务端 {@code RtsCameraManager.isActive} 保持一致的客户端映射，
     * 供渲染 pass 与交互发包判断使用。</p>
     */
    public boolean isCameraEnabled() {
        return state.enabled;
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

    /**
     * 判断给定物品栈是否为“开启当前 RTS 模式的那把终端”。
     * <p>RTS 模式下禁止对该终端进行拿去/启用等网格操作。</p>
     */
    public static boolean isLockedTerminal(ItemStack stack) {
        RtsClientKernel kernel = RtsClientKernel.get();
        CameraModule cam = kernel == null ? null : kernel.module(CameraModule.class);
        return cam != null && cam.isLockedTerminalInternal(stack);
    }

    private boolean isLockedTerminalInternal(ItemStack stack) {
        if (activeTerminalUuid == null || stack == null || stack.isEmpty()) {
            return false;
        }
        if (!stack.is(RtsItems.RTS_TERMINAL.get())) {
            return false;
        }
        String uuid = stack.get(RtsItems.TERMINAL_UUID.get());
        return uuid != null && activeTerminalUuid.equals(uuid);
    }

    public void applyServerCameraAnchor(S2CRtsCameraAnchorPayload payload) {
        if (!state.enabled) return;
        state.anchorX = payload.anchorX();
        state.anchorY = payload.anchorY();
        state.anchorZ = payload.anchorZ();
        state.maxRadius = payload.maxRadius();
    }

    // ── 相机姿态上报节流：上次实际发送的值（用于变化检测） ──
    private double lastSentPoseX = Double.NaN, lastSentPoseY = Double.NaN, lastSentPoseZ = Double.NaN;
    private float lastSentPoseYaw, lastSentPosePitch;

    /**
     * 姿态变化阈值：位置位移 ≥ 0.01 格或角度变化 ≥ 0.05° 才上报。
     * 相机静止时完全停发，避免每 tick 空转包（下行 NBT 序列化 + 网络往返）。
     */
    private static final double POSE_MOVE_EPSILON_SQ = 1.0E-4D;   // (0.01)²
    private static final float POSE_ANGLE_EPSILON_DEG = 0.05F;

    private void sendPoseIfChanged() {
        double x = state.localX, y = state.localY, z = state.localZ;
        float yaw = state.localYaw, pitch = state.localPitch;
        double dx = x - lastSentPoseX;
        double dy = y - lastSentPoseY;
        double dz = z - lastSentPoseZ;
        boolean firstSend = Double.isNaN(lastSentPoseX);
        if (!firstSend
                && dx * dx + dy * dy + dz * dz < POSE_MOVE_EPSILON_SQ
                && Math.abs(yaw - lastSentPoseYaw) < POSE_ANGLE_EPSILON_DEG
                && Math.abs(pitch - lastSentPosePitch) < POSE_ANGLE_EPSILON_DEG) {
            return;
        }
        RtsClientPacketGateway.sendCameraPose(x, y, z, yaw, pitch);
        lastSentPoseX = x;
        lastSentPoseY = y;
        lastSentPoseZ = z;
        lastSentPoseYaw = yaw;
        lastSentPosePitch = pitch;
    }

    
    
    

    @Override
    public void tick(long epochMs, int tickIndex) {
        if (!state.enabled || !state.localReady) return;

        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        entitySync.ensureMirrorCamera(mc);

        // 实时上报相机姿态（位置 + 朝向）给服务端。相机移动/旋转是纯客户端计算，
        // 服务端会话需要客户端相机真实位置与朝向（如无人机跟随、动作范围校验）。
        // 每 2 tick 采样一次（10Hz，原版实体同步同频），配合 sendPoseIfChanged 的变化检测：
        // 静止时完全停发，移动时也不会超过 10Hz。
        if ((tickIndex & 1) == 0) {
            sendPoseIfChanged();
        }
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
        // 记录开启该模式的那把终端，RTS 模式下禁止对它拿去/启用
        this.activeTerminalUuid = payload.terminalUuid();

        if (freshEnable) {
            // 重置姿态上报节流缓存，保证新会话首个 tick 强制上报一次
            this.lastSentPoseX = Double.NaN;
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
        this.activeTerminalUuid = null;
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
