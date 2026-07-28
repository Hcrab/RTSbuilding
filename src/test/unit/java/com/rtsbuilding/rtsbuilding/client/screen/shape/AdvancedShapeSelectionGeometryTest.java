package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高级形状选区必须能在会话与包围盒之间稳定往返，并从普通预览半径开始。
 */
class AdvancedShapeSelectionGeometryTest {
    @Test
    void circleStartsFromCenteredPlaneRadius() {
        ShapeBuildTypes.Session session = session(
                BuildShape.CIRCLE,
                EnumFacing.UP,
                new BlockPos(10, 64, 10),
                new BlockPos(13, 64, 14),
                0);

        RtsCullingBox box = AdvancedShapeSelectionGeometry.initialBox(session);

        assertEquals(new BlockPos(5, 64, 5), box.min());
        assertEquals(new BlockPos(15, 64, 15), box.max());
    }

    @Test
    void cylinderUsesPlaneNormalForHeightAndRoundTripsThroughSession() {
        ShapeBuildTypes.Session session = session(
                BuildShape.CYLINDER,
                EnumFacing.EAST,
                new BlockPos(10, 64, 10),
                new BlockPos(10, 67, 14),
                2);

        RtsCullingBox initial = AdvancedShapeSelectionGeometry.initialBox(session);
        ShapeBuildTypes.Session rebuilt =
                AdvancedShapeSelectionGeometry.sessionFromBox(session, initial);
        RtsCullingBox roundTrip =
                AdvancedShapeSelectionGeometry.boxFromSession(rebuilt);

        assertEquals(new BlockPos(10, 59, 5), initial.min());
        assertEquals(new BlockPos(12, 69, 15), initial.max());
        assertEquals(2, rebuilt.boxHeightOffset());
        assertEquals(new BlockPos(10, 69, 15), rebuilt.pointB());
        assertEquals(initial, roundTrip);
    }

    @Test
    void ballStartsFromCenteredSpatialRadius() {
        ShapeBuildTypes.Session session = session(
                BuildShape.BALL,
                EnumFacing.UP,
                new BlockPos(10, 64, 10),
                new BlockPos(13, 68, 10),
                0);

        RtsCullingBox box = AdvancedShapeSelectionGeometry.initialBox(session);

        assertEquals(new BlockPos(5, 59, 5), box.min());
        assertEquals(new BlockPos(15, 69, 15), box.max());
    }

    @Test
    void rectilinearSessionKeepsVerticalHeightAndMouseReference() {
        ShapeBuildTypes.Session previous = session(
                BuildShape.BOX,
                EnumFacing.UP,
                new BlockPos(2, 8, 4),
                new BlockPos(6, 8, 9),
                3);
        RtsCullingBox box = new RtsCullingBox(
                0,
                new BlockPos(1, 7, 3),
                new BlockPos(5, 11, 8));

        ShapeBuildTypes.Session rebuilt =
                AdvancedShapeSelectionGeometry.sessionFromBox(previous, box);

        assertEquals(new BlockPos(1, 7, 3), rebuilt.pointA());
        assertEquals(new BlockPos(5, 7, 8), rebuilt.pointB());
        assertEquals(4, rebuilt.boxHeightOffset());
        assertEquals(previous.boxHeightMouseBaseY(), rebuilt.boxHeightMouseBaseY());
        assertEquals(ShapeBuildTypes.Phase.READY_CONFIRM, rebuilt.phase());
    }

    @Test
    void initialHeightKeepsRoundShapesFlatAndRectilinearShapesVertical() {
        BlockPos first = new BlockPos(0, 10, 0);
        BlockPos second = new BlockPos(0, 16, 0);

        assertEquals(
                0,
                AdvancedShapeSelectionGeometry.initialHeightOffset(
                        BuildShape.CIRCLE,
                        first,
                        second));
        assertEquals(
                0,
                AdvancedShapeSelectionGeometry.initialHeightOffset(
                        BuildShape.BALL,
                        first,
                        second));
        assertEquals(
                6,
                AdvancedShapeSelectionGeometry.initialHeightOffset(
                        BuildShape.WALL,
                        first,
                        second));
        assertNull(AdvancedShapeSelectionGeometry.initialBox(null));
    }

    private static ShapeBuildTypes.Session session(
            BuildShape shape,
            EnumFacing planeFace,
            BlockPos pointA,
            BlockPos pointB,
            int heightOffset) {
        return new ShapeBuildTypes.Session(
                shape,
                planeFace,
                EnumFacing.UP,
                pointA,
                pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                heightOffset,
                42.0D);
    }
}
