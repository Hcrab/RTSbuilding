package com.rtsbuilding.rtsbuilding.compat.sable;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/** 验证未安装/未进入 Sable 子世界时，空间适配仍保持完整三轴原版距离。 */
class RtsSableSpatialCompatTest {
    @Test
    void noSubLevelDistanceUsesAllThreeAxes() {
        Level ordinaryLevel = mock(Level.class);
        Vec3 first = new Vec3(1.0D, 2.0D, 3.0D);
        Vec3 second = new Vec3(4.0D, 6.0D, 8.0D);

        assertNotNull(SableCompanion.INSTANCE);
        assertEquals(50.0D,
                RtsSableSpatialCompat.logicalDistanceSquared(ordinaryLevel, first, second),
                1.0E-9D);
    }
}
