package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.util.math.Vec3d;

/**
 * R 放置状态面板打开期间使用的客户端射线快照。
 *
 * <p>它只冻结“下一次放置”的观察射线，不接管相机或鼠标输入。这样玩家可以把
 * 鼠标移到面板选项上，而放置虚影、目标方块和 BlockPlaceContext 仍使用打开
 * 面板那一刻的方向。世界方块旋转与服务端网络请求不读取这里的状态。</p>
 */
public final class RtsPlacementRayFreeze {
    private static Vec3d origin;
    private static Vec3d direction;

    private RtsPlacementRayFreeze() {
    }

    public static void freeze(Vec3d rayOrigin, Vec3d rayDirection) {
        if (rayOrigin == null || rayDirection == null || rayDirection.lengthSquared() < 1.0E-8D) {
            clear();
            return;
        }
        origin = rayOrigin;
        direction = rayDirection.normalize();
    }

    public static boolean isFrozen() {
        return origin != null && direction != null;
    }

    public static Vec3d originOr(Vec3d fallback) {
        return origin == null ? fallback : origin;
    }

    public static Vec3d directionOr(Vec3d fallback) {
        return direction == null ? fallback : direction;
    }

    public static void clear() {
        origin = null;
        direction = null;
    }
}
