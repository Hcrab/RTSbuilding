package com.rtsbuilding.rtsbuilding.client.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 自由模式摄像机操控——键盘位移、鼠标拖拽平移、滚轮推拉、鼠标旋转视角。
 *
 * <p>处理自由模式下所有累积输入（旋转/平移/滚轮/键盘），
 * 更新 {@link CameraState} 中的位置、偏航角、俯仰角。</p>
 *
 * <p>此类的所有方法均为包内可见，仅由 {@link CameraModule} 调用。</p>
 */
final class FreeCameraMode {

    // ======================================================================
    //  Constants
    // ======================================================================

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final double DOLLY_DAMP_MAX_DIST = 30.0D;
    private static final double DOLLY_DAMP_MIN_FACTOR = 0.05D;
    private static final double DOLLY_DAMP_RAY_RANGE = 128.0D;
    private static final double MIN_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_HEIGHT_OFFSET = 110.0D;
    private static final float MIN_PITCH = -90.0F;
    private static final float MAX_PITCH = 90.0F;
    // —— EMA 旋转平滑参数（移植自旧版 CameraOrbitService）
    private static final float ROT_EMA_ALPHA = 0.28F;
    private static final float ROT_EMA_DECAY = 0.78F;
    private static final float CAMERA_INPUT_EPSILON = 1.0e-4F;

    // —— EMA 累积值（每帧衰减，无输入时快速归零）
    private float emaRotateX;
    private float emaRotateY;

    // ======================================================================
    //  Entry point
    // ======================================================================

    /**
     * 处理所有累积输入，更新自由模式下的摄像机姿态。
     * <p>输入来源：键盘 (CameraInput)、拖拽 (pendingPanX/Y)、滚轮 (pendingScroll)、
     * 旋转 (pendingRawRotateX/Y)、四分之一转 (pendingRotateSteps)。</p>
     *
     * @param state 摄像机状态（会被就地修改）
     * @param input 键盘输入
     */
    void processInput(CameraState state, CameraInput input) {
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);

        // EMA 平滑——指数移动平均，消除鼠标微抖动
        this.emaRotateX += (rawX - this.emaRotateX) * ROT_EMA_ALPHA;
        this.emaRotateY += (rawY - this.emaRotateY) * ROT_EMA_ALPHA;

        // 输入接近零时快速衰减，避免平滑滞后
        if (Math.abs(rawX) < CAMERA_INPUT_EPSILON) this.emaRotateX *= ROT_EMA_DECAY;
        if (Math.abs(rawY) < CAMERA_INPUT_EPSILON) this.emaRotateY *= ROT_EMA_DECAY;

        float sensScale = state.inputSensitivity;
        float rotateXForTick = this.emaRotateX * state.rotateSensitivity * sensScale;
        float rotateYForTick = this.emaRotateY * state.rotateSensitivity * sensScale;

        // 平滑后仍低于阈值则强制归零（彻底停住）
        if (Math.abs(rotateXForTick) < CAMERA_INPUT_EPSILON) {
            rotateXForTick = 0.0F;
            this.emaRotateX = 0.0F;
        }
        if (Math.abs(rotateYForTick) < CAMERA_INPUT_EPSILON) {
            rotateYForTick = 0.0F;
            this.emaRotateY = 0.0F;
        }

        state.localYaw += rotateXForTick * ROTATE_GAIN_X;
        if (state.pendingRotateSteps != 0) {
            state.localYaw = snapQuarter(state.localYaw + 90.0F * state.pendingRotateSteps);
        }
        state.localPitch = Mth.clamp(state.localPitch + rotateYForTick * ROTATE_GAIN_Y, MIN_PITCH, MAX_PITCH);

        double sensNorm = state.rotateSensitivity / 5.0D;
        double speed = (input.fast ? 0.80D : 0.45D) * sensNorm;
        double yawRad = Math.toRadians(state.localYaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double half = state.maxRadius;

        // 键盘位移
        double kbDx = (-sin * input.forward + cos * input.strafe) * speed;
        double kbDz = (cos * input.forward + sin * input.strafe) * speed;
        double kbDy = input.vertical * (input.fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED) * sensNorm;
        state.localX = Mth.clamp(state.localX + kbDx, state.anchorX - half, state.anchorX + half);
        state.localY = Mth.clamp(state.localY + kbDy, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
        state.localZ = Mth.clamp(state.localZ + kbDz, state.anchorZ - half, state.anchorZ + half);

        // 滚轮推拉（含距离阻尼）
        if (state.pendingScroll != 0.0F) {
            double pitchRad = Math.toRadians(state.localPitch);
            double dampingFactor = computeDollyDamping(state, yawRad, pitchRad);
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL * dampingFactor;
            double scrollX = -Math.sin(yawRad) * Math.cos(pitchRad) * scroll;
            double scrollY = -Math.sin(pitchRad) * scroll;
            double scrollZ = Math.cos(yawRad) * Math.cos(pitchRad) * scroll;
            state.localX = Mth.clamp(state.localX + scrollX, state.anchorX - half, state.anchorX + half);
            state.localY = Mth.clamp(state.localY + scrollY, state.anchorY + MIN_HEIGHT_OFFSET, state.anchorY + MAX_HEIGHT_OFFSET);
            state.localZ = Mth.clamp(state.localZ + scrollZ, state.anchorZ - half, state.anchorZ + half);
        }

        // 拖拽位移
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
     * 重置 EMA 累积值——在摄像机启用/恢复时调用。
     */
    void resetEma() {
        this.emaRotateX = 0.0F;
        this.emaRotateY = 0.0F;
    }

    // ======================================================================
    //  Keyboard input reading
    // ======================================================================

    /**
     * 从按键状态读取摄像机键盘输入（已禁用——自由视角不再支持 WASD 移动）。
     */
    CameraInput readCameraInput() {
        return new CameraInput(0.0F, 0.0F, 0.0F, Minecraft.getInstance().options.keySprint.isDown());
    }

    /**
     * 清除所有累积输入。
     */
    void resetAccumulation(CameraState state) {
        state.pendingPanX = 0.0F;
        state.pendingPanY = 0.0F;
        state.pendingScroll = 0.0F;
        state.pendingRotateSteps = 0;
        state.pendingRawRotateX = 0.0F;
        state.pendingRawRotateY = 0.0F;
    }

    // ======================================================================
    //  Dolly damping
    // ======================================================================

    /**
     * 计算滚轮距离阻尼因子。
     * <p>摄像机沿视线方向发射射线，若在 {@link #DOLLY_DAMP_MAX_DIST} 内命中方块，
     * 则距离越近阻尼越大（移动距离越短）。</p>
     */
    private double computeDollyDamping(CameraState state, double yawRad, double pitchRad) {
        Minecraft mc = Minecraft.getInstance();
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
                t = t * t * (3.0D - 2.0D * t);
                return Mth.lerp(t, DOLLY_DAMP_MIN_FACTOR, 1.0D);
            }
        }
        return 1.0D;
    }

    // ======================================================================
    //  Utility
    // ======================================================================

    static float snapQuarter(float yaw) {
        return Math.round(yaw / 90.0F) * 90.0F;
    }

    // ======================================================================
    //  Input record
    // ======================================================================

    record CameraInput(float forward, float strafe, float vertical, boolean fast) {
        boolean hasMovement() {
            return forward != 0.0F || strafe != 0.0F || vertical != 0.0F;
        }
    }
}
