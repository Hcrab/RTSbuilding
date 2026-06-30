package com.rtsbuilding.rtsbuilding.client.module.camera;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 相机模块——管理 RTS 摄像机轨道、平移、缩放、平滑与本地镜像实体。
 *
 * <p>各职责委托给子组件：
 * <ul>
 *   <li>{@link FreeCameraMode} — 自由模式操控（键盘/拖拽/滚轮）</li>
 *   <li>{@link PlayerOrbitCameraMode} — 玩家环绕模式操控</li>
 *   <li>{@link CameraPoseComputer} — 方块轨道模式姿态运算</li>
 *   <li>{@link CameraEntitySync} — 镜像实体生命周期</li>
 *   <li>{@link CameraViewManager} — 视角状态捕获/恢复</li>
 * </ul>
 */
public final class CameraModule implements FeatureModule {

    // ======================================================================
    //  Init
    // ======================================================================

    @Override
    public void init(RtsClientKernel kernel) {
        kernel.inputPipeline().registerLayer(new CameraInputLayer(kernel));
    }

    // ======================================================================
    //  Sub-components
    // ======================================================================

    private final CameraState state = new CameraState();
    private final FreeCameraMode freeCamera = new FreeCameraMode();
    private final PlayerOrbitCameraMode playerOrbit = new PlayerOrbitCameraMode();
    private final CameraPoseComputer poseComputer = new CameraPoseComputer();
    private final CameraEntitySync entitySync = new CameraEntitySync();
    private final CameraViewManager viewManager = new CameraViewManager();

    // ======================================================================
    //  Module lifecycle
    // ======================================================================

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

    // ======================================================================
    //  Public API
    // ======================================================================

    /** 禁用相机——恢复视角、清理状态、通知服务端。 */
    public void disableCamera() {
        if (!state.enabled) return;
        shutdownCamera();
        RtsClientPacketGateway.sendToggleCamera(false);
    }

    // ======================================================================
    //  Server state callbacks
    // ======================================================================

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

    // ======================================================================
    //  Tick
    // ======================================================================

    @Override
    public void tick(long epochMs, int tickIndex) {
        if (!state.enabled || !state.localReady) return;

        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        entitySync.savePrevState(state);

        if (state.playerOrbitMode || state.orbitMode) {
            // 轨道/玩家环绕模式输入已在 onRenderFrame 中帧率级处理，tick 只负责实体同步
        } else {
            FreeCameraMode.CameraInput input = freeCamera.readCameraInput();
            freeCamera.processInput(state, input);
            freeCamera.resetAccumulation(state);
        }

        entitySync.sync(mc, state);
    }

    /**
     * 渲染帧前处理——在每帧 {@code Camera.setup()} 之前调用。
     * 轨道模式下在此帧率级处理累积输入并更新实体位置，实现平滑圆周运动。
     *
     * @param partialTick 渲染帧 partialTick，用于玩家位置插值
     */
    public void onRenderFrame(float partialTick) {
        if (!state.enabled || !state.localReady) return;
        if (!state.playerOrbitMode && !state.orbitMode) return;

        Minecraft mc = mc();
        if (mc.player == null || mc.level == null) return;

        if (state.playerOrbitMode) {
            // 帧率级消费累积输入并更新玩家环绕姿态
            playerOrbit.processInput(state, partialTick);
        } else {
            // 帧率级消费累积输入并更新轨道姿态
            poseComputer.processOrbitInput(state);
        }
        // 直接用 snapTo 设置实体位置（xo=x, yRotO=yRot），
        // 使 getEyePosition(partialTick) 返回当前精确的圆周位置
        entitySync.snapToState(state);
    }

    // ======================================================================
    //  Camera input queueing
    // ======================================================================

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
        // 方块环绕模式：右键拖拽平移轨道目标（改变射线瞄向的方块）
        if (state.orbitMode && !state.playerOrbitMode) {
            double yawRad = Math.toRadians(state.localYaw);
            double cos = Math.cos(yawRad);
            double sin = Math.sin(yawRad);
            double scale = 0.005D * Math.max(4.0D, state.orbitRadius) * state.inputSensitivity;
            // 屏幕拖拽 → 世界空间平移（dy=前后, dx=左右，与自由模式一致）
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

    // ======================================================================
    //  Orbit mode API——绕目标方块圆周旋转
    // ======================================================================

    /**
     * 启用轨道旋转模式——相机绕着 {@code mc.hitResult} 瞄准的方块旋转，并始终看向目标。
     * <p>如果当前没有瞄准方块，则使用锚点作为轨道目标（在 GUI 设置面板中点击开关时适用）。</p>
     *
     * @return 是否成功启用
     */
    public boolean enableOrbitMode() {
        Minecraft mc = mc();
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            BlockPos pos = hit.getBlockPos();
            state.orbitTargetX = pos.getX() + 0.5;
            state.orbitTargetY = pos.getY() + 0.5;
            state.orbitTargetZ = pos.getZ() + 0.5;
        } else {
            // 在 GUI 设置面板中 mc.hitResult 可能为空，回退使用锚点
            state.orbitTargetX = state.anchorX;
            state.orbitTargetY = state.anchorY;
            state.orbitTargetZ = state.anchorZ;
        }
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
        return true;
    }

    /**
     * 启用轨道旋转模式——相机绕着指定方块旋转，并始终看向目标。
     *
     * @param pos 轨道环绕的目标方块位置
     * @return 是否成功启用
     */
    public boolean enableOrbitMode(BlockPos pos) {
        if (pos == null) return enableOrbitMode();
        state.orbitTargetX = pos.getX() + 0.5;
        state.orbitTargetY = pos.getY() + 0.5;
        state.orbitTargetZ = pos.getZ() + 0.5;
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
        return true;
    }

