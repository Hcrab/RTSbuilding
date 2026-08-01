package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 把当前形状交互会话解析为一次可预览或可确认的形状输入。
 *
 * <p>本类负责平面射线交点、会话阶段、盒体高度和脚印微调的确定性换算；
 * 不读取 Minecraft 客户端、配置、插件、窗口或网络，也不拥有会话生命周期。
 * 控制器仍负责取得当前相机射线并决定何时确认或清理会话。</p>
 */
public final class ShapeSessionInputResolver {
    private static final double MIN_RAY_COMPONENT = 1.0E-5D;
    private static final double MAX_PLANE_DISTANCE = 128.0D;

    public static ShapeBuildTypes.Input resolve(
            ShapeBuildTypes.Session session,
            BlockHitResult cursorHit,
            boolean requireReady,
            boolean lineConnected,
            int footprintNudgeA,
            int footprintNudgeB,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        return resolve(
                session,
                cursorHit,
                requireReady,
                false,
                lineConnected,
                footprintNudgeA,
                footprintNudgeB,
                rayOrigin,
                rayDirection);
    }

    /**
     * 解析当前形状输入；垂直直线由调用方显式声明，避免解析器读取界面状态。
     */
    public static ShapeBuildTypes.Input resolve(
            ShapeBuildTypes.Session session,
            BlockHitResult cursorHit,
            boolean requireReady,
            boolean verticalLine,
            boolean lineConnected,
            int footprintNudgeA,
            int footprintNudgeB,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (session == null) {
            return null;
        }
        if (requireReady && session.phase() != ShapeBuildTypes.Phase.READY_CONFIRM) {
            return null;
        }
        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return null;
        }

        if (session.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT) {
            if (requireReady) {
                return null;
            }
            BlockPos pointB = verticalLine && session.shape() == BuildShape.LINE
                    ? resolveVerticalLinePoint(session, cursorHit)
                    : resolvePlanePoint(session, cursorHit, rayOrigin, rayDirection);
            if (!verticalLine) {
                pointB = applyFootprintNudges(
                        session.shape(), session.planeFace(), pointA, pointB,
                        footprintNudgeA, footprintNudgeB);
            }
            int heightOffset = verticalLine && pointB != null
                    ? pointB.getY() - pointA.getY()
                    : 0;
            return input(session, pointA, pointB, heightOffset, lineConnected);
        }

