package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeGeometryUtilTest {
    @Test
    void cylinderPreviewUsesCircleFootprintAndScrollHeight() {
        BlockPos start = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.CYLINDER,
                Direction.UP,
                Direction.UP,
                start,
                new BlockPos(2, 64, 0),
                2,
                false);

        List<BlockPos> fill = ShapeGeometryUtil.buildShapePositions(input, ShapeFillMode.FILL);
        List<BlockPos> hollow = ShapeGeometryUtil.buildShapePositions(input, ShapeFillMode.HOLLOW);

        assertEquals(39, new HashSet<>(fill).size());
        assertTrue(fill.contains(new BlockPos(0, 65, 0)));
        assertEquals(38, new HashSet<>(hollow).size());
        assertFalse(hollow.contains(new BlockPos(0, 65, 0)));
    }

    @Test
    void ballPreviewCreatesThreeDimensionalRadiusFromCenterPoint() {
        BlockPos start = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.BALL,
                Direction.UP,
                Direction.UP,
                start,
                new BlockPos(1, 64, 0),
                0,
                false);

        List<BlockPos> fill = ShapeGeometryUtil.buildShapePositions(input, ShapeFillMode.FILL);

        assertEquals(7, new HashSet<>(fill).size());
        assertTrue(fill.contains(start.above()));
        assertTrue(fill.contains(start.below()));
    }
}