    /** 禁用轨道旋转模式，恢复自由视角控制。 */
    public void disableOrbitMode() {
        state.orbitMode = false;
    }

    /**
     * 从持久化恢复方块环绕模式——使用保存的目标坐标，不依赖 {@code mc.hitResult}。
     * 在重新进入 RTS 模式且之前处于方块环绕时调用。
     *
     * @param targetX 保存的目标方块中心 X
     * @param targetY 保存的目标方块中心 Y
     * @param targetZ 保存的目标方块中心 Z
     */
    public void restoreOrbitMode(double targetX, double targetY, double targetZ) {
        state.orbitTargetX = targetX;
        state.orbitTargetY = targetY;
        state.orbitTargetZ = targetZ;
        poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        state.orbitMode = true;
    }

    /** 切换轨道旋转模式开关。 */
    public boolean toggleOrbitMode() {
        if (state.orbitMode) {
            disableOrbitMode();
            return false;
        }
        return enableOrbitMode();
    }

    /** 是否处于轨道旋转模式 */
    public boolean isOrbitMode() {
        return state.orbitMode;
    }

    // ======================================================================
    //  Player orbit mode API——绕玩家实体环绕旋转
    // ======================================================================

    /**
     * 启用玩家环绕模式——相机绕着玩家实体旋转，并始终看向玩家。
     * <p>如果当前处于方块轨道模式，先退出方块轨道模式。</p>
     *
     * @return 是否成功启用
     */
    public boolean enablePlayerOrbitMode() {
        Minecraft mc = mc();
        if (mc.player == null) return false;

        // 保存当前方块环绕模式的完整状态，退出玩家环绕时恢复
        state.savedBlockOrbitMode = state.orbitMode;
        if (state.orbitMode) {
            state.savedOrbitTargetX = state.orbitTargetX;
            state.savedOrbitTargetY = state.orbitTargetY;
            state.savedOrbitTargetZ = state.orbitTargetZ;
            state.savedOrbitAngle = state.orbitAngle;
            state.savedOrbitPitch = state.orbitPitch;
            state.savedOrbitRadius = state.orbitRadius;
        }
        state.orbitMode = false;

        playerOrbit.init(state);
        state.playerOrbitMode = true;
        return true;
    }

    /** 禁用玩家环绕模式，恢复自由视角控制。若之前处于方块环绕模式则自动恢复。 */
    public void disablePlayerOrbitMode() {
        state.playerOrbitMode = false;
        // 恢复之前保存的方块环绕模式
        if (state.savedBlockOrbitMode && !state.orbitMode) {
            state.orbitTargetX = state.savedOrbitTargetX;
            state.orbitTargetY = state.savedOrbitTargetY;
            state.orbitTargetZ = state.savedOrbitTargetZ;
            state.orbitAngle = state.savedOrbitAngle;
            state.orbitPitch = state.savedOrbitPitch;
            state.orbitRadius = state.savedOrbitRadius;
            state.orbitMode = true;
            poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
        }
        state.savedBlockOrbitMode = false;
    }

    /** 切换玩家环绕模式开关。 */
    public boolean togglePlayerOrbitMode() {
        if (state.playerOrbitMode) {
            disablePlayerOrbitMode();
            return false;
        }
        return enablePlayerOrbitMode();
    }

    /** 是否处于玩家环绕模式 */
    public boolean isPlayerOrbitMode() {
        return state.playerOrbitMode;
    }

    // ======================================================================
    //  Getters (for UI)
    // ======================================================================

    public CameraState getState() { return this.state; }

    public float getRotateSensitivity() { return this.state.rotateSensitivity; }

    public float getInputSensitivity() { return state.inputSensitivity; }

    public void setInputSensitivity(float val) {
        state.inputSensitivity = Mth.clamp(val, 0.1F, 2.0F);
    }

    // ======================================================================
    //  Internal — camera lifecycle
    // ======================================================================

    /** 启用 RTS 摄像机，应用服务端下发的姿态。 */
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
        }

        viewManager.applyRtsView(mc);

        state.localHeightOffset = payload.heightOffset();
        state.localYaw = payload.yawDeg();
        state.localPitch = payload.pitchDeg();
        state.localX = payload.anchorX();
        state.localY = payload.anchorY() + payload.heightOffset();
        state.localZ = payload.anchorZ();
        state.localReady = true;

        // 首次启用时默认开启方块环绕模式，以锚点作为轨道目标
        if (freshEnable) {
            state.orbitTargetX = state.anchorX;
            state.orbitTargetY = state.anchorY + state.localHeightOffset;
            state.orbitTargetZ = state.anchorZ;
            poseComputer.initOrbitPose(state, state.localX, state.localY, state.localZ);
            state.orbitMode = true;
        }

        entitySync.savePrevState(state);
        entitySync.sync(mc, state);
    }

    /**
     * 关闭 RTS 摄像机——恢复视角、清理状态。
     * <p>被 {@link #disableCamera()} 和 {@link #applyServerCameraState} 共用，
     * 消除重复的关闭逻辑。</p>
     */
    private void shutdownCamera() {
        state.enabled = false;
        state.localReady = false;
        viewManager.restore(mc());
        clearState();
    }

    private void clearState() {
        state.prevX = state.prevY = state.prevZ = 0.0D;
        state.prevYaw = state.prevPitch = 0.0F;
        state.orbitMode = false;
        state.playerOrbitMode = false;
        state.savedBlockOrbitMode = false;
        viewManager.clear();
        entitySync.clear();
    }
}