        BlockPos pointB = session.pointB();
        if (pointB == null) {
            return null;
        }
        if (session.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT && requireReady) {
            return null;
        }
        if (!verticalLine) {
            pointB = applyFootprintNudges(
                    session.shape(), session.planeFace(), pointA, pointB,
                    footprintNudgeA, footprintNudgeB);
        }
        return input(session, pointA, pointB, session.boxHeightOffset(), lineConnected);
    }

    /**
     * 垂直直线只沿 Y 轴改变终点；鼠标未产生高度差时给出一格默认预览。
     */
    public static BlockPos resolveVerticalLinePoint(
            ShapeBuildTypes.Session session,
            BlockHitResult cursorHit) {
        BlockPos pointA = session == null ? null : session.pointA();
        if (pointA == null) {
            return cursorHit == null ? null : cursorHit.getBlockPos();
        }
        int offset = session.boxHeightOffset();
        if (offset == 0 && cursorHit != null) {
            offset = cursorHit.getBlockPos().getY() - pointA.getY();
        }
        if (offset == 0) {
            offset = 1;
        }
        return pointA.offset(0, ShapeGeometryUtil.clampShapeOffset(offset), 0);
    }

    public static BlockPos resolvePlanePoint(
            ShapeBuildTypes.Session session,
            BlockHitResult cursorHit,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (session == null) {
            return cursorHit == null ? null : cursorHit.getBlockPos();
        }
        BlockPos pointA = session.pointA();
        if (pointA == null) {
            return cursorHit == null ? null : cursorHit.getBlockPos();
        }
        BuildShape shape = session.shape();
        if (shape == null || shape == BuildShape.BLOCK) {
            return cursorHit == null ? pointA : cursorHit.getBlockPos();
        }
        Direction planeFace = planeFace(shape, session.planeFace());
        if (planeFace == null) {
            return cursorHit == null ? pointA : cursorHit.getBlockPos();
        }
        Vec3 planeHit = intersectPlane(pointA, planeFace, rayOrigin, rayDirection);
        if (planeHit == null && cursorHit != null) {
            planeHit = cursorHit.getLocation();
        }
        return planeHit == null ? pointA : blockPosFromPlaneHit(pointA, planeFace, planeHit);
    }

    static Vec3 intersectPlane(
            BlockPos anchor,
            Direction face,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (anchor == null || face == null || rayOrigin == null || rayDirection == null) {
            return null;
        }
        Vec3 planeAnchor = Vec3.atCenterOf(anchor);
        double planeCoordinate = coordinate(planeAnchor, face.getAxis());
        double originCoordinate = coordinate(rayOrigin, face.getAxis());
        double directionCoordinate = coordinate(rayDirection, face.getAxis());
        if (Math.abs(directionCoordinate) < MIN_RAY_COMPONENT) {
            return null;
        }
        double distance = (planeCoordinate - originCoordinate) / directionCoordinate;
        if (distance <= 0.0D || distance > MAX_PLANE_DISTANCE) {
            return null;
        }
        return rayOrigin.add(rayDirection.scale(distance));
    }

    static BlockPos applyFootprintNudges(
            BuildShape shape,
            Direction face,
            BlockPos pointA,
            BlockPos pointB,
            int footprintNudgeA,
            int footprintNudgeB) {
        if (pointA == null || pointB == null
                || (footprintNudgeA == 0 && footprintNudgeB == 0)
                || shape == null || shape == BuildShape.BLOCK) {
            return pointB;
        }
        Direction axisA;
        Direction axisB;
        if (shape == BuildShape.BOX) {
            axisA = Direction.EAST;
            axisB = Direction.SOUTH;
        } else {
            Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(shape, face);
            if (axes.length < 2) {
                return pointB;
            }
            axisA = axes[0];
            axisB = axes[1];
        }
        int dx = pointB.getX() - pointA.getX();
        int dy = pointB.getY() - pointA.getY();
        int dz = pointB.getZ() - pointA.getZ();
        int nextA = ShapeGeometryUtil.clampShapeOffset(
                ShapeGeometryUtil.dotDelta(dx, dy, dz, axisA) + footprintNudgeA);
        int nextB = ShapeGeometryUtil.clampShapeOffset(
                ShapeGeometryUtil.dotDelta(dx, dy, dz, axisB) + footprintNudgeB);
        return ShapeGeometryUtil.offsetPos(pointA, axisA, nextA, axisB, nextB);
    }

    private static ShapeBuildTypes.Input input(
            ShapeBuildTypes.Session session,
            BlockPos pointA,
            BlockPos pointB,
            int heightOffset,
            boolean lineConnected) {
        return new ShapeBuildTypes.Input(
                session.shape(),
                session.planeFace(),
                session.placementFace(),
                pointA,
                pointB,
                heightOffset,
                lineConnected);
    }

    private static Direction planeFace(BuildShape shape, Direction configured) {
        if (shape == BuildShape.LINE
                || shape == BuildShape.SQUARE
                || shape == BuildShape.WALL
                || shape == BuildShape.BOX) {
            return Direction.UP;
        }
        return configured;
    }

    private static BlockPos blockPosFromPlaneHit(
            BlockPos anchor,
            Direction face,
            Vec3 hit) {
        switch (face.getAxis()) {
            case X:
                return new BlockPos(anchor.getX(), Mth.floor(hit.y), Mth.floor(hit.z));
            case Y:
                return new BlockPos(Mth.floor(hit.x), anchor.getY(), Mth.floor(hit.z));
            case Z:
                return new BlockPos(Mth.floor(hit.x), Mth.floor(hit.y), anchor.getZ());
            default:
                return anchor;
        }
    }

    private static double coordinate(Vec3 value, Direction.Axis axis) {
        switch (axis) {
            case X:
                return value.x;
            case Y:
                return value.y;
            case Z:
                return value.z;
            default:
                return 0.0D;
        }
    }

    private ShapeSessionInputResolver() {
    }
}
