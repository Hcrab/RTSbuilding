package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

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
            RayTraceResult cursorHit,
            boolean requireReady,
            boolean lineConnected,
            int footprintNudgeA,
            int footprintNudgeB,
            Vec3d rayOrigin,
            Vec3d rayDirection) {
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
            RayTraceResult cursorHit,
            boolean requireReady,
            boolean verticalLine,
            boolean lineConnected,
            int footprintNudgeA,
            int footprintNudgeB,
            Vec3d rayOrigin,
            Vec3d rayDirection) {
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
            RayTraceResult cursorHit) {
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
        return pointA.add(0, ShapeGeometryUtil.clampShapeOffset(offset), 0);
    }

    public static BlockPos resolvePlanePoint(
            ShapeBuildTypes.Session session,
            RayTraceResult cursorHit,
            Vec3d rayOrigin,
            Vec3d rayDirection) {
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
        EnumFacing planeFace = planeFace(shape, session.planeFace());
        if (planeFace == null) {
            return cursorHit == null ? pointA : cursorHit.getBlockPos();
        }
        Vec3d planeHit = intersectPlane(pointA, planeFace, rayOrigin, rayDirection);
        if (planeHit == null && cursorHit != null) {
            planeHit = cursorHit.hitVec;
        }
        return planeHit == null ? pointA : blockPosFromPlaneHit(pointA, planeFace, planeHit);
    }

    static Vec3d intersectPlane(
            BlockPos anchor,
            EnumFacing face,
            Vec3d rayOrigin,
            Vec3d rayDirection) {
        if (anchor == null || face == null || rayOrigin == null || rayDirection == null) {
            return null;
        }
        Vec3d planeAnchor = new Vec3d(anchor).add(0.5D, 0.5D, 0.5D);
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
            EnumFacing face,
            BlockPos pointA,
            BlockPos pointB,
            int footprintNudgeA,
            int footprintNudgeB) {
        if (pointA == null || pointB == null
                || (footprintNudgeA == 0 && footprintNudgeB == 0)
                || shape == null || shape == BuildShape.BLOCK) {
            return pointB;
        }
        EnumFacing axisA;
        EnumFacing axisB;
        if (shape == BuildShape.BOX) {
            axisA = EnumFacing.EAST;
            axisB = EnumFacing.SOUTH;
        } else {
            EnumFacing[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(shape, face);
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

    private static EnumFacing planeFace(BuildShape shape, EnumFacing configured) {
        if (shape == BuildShape.LINE
                || shape == BuildShape.SQUARE
                || shape == BuildShape.WALL
                || shape == BuildShape.BOX) {
            return EnumFacing.UP;
        }
        return configured;
    }

    private static BlockPos blockPosFromPlaneHit(
            BlockPos anchor,
            EnumFacing face,
            Vec3d hit) {
        switch (face.getAxis()) {
            case X:
                return new BlockPos(anchor.getX(), MathHelper.floor(hit.y), MathHelper.floor(hit.z));
            case Y:
                return new BlockPos(MathHelper.floor(hit.x), anchor.getY(), MathHelper.floor(hit.z));
            case Z:
                return new BlockPos(MathHelper.floor(hit.x), MathHelper.floor(hit.y), anchor.getZ());
            default:
                return anchor;
        }
    }

    private static double coordinate(Vec3d value, EnumFacing.Axis axis) {
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
