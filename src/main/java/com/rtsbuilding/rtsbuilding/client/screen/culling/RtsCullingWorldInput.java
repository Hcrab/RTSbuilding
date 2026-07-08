package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * World-click entry point for the range-culling management page.
 *
 * <p>This class only connects current ray data, culling-aware block hits, and the manager state machine.
 * It deliberately hides raw non-culling pickers so selection cannot regress to being blocked by hidden blocks.
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
