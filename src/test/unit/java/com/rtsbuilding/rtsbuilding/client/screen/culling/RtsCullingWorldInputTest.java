package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
            public Vec3 currentRayOrigin() {
                return new Vec3(0.0D, 64.0D, 0.0D);
            }

            @Override
            public Vec3 computeCursorRayDirection() {
                return new Vec3(1.0D, 0.0D, 1.0D).normalize();
            }

            @Override
            public BlockHitResult pickCullingAwareBlockHit() {
                pickedCullingAwareHit.set(true);
                return new BlockHitResult(Vec3.atCenterOf(firstPoint), Direction.UP, firstPoint, false);
            }
        });

        assertTrue(handled);
        assertTrue(pickedCullingAwareHit.get());
        assertEquals(RtsCullingManager.Phase.NEED_SECOND, manager.phase());
        assertEquals(firstPoint, manager.previewBox().min());
    }
}
