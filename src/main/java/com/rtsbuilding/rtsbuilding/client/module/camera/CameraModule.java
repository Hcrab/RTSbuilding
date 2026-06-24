package com.rtsbuilding.rtsbuilding.client.module.camera;

import com.rtsbuilding.rtsbuilding.client.input.layer.CameraInputLayer;
import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.ModuleState;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraAnchorPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;


/**
 * 相机模块——管理 RTS 摄像机轨道、平移、缩放、平滑与本地镜像实体。
 *
 * <p>激活梯度：RTS 模式开启时 {@link ModuleState#HOT}，关闭时 {@link ModuleState#IDLE}。</p>
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
    //  Constants
    // ======================================================================

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final float ROT_SENS_MIN = 1.00F;
    private static final float ROT_SENS_MAX = 10.00F;
    private static final float ROT_SENS_STEP = 0.50F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final double DOLLY_DAMP_MAX_DIST = 30.0D;
    private static final double DOLLY_DAMP_MIN_FACTOR = 0.05D;
    private static final double DOLLY_DAMP_RAY_RANGE = 128.0D;
    private static final float[] INPUT_SENS_PRESETS = {0.50F, 0.75F, 1.00F, 1.25F, 1.50F, 2.00F};
    private static final int INPUT_SENS_DEFAULT_INDEX = 2;
    private static final double MIN_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_HEIGHT_OFFSET = 110.0D;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;
    private static final int RESTORE_COOLDOWN_TICKS = 10;

    // ======================================================================
    //  State
    // ======================================================================

    private final CameraState state = new CameraState();
    private int cameraRestoreCooldown;
    private EntityType<RtsCameraEntity> cachedCameraEntityType;

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
            if (!e.enabled()) {
                disableCamera();
            }
        } else if (event instanceof StateEvent.AnchorUpdated e) {
            state.setBounds(e.x(), e.y(), e.z(), e.maxRadius());
        } else if (event instanceof StateEvent.PlayerDied) {
            // 玩家死亡 → 立即禁用相机、恢复正常视角
            disableCamera();
        }
    }

    private void disableCamera() {
        if (!state.enabled) return;
        restorePreviousView();
        clearState();
        Minecraft mc = mc();
        if (mc.screen instanceof BuilderScreen) {
            mc.setScreen(null);
        }
        // 通知服务器关闭 RTS 模式（匹配旧代码死亡处理）
        RtsClientPacketGateway.sendToggleCamera(false);
    }

    // ======================================================================
    //  Server state callbacks (called from network handler)
    // ======================================================================

    /**
     * 应用服务端下发的完整相机状态（RTS 模式开启）。
     */
    public void applyServerCameraState(S2CRtsCameraStatePayload payload) {
        Minecraft mc = mc();
        if (mc.player == null) return;

        if (payload.enabled()) {
            boolean freshEnable = !state.enabled;
            state.enabled = true;
            state.anchorX = payload.anchorX();
            state.anchorY = payload.anchorY();
            state.anchorZ = payload.anchorZ();
            state.maxRadius = payload.maxRadius();

            if (freshEnable) {
                capturePreviousView();
                // 清除残留输入
                if (mc.player instanceof LocalPlayer lp) {
                    lp.input.forwardImpulse = 0.0F;
                    lp.input.leftImpulse = 0.0F;
                    lp.input.jumping = false;
                    lp.input.shiftKeyDown = false;
                }
            }

            // 每次服务端下发状态时都重新应用 RTS 视角设置
            applyRtsView();

            // 应用服务端相机姿态
            state.localHeightOffset = payload.heightOffset();
            state.localYaw = payload.yawDeg();
            state.localPitch = payload.pitchDeg();
            state.localX = payload.anchorX();
            state.localY = payload.anchorY() + payload.heightOffset();
            state.localZ = payload.anchorZ();
            state.localReady = true;

            // 打开 BuilderScreen（如果未打开）
            if (!(mc.screen instanceof BuilderScreen)) {
                mc.setScreen(new BuilderScreen());
            }

            // 首次同步前先保存当前姿态作为插值基准，防止 prevX/Y/Z 为 0 导致从原点跳变闪屏
            savePrevState();

            syncCameraEntity(mc);
        } else {
            // RTS 模式关闭
            state.enabled = false;
            state.localReady = false;
            restorePreviousView();
            clearState();
            if (mc.screen instanceof BuilderScreen) {
                mc.setScreen(null);
            }
        }
    }

    /**
     * 应用服务端锚点更新。
     */
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

        // 保底：RTS 模式活跃时如果 BuilderScreen 被意外关闭，立即重开
        // 防止原版 HUD（血条、经验条、准星等）透过 RTS 视图显示
        if (!(mc.screen instanceof BuilderScreen)) {
            mc.setScreen(new BuilderScreen());
        }

        CameraInput input = readCameraInput(mc);

        // 在处理输入前保存当前姿态作为上一 tick 插值基准
        savePrevState();

        processPendingInput(input);

        resetAccumulation();

        // 每 tick 同步实体位置
        syncCameraEntity(mc);
    }

    // ======================================================================
    //  Camera input queueing (called from input layer)
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

    /**
     * 右键拖拽移动摄像机：垂直拖→前后移动，水平拖→左右移动。
     * 符号约定：dragY 负=上=后退，dragY 正=下=前进；dragX 正=右，dragX 负=左。
     */
    public void queueDragMove(double dx, double dy) {
        // pendingPanX → dz（前后），pendingPanY → -pendingPanY → dx（左右）
        // 所以：dy（垂直）→ pendingPanX，dx（水平）→ -pendingPanY
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
    //  Getters (for UI)
    // ======================================================================

    public CameraState getState() {
        return this.state;
    }

    public float getRotateSensitivity() {
        return this.state.rotateSensitivity;
    }

    public int getInputSensitivityIndex() {
        return Mth.clamp(this.state.inputSensitivityIndex, 0, INPUT_SENS_PRESETS.length - 1);
    }

    public int getInputSensitivityPresetCount() {
        return INPUT_SENS_PRESETS.length;
    }

    public void setInputSensitivityByFraction(double fraction) {
        int next = (int) Math.round(Mth.clamp(fraction, 0.0D, 1.0D) * (INPUT_SENS_PRESETS.length - 1));
        this.state.inputSensitivityIndex = Mth.clamp(next, 0, INPUT_SENS_PRESETS.length - 1);
    }

    public void cycleInputSensitivity() {
        this.state.inputSensitivityIndex = (this.state.inputSensitivityIndex + 1) % INPUT_SENS_PRESETS.length;
    }

    public void increaseSensitivity() {
        this.state.rotateSensitivity = Mth.clamp(this.state.rotateSensitivity + ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public void decreaseSensitivity() {
        this.state.rotateSensitivity = Mth.clamp(this.state.rotateSensitivity - ROT_SENS_STEP, ROT_SENS_MIN, ROT_SENS_MAX);
    }

    public float getInputSensitivity() {
        return state.inputSensitivity;
    }

    public void setInputSensitivity(float val) {
        state.inputSensitivity = Mth.clamp(val, 0.1F, 2.0F);
    }

    public String getInputSensitivityLabel() {
        return String.format(java.util.Locale.ROOT, "x%.2f", state.inputSensitivity);
    }

    // ======================================================================
    //  Internal
    // ======================================================================

    private void capturePreviousView() {
        Minecraft mc = mc();
        state.prevCameraEntity = mc.getCameraEntity();
        state.prevCameraType = mc.options.getCameraType();
        state.prevBobView = mc.options.bobView().get();
        state.prevFovScale = mc.options.fovEffectScale().get();
    }

    private void applyRtsView() {
        Minecraft mc = mc();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.bobView().set(false);
        mc.options.fovEffectScale().set(0.0D);
    }

    private void restorePreviousView() {
        Minecraft mc = mc();
        Entity restore = state.prevCameraEntity != null ? state.prevCameraEntity : mc.player;
        mc.setCameraEntity(restore);
        mc.options.setCameraType(state.prevCameraType);
        mc.options.bobView().set(state.prevBobView);
        mc.options.fovEffectScale().set(state.prevFovScale);
    }

    private void clearState() {
        state.enabled = false;
        state.localReady = false;
        state.prevCameraEntity = null;
        state.localMirrorCamera = null;
        state.prevX = state.prevY = state.prevZ = 0.0D;
        state.prevYaw = state.prevPitch = 0.0F;
        cameraRestoreCooldown = 0;
    }

    private void resetAccumulation() {
        state.pendingPanX = 0.0F;
        state.pendingPanY = 0.0F;
        state.pendingScroll = 0.0F;
        state.pendingRotateSteps = 0;
        state.pendingRawRotateX = 0.0F;
        state.pendingRawRotateY = 0.0F;
    }

    private void processPendingInput(CameraInput input) {
        // 首次处理输入
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        // 旋转输入无二次钳位——让灵敏度自然控制手感，拖多少转多少
        float sensScale = state.inputSensitivity;
        float rotateXForTick = rawX * state.rotateSensitivity * sensScale;
        float rotateYForTick = rawY * state.rotateSensitivity * sensScale;

        // 中键拖拽旋转（即时响应，无平滑）
        state.localYaw += rotateXForTick * ROTATE_GAIN_X;
        if (state.pendingRotateSteps != 0) {
            state.localYaw = snapQuarter(state.localYaw + 90.0F * state.pendingRotateSteps);
        }
        state.localPitch = Mth.clamp(state.localPitch + rotateYForTick * ROTATE_GAIN_Y, MIN_PITCH, MAX_PITCH);

        // 灵敏度归一化因子（以默认值 5.0 为基准，保持默认手感不变）
        double sensNorm = state.rotateSensitivity / 5.0D;
        double speed = (input.fast ? 0.80D : 0.45D) * sensNorm;
        double yawRad = Math.toRadians(state.localYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        // ==================================================================
        //  键盘/WASD/拖拽/滚轮全部即时响应，无惯性无平滑
        // ==================================================================

        // 键盘输入位移
        double kbDx = (-sin * input.forward + cos * input.strafe) * speed;
        double kbDz = (cos * input.forward + sin * input.strafe) * speed;
        double kbDy = input.vertical * (input.fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED) * sensNorm;

        double half = state.maxRadius;
        state.localX = Mth.clamp(state.localX + kbDx, state.anchorX - half, state.anchorX + half);
        state.localY = Mth.clamp(state.localY + kbDy, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
        state.localZ = Mth.clamp(state.localZ + kbDz, state.anchorZ - half, state.anchorZ + half);

        // 滚轮推拉（即时响应，含距离阻尼）
        if (state.pendingScroll != 0.0F) {
            double pitchRad = Math.toRadians(state.localPitch);
            double dampingFactor = computeDollyDamping(yawRad, pitchRad);
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL * dampingFactor;
            double scrollX = -Math.sin(yawRad) * Math.cos(pitchRad) * scroll;
            double scrollY = -Math.sin(pitchRad) * scroll;
            double scrollZ = Math.cos(yawRad) * Math.cos(pitchRad) * scroll;
            state.localX = Mth.clamp(state.localX + scrollX, state.anchorX - half, state.anchorX + half);
            state.localY = Mth.clamp(state.localY + scrollY, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
            state.localZ = Mth.clamp(state.localZ + scrollZ, state.anchorZ - half, state.anchorZ + half);
        }

        // 拖拽位移（即时响应，无惯性无平滑）
        if (state.pendingPanX != 0.0F || state.pendingPanY != 0.0F) {
            double dragScale = 0.010D * Math.max(8.0D, state.localHeightOffset) * sensScale * sensNorm;
            double dragDx = cos * -state.pendingPanY * dragScale + (-sin) * state.pendingPanX * dragScale;
            double dragDz = sin * -state.pendingPanY * dragScale + cos * state.pendingPanX * dragScale;
            state.localX = Mth.clamp(state.localX + dragDx, state.anchorX - half, state.anchorX + half);
            state.localZ = Mth.clamp(state.localZ + dragDz, state.anchorZ - half, state.anchorZ + half);
        }
        state.localHeightOffset = state.localY - state.anchorY;
    }

    /**
     * 计算滚轮距离阻尼因子。
     * <p>摄像机沿视线方向发射射线，若在 {@link #DOLLY_DAMP_MAX_DIST} 内命中方块，
     * 则距离越近阻尼越大（移动距离越短），实现靠近方块时滚轮推拉丝滑减速。</p>
     *
     * @param yawRad   当前水平角（弧度）
     * @param pitchRad 当前俯仰角（弧度）
     * @return 阻尼系数 [DOLLY_DAMP_MIN_FACTOR, 1.0]，无阻挡或距离太远返回 1.0
     */
    private double computeDollyDamping(double yawRad, double pitchRad) {
        Minecraft mc = mc();
        if (mc.level == null) return 1.0D;

        Vec3 from = new Vec3(state.localX, state.localY, state.localZ);
        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 to = from.add(dx * DOLLY_DAMP_RAY_RANGE, dy * DOLLY_DAMP_RAY_RANGE, dz * DOLLY_DAMP_RAY_RANGE);

        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.getCameraEntity()));
        if (hit.getType() == HitResult.Type.BLOCK) {
            double dist = from.distanceTo(hit.getLocation());
            if (dist < DOLLY_DAMP_MAX_DIST) {
                double t = dist / DOLLY_DAMP_MAX_DIST;
                // smoothstep 平滑过渡
                t = t * t * (3.0D - 2.0D * t);
                return Mth.lerp(t, DOLLY_DAMP_MIN_FACTOR, 1.0D);
            }
        }
        return 1.0D;
    }

    private void savePrevState() {
        state.prevX = state.localX;
        state.prevY = state.localY;
        state.prevZ = state.localZ;
        state.prevYaw = state.localYaw;
        state.prevPitch = state.localPitch;
    }

    /**
     * 同步摄像机实体位置和视角，必要时切换 {@code mc.cameraEntity}。
     * <p>仅在 {@link #tick} 和 {@link #applyServerCameraState} 中调用（20Hz），
     * {@code setCameraEntity} 带 10 tick 冷却防抖。</p>
     */
    private void syncCameraEntity(Minecraft mc) {
        if (mc.level == null || !state.localReady) return;
        ensureMirrorCamera(mc);
        if (state.localMirrorCamera != null) {
            state.localMirrorCamera.snapInterpolated(
                    state.prevX, state.prevY, state.prevZ, state.prevYaw, state.prevPitch,
                    state.localX, state.localY, state.localZ, state.localYaw, state.localPitch);
            if (mc.getCameraEntity() != state.localMirrorCamera) {
                if (cameraRestoreCooldown <= 0) {
                    mc.setCameraEntity(state.localMirrorCamera);
                    cameraRestoreCooldown = RESTORE_COOLDOWN_TICKS;
                } else {
                    cameraRestoreCooldown--;
                }
            } else if (cameraRestoreCooldown > 0) {
                cameraRestoreCooldown--;
            }
        }
    }

    private void ensureMirrorCamera(Minecraft mc) {
        if (state.localMirrorCamera != null && state.localMirrorCamera.level() == mc.level) return;
        if (cachedCameraEntityType == null) {
            cachedCameraEntityType = (EntityType<RtsCameraEntity>) com.rtsbuilding.rtsbuilding.common.RtsEntities.RTS_CAMERA_ENTITY.get();
        }
        state.localMirrorCamera = new RtsCameraEntity(cachedCameraEntityType, mc.level);
    }

    private static float snapQuarter(float yaw) {
        return Math.round(yaw / 90.0F) * 90.0F;
    }

    private CameraInput readCameraInput(Minecraft mc) {
        float forward = 0.0F;
        float strafe = 0.0F;
        float vertical = 0.0F;

        long window = mc.getWindow().getWindow();

        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) forward += 1.0F;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) forward -= 1.0F;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) strafe += 1.0F;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) strafe -= 1.0F;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS) vertical += 1.0F;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS) vertical -= 1.0F;

        return new CameraInput(forward, strafe, vertical, mc.options.keySprint.isDown());
    }

    private record CameraInput(float forward, float strafe, float vertical, boolean fast) {
        boolean hasMovement() {
            return forward != 0.0F || strafe != 0.0F || vertical != 0.0F;
        }
    }
}
