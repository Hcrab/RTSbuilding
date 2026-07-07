package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
        Vec3 origin = cursor.currentRayOrigin();
        Vec3 direction = cursor.computeCursorRayDirection();
        BlockHitResult hit = cursor.pickCullingAwareBlockHit();
        return manager.handleWorldAction(hit, origin, direction);
    }

    public interface Cursor {
        Vec3 currentRayOrigin();

        Vec3 computeCursorRayDirection();

        BlockHitResult pickCullingAwareBlockHit();
    }
}
