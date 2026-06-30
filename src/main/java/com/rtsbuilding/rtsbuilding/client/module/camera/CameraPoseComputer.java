package com.rtsbuilding.rtsbuilding.client.module.camera;

import net.minecraft.util.Mth;

/**
 * 方块轨道模式姿态计算器——绕目标方块中心圆周旋转。
 *
 * <p>自由模式操控见 {@link FreeCameraMode}，玩家环绕模式见 {@link PlayerOrbitCameraMode}。</p>
 *
 * <p>此类的所有方法均为包内可见，仅由 {@link CameraModule} 调用。</p>
 */
final class CameraPoseComputer {

    // ======================================================================
    //  Constants
    // ======================================================================

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;

    // ======================================================================
    //  Orbit mode——绕目标方块圆周旋转
    // ======================================================================

    /**
     * 初始化轨道模式的参数——根据当前相机位置和目标方块计算初始轨道角/半径。
     *
     * @param state    相机状态（orbitTarget 必须已设置）
     * @param camX     相机当前位置 X
     * @param camY     相机当前位置 Y
     * @param camZ     相机当前位置 Z
     */
    void initOrbitPose(CameraState state, double camX, double camY, double camZ) {
        double dx = camX - state.orbitTargetX;
        double dy = camY - state.orbitTargetY;
        double dz = camZ - state.orbitTargetZ;
        state.orbitRadius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        state.orbitAngle = Math.atan2(dx, dz);
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        state.orbitPitch = Math.atan2(dy, distXZ);
        // 注意：这里不钳制俯仰角，保持原始精确参数使首个 tick 的 processOrbitInput 不产生位置跳变。
        // 钳制仅在 processOrbitInput 的实时旋转输入时生效。
        state.orbitRadius = Math.max(1.0, state.orbitRadius);
    }

    /**
     * 轨道模式输入处理——将累积输入应用到轨道参数并计算姿态。
     * 相机始终看向目标方块中心。
     */
    void processOrbitInput(CameraState state) {
        // 1. 旋转输入 → 修改轨道水平角/俯仰角
        //    中键拖拽 → pendingRawRotateX/Y，右键拖拽 → pendingPanX/Y
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        // 右键拖拽产生的平移输入也转为旋转控制
        float panX = Mth.clamp(state.pendingPanX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panY = Mth.clamp(state.pendingPanY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float sensScale = state.inputSensitivity;

        state.orbitAngle += (rawX + panX) * state.rotateSensitivity * sensScale * ROTATE_GAIN_X * 0.01;
        state.orbitPitch += (rawY + panY) * state.rotateSensitivity * sensScale * ROTATE_GAIN_Y * 0.01;
        state.orbitPitch = Mth.clamp(state.orbitPitch, -Math.PI * 0.45, Math.PI * 0.45);

        // 2. 滚轮输入 → 修改轨道半径
        if (state.pendingScroll != 0.0F) {
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL;
            state.orbitRadius = Math.max(1.0, state.orbitRadius - scroll);
        }

        // 3. 从轨道参数计算相机位置
        double sinAngle = Math.sin(state.orbitAngle);
        double cosAngle = Math.cos(state.orbitAngle);
        double cosPitch = Math.cos(state.orbitPitch);
        double sinPitch = Math.sin(state.orbitPitch);

        double tx = state.orbitTargetX;
        double ty = state.orbitTargetY;
        double tz = state.orbitTargetZ;
        double r = state.orbitRadius;

        state.localX = tx + r * sinAngle * cosPitch;
        state.localY = ty + r * sinPitch;
        state.localZ = tz + r * cosAngle * cosPitch;
        state.localHeightOffset = state.localY - state.anchorY;

        // 4. 计算朝向目标的旋转角（相机始终看向目标）
        //     从轨道参数直接推导：yaw = 180° - orbitAngle°，pitch = orbitPitch°
        //     避免使用 atan2(-dx, dz)，因为 orbitAngle 在 0°→360° 边界时
        //     atan2 输出会从 -180° 回绕到 180°，造成 360° 跳变导致画面抽搐。
        state.localYaw = Mth.wrapDegrees(180.0f - (float) Math.toDegrees(state.orbitAngle));
        state.localPitch = (float) Math.toDegrees(state.orbitPitch);

        // 5. 清除已消费的累积输入（processOrbitInput 可能在帧率级被调用，必须同步清除）
        state.pendingRawRotateX = 0;
        state.pendingRawRotateY = 0;
        state.pendingPanX = 0;
        state.pendingPanY = 0;
        state.pendingScroll = 0;
    }
}
