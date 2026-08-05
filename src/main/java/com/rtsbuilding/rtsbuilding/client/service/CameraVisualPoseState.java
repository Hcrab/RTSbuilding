package com.rtsbuilding.rtsbuilding.client.service;

/**
 * 渲染相机姿态的短时插值 owner。
 *
 * <p>只保存视觉姿态并向逻辑姿态收敛，不创建实体、不恢复 Minecraft 相机，
 * 也不参与服务端运动积分，避免视觉平滑与权威位置形成第二套运动模型。</p>
 */
final class CameraVisualPoseState {
    private static final float POSITION_RESPONSE_SECONDS = 0.018F;
    private static final float ROTATION_RESPONSE_SECONDS = 0.014F;
    private static final double POSITION_EPSILON = 1.0e-4D;
    private static final float ROTATION_EPSILON = 1.0e-3F;

    private boolean ready;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;

    boolean ready() { return ready; }
    double x() { return x; }
    double y() { return y; }
    double z() { return z; }
    float yaw() { return yaw; }
    float pitch() { return pitch; }
    void clear() { ready = false; }

    void snap(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch, boolean localReady) {
        if (!localReady) { ready = false; return; }
        x = targetX; y = targetY; z = targetZ; yaw = targetYaw; pitch = targetPitch; ready = true;
    }

    void update(double targetX, double targetY, double targetZ, float targetYaw, float targetPitch,
                boolean localReady, float frameSeconds) {
        if (!ready) { snap(targetX, targetY, targetZ, targetYaw, targetPitch, localReady); return; }
        if (!(frameSeconds > 0.0F)) return;
        float positionAlpha = RtsCameraSmoothingMath.exponentialAlpha(frameSeconds, POSITION_RESPONSE_SECONDS);
        float rotationAlpha = RtsCameraSmoothingMath.exponentialAlpha(frameSeconds, ROTATION_RESPONSE_SECONDS);
        x = approach(x, targetX, positionAlpha);
        y = approach(y, targetY, positionAlpha);
        z = approach(z, targetZ, positionAlpha);
        yaw = approachAngle(yaw, targetYaw, rotationAlpha);
        pitch = approachAngle(pitch, targetPitch, rotationAlpha);
    }

    private static double approach(double current, double target, float alpha) {
        double next = current + (target - current) * alpha;
        return Math.abs(target - next) <= POSITION_EPSILON ? target : next;
    }

    private static float approachAngle(float current, float target, float alpha) {
        float next = RtsCameraSmoothingMath.interpolateAngleDegrees(current, target, alpha);
        return Math.abs(com.rtsbuilding.rtsbuilding.platform.math.MathHelper.wrapDegrees(target - next)) <= ROTATION_EPSILON ? target : next;
    }
}
