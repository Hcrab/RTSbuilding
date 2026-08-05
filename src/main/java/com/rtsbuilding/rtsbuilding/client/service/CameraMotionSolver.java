package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;

import java.util.Objects;

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

    static final class Pose {
        private final double x, y, z, heightOffset;
        private final float yawDeg, pitchDeg;
        Pose(double x, double y, double z, double heightOffset, float yawDeg, float pitchDeg) {
            this.x = x; this.y = y; this.z = z; this.heightOffset = heightOffset;
            this.yawDeg = yawDeg; this.pitchDeg = pitchDeg;
        }
        double x() { return x; } double y() { return y; } double z() { return z; }
        double heightOffset() { return heightOffset; }
        float yawDeg() { return yawDeg; } float pitchDeg() { return pitchDeg; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Pose)) return false;
            Pose that = (Pose) other;
            return Double.compare(x, that.x) == 0 && Double.compare(y, that.y) == 0
                    && Double.compare(z, that.z) == 0 && Double.compare(heightOffset, that.heightOffset) == 0
                    && Float.compare(yawDeg, that.yawDeg) == 0 && Float.compare(pitchDeg, that.pitchDeg) == 0;
        }
        @Override public int hashCode() { return Objects.hash(x, y, z, heightOffset, yawDeg, pitchDeg); }
        @Override public String toString() {
            return "Pose[x=" + x + ", y=" + y + ", z=" + z + ", heightOffset=" + heightOffset
                    + ", yawDeg=" + yawDeg + ", pitchDeg=" + pitchDeg + "]";
        }
    }

    static final class Bounds {
        private final double anchorX, anchorY, anchorZ, maxRadius;
        Bounds(double anchorX, double anchorY, double anchorZ, double maxRadius) {
            this.anchorX = anchorX; this.anchorY = anchorY; this.anchorZ = anchorZ; this.maxRadius = maxRadius;
        }
        double anchorX() { return anchorX; } double anchorY() { return anchorY; }
        double anchorZ() { return anchorZ; } double maxRadius() { return maxRadius; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Bounds)) return false;
            Bounds that = (Bounds) other;
            return Double.compare(anchorX, that.anchorX) == 0 && Double.compare(anchorY, that.anchorY) == 0
                    && Double.compare(anchorZ, that.anchorZ) == 0 && Double.compare(maxRadius, that.maxRadius) == 0;
        }
        @Override public int hashCode() { return Objects.hash(anchorX, anchorY, anchorZ, maxRadius); }
        @Override public String toString() {
            return "Bounds[anchorX=" + anchorX + ", anchorY=" + anchorY + ", anchorZ=" + anchorZ
                    + ", maxRadius=" + maxRadius + "]";
        }
    }

    static final class Input {
        private final float forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll;
        private final int rotateSteps;
        private final boolean fast;
        Input(float forward, float strafe, float vertical, float panX, float panY,
                float rotateX, float rotateY, float scroll, int rotateSteps, boolean fast) {
            this.forward = forward; this.strafe = strafe; this.vertical = vertical;
            this.panX = panX; this.panY = panY; this.rotateX = rotateX; this.rotateY = rotateY;
            this.scroll = scroll; this.rotateSteps = rotateSteps; this.fast = fast;
        }
        float forward() { return forward; } float strafe() { return strafe; }
        float vertical() { return vertical; } float panX() { return panX; } float panY() { return panY; }
        float rotateX() { return rotateX; } float rotateY() { return rotateY; } float scroll() { return scroll; }
        int rotateSteps() { return rotateSteps; } boolean fast() { return fast; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Input)) return false;
            Input that = (Input) other;
            return Float.compare(forward, that.forward) == 0 && Float.compare(strafe, that.strafe) == 0
                    && Float.compare(vertical, that.vertical) == 0 && Float.compare(panX, that.panX) == 0
                    && Float.compare(panY, that.panY) == 0 && Float.compare(rotateX, that.rotateX) == 0
                    && Float.compare(rotateY, that.rotateY) == 0 && Float.compare(scroll, that.scroll) == 0
                    && rotateSteps == that.rotateSteps && fast == that.fast;
        }
        @Override public int hashCode() {
            return Objects.hash(forward, strafe, vertical, panX, panY, rotateX, rotateY, scroll, rotateSteps, fast);
        }
        @Override public String toString() {
            return "Input[forward=" + forward + ", strafe=" + strafe + ", vertical=" + vertical
                    + ", panX=" + panX + ", panY=" + panY + ", rotateX=" + rotateX
                    + ", rotateY=" + rotateY + ", scroll=" + scroll + ", rotateSteps=" + rotateSteps
                    + ", fast=" + fast + "]";
        }
    }
}
