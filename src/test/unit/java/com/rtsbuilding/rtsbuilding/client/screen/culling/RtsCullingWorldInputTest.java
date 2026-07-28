package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCullingWorldInputTest {
    @Test
    void worldActionUsesCullingAwareCursorHit() {
        RtsCullingManager manager = new RtsCullingManager();
        manager.setManagementMode(true);
        AtomicBoolean pickedCullingAwareHit = new AtomicBoolean(false);
        BlockPos firstPoint = new BlockPos(8, 64, 8);

        boolean handled = RtsCullingWorldInput.handleWorldAction(manager, new RtsCullingWorldInput.Cursor() {
            @Override
            public Vec3d currentRayOrigin() {
                return new Vec3d(0.0D, 64.0D, 0.0D);
            }

            @Override
            public Vec3d computeCursorRayDirection() {
                return new Vec3d(1.0D, 0.0D, 1.0D).normalize();
            }

            @Override
            public RayTraceResult pickCullingAwareBlockHit() {
                pickedCullingAwareHit.set(true);
                return new RayTraceResult(new Vec3d(firstPoint).add(0.5D, 0.5D, 0.5D), EnumFacing.UP, firstPoint);
            }
        });

        assertTrue(handled);
        assertTrue(pickedCullingAwareHit.get());
        assertEquals(RtsCullingManager.Phase.NEED_SECOND, manager.phase());
        assertEquals(firstPoint, manager.previewBox().min());
    }
}
