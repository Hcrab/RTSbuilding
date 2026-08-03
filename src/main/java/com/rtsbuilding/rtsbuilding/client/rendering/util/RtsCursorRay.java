package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.input.Mouse;

/**
 * RTS 世界交互与悬停高亮共享的鼠标射线。
 *
 * <p>该类只负责把当前相机姿态与 framebuffer 鼠标坐标转换成世界射线；不负责
 * 方块裁剪、实体选择或放置冻结。统一这里可以避免快速旋转时操作射线和高亮射线
 * 分别使用插值姿态、当前姿态而指向不同方块。</p>
 */
public final class RtsCursorRay {
    private RtsCursorRay() {
    }

    public static Snapshot capture(Minecraft minecraft) {
        Entity camera = minecraft == null ? null : minecraft.getRenderViewEntity();
        if (minecraft == null || camera == null) {
            return new Snapshot(Vec3d.ZERO, new Vec3d(0.0D, 0.0D, -1.0D));
        }

        double width = Math.max(1.0D, minecraft.displayWidth);
        double height = Math.max(1.0D, minecraft.displayHeight);
        double normalizedX = Mouse.isCreated() ? Mouse.getX() / width * 2.0D - 1.0D : 0.0D;
        double normalizedY = Mouse.isCreated() ? Mouse.getY() / height * 2.0D - 1.0D : 0.0D;

        double yaw = Math.toRadians(camera.rotationYaw);
        double pitch = Math.toRadians(camera.rotationPitch);
        Vec3d forward = new Vec3d(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();
        Vec3d screenRight = new Vec3d(-Math.cos(yaw), 0.0D, -Math.sin(yaw)).normalize();
        Vec3d screenUp = screenRight.crossProduct(forward).normalize();
        double tanY = Math.tan(Math.toRadians(minecraft.gameSettings.fovSetting) * 0.5D);
        double tanX = tanY * width / height;
        Vec3d direction = forward
                .add(screenRight.scale(normalizedX * tanX))
                .add(screenUp.scale(normalizedY * tanY))
                .normalize();
        return new Snapshot(camera.getPositionEyes(1.0F), direction);
    }

    public static final class Snapshot {
        private final Vec3d origin;
        private final Vec3d direction;

        private Snapshot(Vec3d origin, Vec3d direction) {
            this.origin = origin;
            this.direction = direction;
        }

        public Vec3d origin() {
            return origin;
        }

        public Vec3d direction() {
            return direction;
        }
    }
}
