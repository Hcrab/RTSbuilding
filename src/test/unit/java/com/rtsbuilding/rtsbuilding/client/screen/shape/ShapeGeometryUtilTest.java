package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
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
    void normalCylinderBaseUsesExactlyTheNormalCircleFootprint() {
        BlockPos start = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input circleInput = new ShapeBuildTypes.Input(
                BuildShape.CIRCLE,
                Direction.UP,
                Direction.UP,
                start,
                new BlockPos(3, 64, 0),
                0,
                false);
        ShapeBuildTypes.Input cylinderInput = new ShapeBuildTypes.Input(
                BuildShape.CYLINDER,
                Direction.UP,
                Direction.UP,
                start,
                new BlockPos(3, 64, 0),
                0,
                false);

        assertEquals(
                new HashSet<>(ShapeGeometryUtil.buildShapePositions(circleInput, ShapeFillMode.FILL)),
                new HashSet<>(ShapeGeometryUtil.buildShapePositions(cylinderInput, ShapeFillMode.FILL)));
        assertEquals(
                new HashSet<>(ShapeGeometryUtil.buildShapePositions(circleInput, ShapeFillMode.HOLLOW)),
                new HashSet<>(ShapeGeometryUtil.buildShapePositions(cylinderInput, ShapeFillMode.HOLLOW)));
    }

    @Test
    void verticalCylinderExtrudesAlongPlaneNormalInsteadOfAlwaysY() {
        BlockPos start = new BlockPos(0, 64, 0);
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.CYLINDER,
                Direction.EAST,
                Direction.UP,
                start,
                new BlockPos(0, 66, 0),
                2,
                false);

        List<BlockPos> fill = ShapeGeometryUtil.buildShapePositions(input, ShapeFillMode.FILL);

        assertTrue(fill.contains(new BlockPos(2, 64, 0)));
        assertTrue(fill.contains(new BlockPos(1, 66, 0)));
        assertFalse(fill.contains(new BlockPos(0, 67, 0)));
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

    @Test
    void advancedCircleCanUseEllipticFootprintInsideSelectionBox() {
        RtsCullingBox box = new RtsCullingBox(
                0,
                new BlockPos(-2, 64, -1),
                new BlockPos(2, 64, 1));

        List<BlockPos> fill = ShapeGeometryUtil.buildAdvancedShapePositions(
                BuildShape.CIRCLE, box, ShapeFillMode.FILL);

        assertTrue(fill.contains(new BlockPos(0, 64, 0)));
        assertTrue(fill.contains(new BlockPos(2, 64, 0)));
        assertFalse(fill.contains(new BlockPos(-2, 64, -1)));
    }

    @Test
    void advancedCircleCanUseVerticalWallEllipse() {
        RtsCullingBox box = new RtsCullingBox(
                0,
                new BlockPos(0, 62, -1),
                new BlockPos(0, 66, 1));

        List<BlockPos> fill = ShapeGeometryUtil.buildAdvancedShapePositions(
                BuildShape.CIRCLE, box, ShapeFillMode.FILL, Direction.EAST);

        assertTrue(fill.contains(new BlockPos(0, 64, 0)));
        assertTrue(fill.contains(new BlockPos(0, 66, 0)));
        assertTrue(fill.contains(new BlockPos(0, 64, 1)));
        assertFalse(fill.contains(new BlockPos(0, 66, 1)));
    }

    @Test
    void advancedBallHollowKeepsEllipsoidSurfaceAndRemovesCenter() {
        RtsCullingBox box = new RtsCullingBox(
                0,
                new BlockPos(-1, 63, -1),
                new BlockPos(1, 65, 1));

        List<BlockPos> hollow = ShapeGeometryUtil.buildAdvancedShapePositions(
                BuildShape.BALL, box, ShapeFillMode.HOLLOW);

        assertTrue(hollow.contains(new BlockPos(-1, 64, 0)));
        assertFalse(hollow.contains(new BlockPos(0, 64, 0)));
    }

    @Test
    void advancedWallUsesOnlyLengthAndHeightWithoutHiddenThickness() {
        RtsCullingBox box = new RtsCullingBox(
                0,
                new BlockPos(0, 64, 0),
                new BlockPos(4, 66, 2));

        List<BlockPos> fill = ShapeGeometryUtil.buildAdvancedShapePositions(
                BuildShape.WALL, box, ShapeFillMode.FILL);

        assertTrue(fill.contains(new BlockPos(4, 66, 0)));
        assertFalse(fill.contains(new BlockPos(4, 66, 2)));
        assertEquals(15, new HashSet<>(fill).size());
    }
}
