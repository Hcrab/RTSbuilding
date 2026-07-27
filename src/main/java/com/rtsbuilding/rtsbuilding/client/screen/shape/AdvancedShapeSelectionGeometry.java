package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * 高级形状选区与交互会话之间的纯几何转换。
 *
 * <p>本类只负责包围盒、半径和轴向高度的确定性换算，不拥有鼠标捕获、动画、
 * 世界命中、尺寸上限或确认流程。交互状态仍由 ScreenShapeController 管理。</p>
 */
public final class AdvancedShapeSelectionGeometry {
    private AdvancedShapeSelectionGeometry() {
    }

    public static RtsCullingBox boxFromSession(ShapeBuildTypes.Session session) {
        if (session == null) {
            return null;
        }
        if (usesPlaneNormalHeight(session.shape())) {
            EnumFacing normal = session.planeFace() == null
                    ? EnumFacing.UP
                    : session.planeFace();
            BlockPos normalEnd = withAxisOffset(
                    session.pointA(),
                    normal.getAxis(),
                    session.boxHeightOffset());
            return new RtsCullingBox(
                    0,
                    session.pointA(),
                    mergeAxis(session.pointB(), normalEnd, normal.getAxis()));
        }
        return RtsCullingBox.fromDiagonal(
                0,
                session.pointA(),
                session.pointB(),
                session.boxHeightOffset());
    }

    public static ShapeBuildTypes.Session sessionFromBox(
            ShapeBuildTypes.Session previous,
            RtsCullingBox box) {
        if (usesPlaneNormalHeight(previous.shape())) {
            EnumFacing normal = previous.planeFace() == null
                    ? EnumFacing.UP
                    : previous.planeFace();
            EnumFacing.Axis normalAxis = normal.getAxis();
            BlockPos min = box.min();
            BlockPos max = box.max();
            BlockPos pointB = mergeAxis(max, min, normalAxis);
            int heightOffset = coordinate(max, normalAxis)
                    - coordinate(min, normalAxis);
            return new ShapeBuildTypes.Session(
                    previous.shape(),
                    previous.planeFace(),
                    previous.placementFace(),
                    min,
                    pointB,
                    ShapeBuildTypes.Phase.READY_CONFIRM,
                    heightOffset,
                    previous.boxHeightMouseBaseY());
        }
        BlockPos min = box.min();
        BlockPos max = box.max();
        BlockPos pointB = new BlockPos(max.getX(), min.getY(), max.getZ());
        return new ShapeBuildTypes.Session(
                previous.shape(),
                previous.planeFace(),
                previous.placementFace(),
                min,
                pointB,
                ShapeBuildTypes.Phase.READY_CONFIRM,
                max.getY() - min.getY(),
                previous.boxHeightMouseBaseY());
    }

    public static RtsCullingBox initialBox(ShapeBuildTypes.Session session) {
        if (session == null || session.pointA() == null || session.pointB() == null) {
            return boxFromSession(session);
        }
        BlockPos center = session.pointA();
        BlockPos pointB = session.pointB();
        return switch (session.shape()) {
            case CIRCLE -> centeredPlaneBox(
                    center,
                    planeRadius(center, pointB, session.planeFace()),
                    session.planeFace(),
                    0);
            case CYLINDER -> centeredPlaneBox(
                    center,
                    planeRadius(center, pointB, session.planeFace()),
                    session.planeFace(),
                    session.boxHeightOffset());
            case BALL -> centeredBox(center, spatialRadius(center, pointB));
            default -> boxFromSession(session);
        };
    }

    public static int initialHeightOffset(
            BuildShape shape,
            BlockPos pointA,
            BlockPos pointB) {
        if (shape == BuildShape.CYLINDER
                || shape == BuildShape.CIRCLE
                || shape == BuildShape.BALL) {
            return 0;
        }
        return pointB == null || pointA == null
                ? 0
                : pointB.getY() - pointA.getY();
    }

    private static boolean usesPlaneNormalHeight(BuildShape shape) {
        return shape == BuildShape.CIRCLE || shape == BuildShape.CYLINDER;
    }

    private static BlockPos withAxisOffset(
            BlockPos origin,
            EnumFacing.Axis axis,
            int offset) {
        return switch (axis) {
            case X -> new BlockPos(
                    origin.getX() + offset,
                    origin.getY(),
                    origin.getZ());
            case Y -> new BlockPos(
                    origin.getX(),
                    origin.getY() + offset,
                    origin.getZ());
            case Z -> new BlockPos(
                    origin.getX(),
                    origin.getY(),
                    origin.getZ() + offset);
        };
    }

    private static BlockPos mergeAxis(
            BlockPos base,
            BlockPos axisSource,
            EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> new BlockPos(
                    axisSource.getX(),
                    base.getY(),
                    base.getZ());
            case Y -> new BlockPos(
                    base.getX(),
                    axisSource.getY(),
                    base.getZ());
            case Z -> new BlockPos(
                    base.getX(),
                    base.getY(),
                    axisSource.getZ());
        };
    }

    private static int coordinate(BlockPos pos, EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static RtsCullingBox centeredPlaneBox(
            BlockPos center,
            int radius,
            EnumFacing face,
            int heightOffset) {
        int safeRadius = Math.max(0, radius);
        EnumFacing[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(
                BuildShape.CIRCLE,
                face);
        EnumFacing normal = face == null ? EnumFacing.UP : face;
        BlockPos min = center;
        BlockPos max = withAxisOffset(
                center,
                normal.getAxis(),
                heightOffset);
        for (EnumFacing axis : axes) {
            min = withAxisOffset(min, axis.getAxis(), -safeRadius);
            max = withAxisOffset(max, axis.getAxis(), safeRadius);
        }
        return new RtsCullingBox(0, min, max);
    }

    private static RtsCullingBox centeredBox(BlockPos center, int radius) {
        int safeRadius = Math.max(0, radius);
        return new RtsCullingBox(
                0,
                new BlockPos(
                        center.getX() - safeRadius,
                        center.getY() - safeRadius,
                        center.getZ() - safeRadius),
                new BlockPos(
                        center.getX() + safeRadius,
                        center.getY() + safeRadius,
                        center.getZ() + safeRadius));
    }

    private static int planeRadius(
            BlockPos center,
            BlockPos point,
            EnumFacing face) {
        EnumFacing[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(
                BuildShape.CIRCLE,
                face);
        int dx = point.getX() - center.getX();
        int dy = point.getY() - center.getY();
        int dz = point.getZ() - center.getZ();
        int a = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[0]);
        int b = ShapeGeometryUtil.dotDelta(dx, dy, dz, axes[1]);
        return Math.max(
                0,
                (int) Math.round(Math.sqrt(
                        a * (double) a + b * (double) b)));
    }

    private static int spatialRadius(BlockPos center, BlockPos point) {
        int dx = point.getX() - center.getX();
        int dy = point.getY() - center.getY();
        int dz = point.getZ() - center.getZ();
        return Math.max(
                0,
                (int) Math.round(Math.sqrt(
                        dx * (double) dx
                                + dy * (double) dy
                                + dz * (double) dz)));
    }
}
