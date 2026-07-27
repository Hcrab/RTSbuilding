package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.common.placement.PlacedBlockRotationStep;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 世界空间增量旋转 gizmo 的目标、曲线几何和射线命中。
 *
 * <p>四个按钮是摄像机相对的左、右、上、下小圆弧。渲染和点击共享同一组采样点，
 * 避免出现看得见却点不到的错位。本类不发送网络请求，也不接管右键相机拖拽。</p>
 */
public final class PlacedBlockRotationHandles {
    private static final double MAX_HIT_DISTANCE = 128.0D;
    private static final double ARC_RADIUS = 1.02D;
    private static final double HIT_RADIUS = 0.15D;
    private static final int ARC_SEGMENTS = 14;

    private BlockPos targetPos;
    private Block targetBlock;
    private PlacedBlockRotationGesture hoveredGesture;

    public boolean hasTarget() {
        return this.targetPos != null;
    }

    public BlockPos targetPos() {
        return this.targetPos;
    }

    public PlacedBlockRotationGesture hoveredGesture() {
        return this.hoveredGesture;
    }

    public boolean select(World world, BlockPos pos, EnumFacing cameraForward) {
        clear();
        if (world == null || pos == null || !world.isBlockLoaded(pos, false)) {
            return false;
        }
        IBlockState state = world.getBlockState(pos);
        if (state == null || state.getBlock() == Blocks.AIR
                || availableArcs(state, pos, cameraForward).isEmpty()) {
            return false;
        }
        this.targetPos = pos.toImmutable();
        this.targetBlock = state.getBlock();
        return true;
    }

    public List<ArcHandle> arcs(World world, EnumFacing cameraForward) {
        if (!targetStillMatches(world)) {
            return Collections.emptyList();
        }
        return availableArcs(
                world.getBlockState(this.targetPos),
                this.targetPos,
                horizontal(cameraForward));
    }

    public void updateHover(
            World world,
            Vec3d origin,
            Vec3d direction,
            EnumFacing cameraForward) {
        if (!targetStillMatches(world)) {
            clear();
            return;
        }
        this.hoveredGesture = nearestHit(
                arcs(world, cameraForward), origin, direction, MAX_HIT_DISTANCE);
    }

    public PlacedBlockRotationGesture hitGesture(
            World world,
            Vec3d origin,
            Vec3d direction,
            EnumFacing cameraForward) {
        updateHover(world, origin, direction, cameraForward);
        return this.hoveredGesture;
    }

    /**
     * 只比较目标位置上的方块类型，与主线行为一致；方块状态发生合法旋转后目标仍保持选中。
     */
    public boolean targetStillMatches(World world) {
        return this.targetPos != null
                && world != null
                && world.isBlockLoaded(this.targetPos, false)
                && world.getBlockState(this.targetPos).getBlock() == this.targetBlock;
    }

    public void clear() {
        this.targetPos = null;
        this.targetBlock = null;
        this.hoveredGesture = null;
    }

    private static List<ArcHandle> availableArcs(
            IBlockState state,
            BlockPos pos,
            EnumFacing cameraForward) {
        EnumFacing forward = horizontal(cameraForward);
        List<ArcHandle> result = new ArrayList<ArcHandle>(4);
        for (PlacedBlockRotationGesture gesture : PlacedBlockRotationGesture.values()) {
            if (PlacedBlockRotationStep.supports(
                    state,
                    gesture.axisDirection(forward),
                    gesture.quarterTurns())) {
                result.add(createArc(pos, forward, gesture));
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static ArcHandle createArc(
            BlockPos pos,
            EnumFacing cameraForward,
            PlacedBlockRotationGesture gesture) {
        Vec3d center = new Vec3d(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D);
        Vec3d forward = directionVector(cameraForward);
        Vec3d near = forward.scale(-1.0D);
        Vec3d right = directionVector(PlacedBlockRotationGesture.rightOf(cameraForward));
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);

        boolean horizontal = gesture == PlacedBlockRotationGesture.HORIZONTAL_LEFT
                || gesture == PlacedBlockRotationGesture.HORIZONTAL_RIGHT;
        Vec3d planeNormal = horizontal ? up : right;
        Vec3d radialBase = near;
        Vec3d angularBase = horizontal ? right : up;
        double startDegrees;
        double endDegrees;
        if (gesture == PlacedBlockRotationGesture.HORIZONTAL_RIGHT
                || gesture == PlacedBlockRotationGesture.VERTICAL_UP) {
            startDegrees = 8.0D;
            endDegrees = 74.0D;
        } else {
            startDegrees = -8.0D;
            endDegrees = -74.0D;
        }

        Vec3d arcCenter = horizontal
                ? center.add(new Vec3d(0.0D, 0.68D, 0.0D))
                : center;
        List<Vec3d> points = new ArrayList<Vec3d>(ARC_SEGMENTS + 1);
        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            double t = i / (double) ARC_SEGMENTS;
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * t);
            Vec3d radial = radialBase.scale(Math.cos(angle))
                    .add(angularBase.scale(Math.sin(angle)));
            points.add(arcCenter.add(radial.scale(ARC_RADIUS)));
        }
        return new ArcHandle(
                gesture,
                arcCenter,
                planeNormal,
                Collections.unmodifiableList(points));
    }

    private static PlacedBlockRotationGesture nearestHit(
            List<ArcHandle> arcs,
            Vec3d origin,
            Vec3d direction,
            double maxDistance) {
        if (origin == null || direction == null || direction.lengthSquared() < 1.0E-8D) {
            return null;
        }
        Vec3d end = origin.add(direction.normalize().scale(maxDistance));
        PlacedBlockRotationGesture nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ArcHandle arc : arcs) {
            for (Vec3d point : arc.points()) {
                AxisAlignedBB hitBox = new AxisAlignedBB(
                        point.x - HIT_RADIUS,
                        point.y - HIT_RADIUS,
                        point.z - HIT_RADIUS,
                        point.x + HIT_RADIUS,
                        point.y + HIT_RADIUS,
                        point.z + HIT_RADIUS);
                RayTraceResult hit = hitBox.calculateIntercept(origin, end);
                if (hit == null || hit.hitVec == null) {
                    continue;
                }
                double distance = hit.hitVec.squareDistanceTo(origin);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = arc.gesture();
                }
            }
        }
        return nearest;
    }

    private static Vec3d directionVector(EnumFacing direction) {
        return new Vec3d(
                direction.getXOffset(),
                direction.getYOffset(),
                direction.getZOffset());
    }

    private static EnumFacing horizontal(EnumFacing direction) {
        return direction != null && direction.getAxis() != EnumFacing.Axis.Y
                ? direction
                : EnumFacing.NORTH;
    }

    /** 渲染器读取的不可变圆弧快照；方法名保持与主线数据载体访问器一致。 */
    public static final class ArcHandle {
        private final PlacedBlockRotationGesture gesture;
        private final Vec3d center;
        private final Vec3d planeNormal;
        private final List<Vec3d> points;

        private ArcHandle(
                PlacedBlockRotationGesture gesture,
                Vec3d center,
                Vec3d planeNormal,
                List<Vec3d> points) {
            this.gesture = gesture;
            this.center = center;
            this.planeNormal = planeNormal;
            this.points = points;
        }

        public PlacedBlockRotationGesture gesture() {
            return this.gesture;
        }

        public Vec3d center() {
            return this.center;
        }

        public Vec3d planeNormal() {
            return this.planeNormal;
        }

        public List<Vec3d> points() {
            return this.points;
        }
    }
}
