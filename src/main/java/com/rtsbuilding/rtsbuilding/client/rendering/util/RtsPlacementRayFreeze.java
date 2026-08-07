package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.world.phys.Vec3;

/**
 * 放置状态轮盘打开期间使用的客户端射线快照。
 *
 * <p>本类只冻结下一次放置的观察射线，让玩家移动鼠标选择轮盘选项时，虚影、目标方块与
 * 放置上下文仍指向打开轮盘那一刻的位置；它不接管相机输入或服务端请求。</p>
 */
public final class RtsPlacementRayFreeze {
    private static Vec3 origin;
    private static Vec3 direction;

    private RtsPlacementRayFreeze() {
    }

    public static void freeze(Vec3 rayOrigin, Vec3 rayDirection) {
        if (rayOrigin == null || rayDirection == null || rayDirection.lengthSqr() < 1.0E-8D) {
            clear();
            return;
        }
        origin = rayOrigin;
        direction = rayDirection.normalize();
    }

    public static boolean isFrozen() {
        return origin != null && direction != null;
    }

    public static Vec3 originOr(Vec3 fallback) {
        return origin == null ? fallback : origin;
    }

    public static Vec3 directionOr(Vec3 fallback) {
        return direction == null ? fallback : direction;
    }

    public static void clear() {
        origin = null;
        direction = null;
    }
}
