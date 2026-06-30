package com.rtsbuilding.rtsbuilding.client.module.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * 玩家环绕模式摄像机操控——相机绕着玩家实体旋转，并始终看向玩家。
 *
 * <p>输入累积输入（旋转/平移/滚轮）更新轨道参数（水平角、俯仰角、半径），
 * 每帧从轨道参数计算相机位置和朝向。</p>
 *
 * <p>此类的所有方法均为包内可见，仅由 {@link CameraModule} 调用。</p>
 */
final class PlayerOrbitCameraMode {

    // ======================================================================
    //  Constants
    // ======================================================================

    private static final float ROT_INPUT_CLAMP = 20.0F;
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;

    // ======================================================================
    //  Initialization
    // ======================================================================

    /**
     * 初始化玩家环绕模式参数——根据当前相机位置和玩家位置计算初始轨道角/半径。
     * 调用前需确保 {@code Minecraft.getInstance().player != null}。
     */
    void init(CameraState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double tx = mc.player.getX();
        double ty = mc.player.getY() + mc.player.getEyeHeight();
        double tz = mc.player.getZ();
        state.orbitTargetX = tx;
        state.orbitTargetY = ty;
        state.orbitTargetZ = tz;

        double dx = state.localX - tx;
        double dy = state.localY - ty;
        double dz = state.localZ - tz;
        state.orbitRadius = Math.sqrt(dx * dx + dy * dy + dz * dz);
        state.orbitAngle = Math.atan2(dx, dz);
        double distXZ = Math.sqrt(dx * dx + dz * dz);
        state.orbitPitch = Math.atan2(dy, distXZ);
        state.orbitRadius = Math.max(1.0, state.orbitRadius);
    }

    // ======================================================================
    //  Frame-rate input processing
    // ======================================================================

    /**
     * 玩家环绕模式输入处理——将累积输入应用到轨道参数并计算姿态。
     * 相机始终看向玩家实体，目标位置每帧跟随玩家移动。
     *
     * @param state       相机状态
     * @param partialTick 渲染帧 partialTick，用于插值玩家位置实现平滑跟随
     */
    void processInput(CameraState state, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 使用帧率级插值后的玩家位置作为环绕中心
        //    避免直接使用 getX()（仅 tick 级更新，20Hz 步进导致视觉卡顿）
        double playerX = Mth.lerp(partialTick, mc.player.xo, mc.player.getX());
        double playerY = Mth.lerp(partialTick, mc.player.yo, mc.player.getY()) + mc.player.getEyeHeight();
        double playerZ = Mth.lerp(partialTick, mc.player.zo, mc.player.getZ());
        state.orbitTargetX = playerX;
        state.orbitTargetY = playerY;
        state.orbitTargetZ = playerZ;

        // 2. 旋转输入 → 修改轨道水平角/俯仰角
        float rawX = Mth.clamp(state.pendingRawRotateX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float rawY = Mth.clamp(state.pendingRawRotateY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panX = Mth.clamp(state.pendingPanX, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float panY = Mth.clamp(state.pendingPanY, -ROT_INPUT_CLAMP, ROT_INPUT_CLAMP);
        float sensScale = state.inputSensitivity;

        state.orbitAngle += (rawX + panX) * state.rotateSensitivity * sensScale * ROTATE_GAIN_X * 0.01;
        state.orbitPitch += (rawY + panY) * state.rotateSensitivity * sensScale * ROTATE_GAIN_Y * 0.01;
        state.orbitPitch = Mth.clamp(state.orbitPitch, -Math.PI * 0.45, Math.PI * 0.45);

        // 3. 滚轮输入 → 修改轨道半径
        if (state.pendingScroll != 0.0F) {
            double scroll = state.pendingScroll * DOLLY_PER_SCROLL;
            state.orbitRadius = Math.max(1.0, state.orbitRadius - scroll);
        }

        // 4. 从轨道参数计算相机位置
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

        // 5. 计算朝向目标的旋转角（相机始终看向玩家）
        state.localYaw = Mth.wrapDegrees(180.0f - (float) Math.toDegrees(state.orbitAngle));
        state.localPitch = (float) Math.toDegrees(state.orbitPitch);

        // 6. 清除已消费的累积输入
        state.pendingRawRotateX = 0;
        state.pendingRawRotateY = 0;
        state.pendingPanX = 0;
        state.pendingPanY = 0;
        state.pendingScroll = 0;
    }
}
