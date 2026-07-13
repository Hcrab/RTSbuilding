package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeSelectionLimiterTest {
    @Test
    void rectilinearLimitKeepsFirstPointFixed() {
        ShapeBuildTypes.Input input = input(BuildShape.BOX,
                new BlockPos(12, 30, -8), new BlockPos(-80, 120, 90), Direction.UP, 70);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensions(input, 12, 10, 8);

        assertEquals(input.pointA(), limited.pointA());
        assertEquals(new BlockPos(1, 39, -1), limited.pointB());
        assertEquals(9, limited.boxHeightOffset());
    }

    @Test
    void roundLimitProducesMaximumCircleInsteadOfClippedSquare() {
        BlockPos center = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = input(BuildShape.CIRCLE,
                center, new BlockPos(100, 64, 100), Direction.UP, 0);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensions(input, 12, 20, 12);
        int dx = limited.pointB().getX() - center.getX();
        int dz = limited.pointB().getZ() - center.getZ();

        assertEquals(center, limited.pointA());
        assertTrue(Math.sqrt(dx * (double) dx + dz * (double) dz) <= 6.5D);
    }

    @Test
    void cylinderHeightUsesNormalAxisCap() {
        ShapeBuildTypes.Input input = input(BuildShape.CYLINDER,
                new BlockPos(0, 0, 0), new BlockPos(3, 0, 0), Direction.UP, -100);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensions(input, 12, 10, 12);

        assertEquals(-9, limited.boxHeightOffset());
    }

    private static ShapeBuildTypes.Input input(BuildShape shape, BlockPos a, BlockPos b,
            Direction planeFace, int height) {
        return new ShapeBuildTypes.Input(shape, planeFace, planeFace, a, b, height, false);
    }
}
