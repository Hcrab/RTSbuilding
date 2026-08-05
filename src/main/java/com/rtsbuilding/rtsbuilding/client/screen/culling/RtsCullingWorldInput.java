package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

/**
 * 范围剔除管理页的世界点击入口。
 *
 * <p>职责边界：这里只串起“当前射线 + 剔除感知方块命中 + 管理器状态机”。
 * 它刻意不暴露 raw/忽略剔除的 picker，避免之后再把管理页选点改回会被隐藏方块挡住的路径。</p>
 */
public final class RtsCullingWorldInput {
    private RtsCullingWorldInput() {
    }

    public static boolean handleWorldAction(RtsCullingManager manager, Cursor cursor) {
        if (manager == null || cursor == null || !manager.isManagementMode()) {
            return false;
        }
        Vec3d origin = cursor.currentRayOrigin();
        Vec3d direction = cursor.computeCursorRayDirection();
        RayTraceResult hit = cursor.pickCullingAwareBlockHit();
        return CullingUiAdapter.worldPrimary(manager, hit, origin, direction);
    }

    public interface Cursor {
        Vec3d currentRayOrigin();

        Vec3d computeCursorRayDirection();

        RayTraceResult pickCullingAwareBlockHit();
    }
}
