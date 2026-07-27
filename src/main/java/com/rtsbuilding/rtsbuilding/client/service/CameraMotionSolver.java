package com.rtsbuilding.rtsbuilding.client.service;

import net.minecraft.util.math.MathHelper;

/**
 * 相机单步运动与边界钳制的纯数学 owner。
 *
 * <p>不读取键盘、Minecraft 实例或网络状态，只把当前姿态与一批输入转换成下一姿态；
 * 服务继续负责输入累计、平滑时序和向服务端同步。</p>
 */
final class CameraMotionSolver {
    private static final float ROTATE_GAIN_X = 0.24F;
    private static final float ROTATE_GAIN_Y = 0.22F;
    private static final double DOLLY_PER_SCROLL = 2.6D;
    private static final double VERTICAL_SPEED = 0.32D;
    private static final double FAST_VERTICAL_SPEED = 0.55D;
    private static final double MIN_HEIGHT_OFFSET = -35.0D;
    private static final double MAX_HEIGHT_OFFSET = 110.0D;

    private CameraMotionSolver() {}

    static Pose solve(Pose pose, Bounds bounds, Input input) {
        float yaw = pose.yawDeg + input.rotateX * ROTATE_GAIN_X;
        if (input.rotateSteps != 0) yaw = Math.round((yaw + 90.0F * input.rotateSteps) / 90.0F) * 90.0F;
        float pitch = MathHelper.clamp(pose.pitchDeg + input.rotateY * ROTATE_GAIN_Y, -90.0F, 90.0F);
        double speed = input.fast ? 0.80D : 0.45D;
        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);
        double dx = (-sin * input.forward + cos * input.strafe) * speed;
        double dz = (cos * input.forward + sin * input.strafe) * speed;
        double dragScale = 0.020D * Math.max(8.0D, pose.heightOffset);
        double moveRight = input.panX * dragScale;
        double moveForward = -input.panY * dragScale;
        dx += Math.cos(yawRad) * moveRight - Math.sin(yawRad) * moveForward;
        dz += Math.sin(yawRad) * moveRight + Math.cos(yawRad) * moveForward;
        double x = pose.x + dx;
        double y = pose.y + MathHelper.clamp(input.vertical, -4.0F, 4.0F) * (input.fast ? FAST_VERTICAL_SPEED : VERTICAL_SPEED);
        double z = pose.z + dz;
        if (input.scroll != 0.0F) {
            double pitchRad = Math.toRadians(pitch);
            double dolly = input.scroll * DOLLY_PER_SCROLL;
            x += -Math.sin(yawRad) * Math.cos(pitchRad) * dolly;
            y += -Math.sin(pitchRad) * dolly;
            z += Math.cos(yawRad) * Math.cos(pitchRad) * dolly;
        }
        x = MathHelper.clamp(x, bounds.anchorX - bounds.maxRadius, bounds.anchorX + bounds.maxRadius);
        z = MathHelper.clamp(z, bounds.anchorZ - bounds.maxRadius, bounds.anchorZ + bounds.maxRadius);
        y = MathHelper.clamp(y, bounds.anchorY + MIN_HEIGHT_OFFSET, bounds.anchorY + MAX_HEIGHT_OFFSET);
        return new Pose(x, y, z, y - bounds.anchorY, yaw, pitch);
    }

    record Pose(double x, double y, double z, double heightOffset, float yawDeg, float pitchDeg) {}
    record Bounds(double anchorX, double anchorY, double anchorZ, double maxRadius) {}
    record Input(float forward, float strafe, float vertical, float panX, float panY,
                 float rotateX, float rotateY, float scroll, int rotateSteps, boolean fast) {}
}
