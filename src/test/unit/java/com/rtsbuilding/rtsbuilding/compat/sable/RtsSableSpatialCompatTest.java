package com.rtsbuilding.rtsbuilding.compat.sable;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证空间适配在恒等坐标帧下保持完整的原版三轴距离语义。 */
class RtsSableSpatialCompatTest {
    @Test
    void unchangedCoordinateFrameUsesAllThreeAxes() {
        Vec3 first = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 second = new Vec3(4.0D, 6.0D, 8.0D);

        assertEquals(50.0D,
                RtsSableSpatialCompat.squaredDistance(first, second),
                1.0E-9D);
    }
}
