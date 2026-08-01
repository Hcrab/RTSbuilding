package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.common.placement.PlacedBlockRotationStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
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

    public boolean select(Level level, BlockPos pos, Direction cameraForward) {
        clear();
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || availableArcs(state, pos, cameraForward).isEmpty()) {
            return false;
        }
        this.targetPos = pos.immutable();
        this.targetBlock = state.getBlock();
        return true;
    }

    public List<ArcHandle> arcs(Level level, Direction cameraForward) {
        if (!targetStillMatches(level)) {
            return List.of();
        }
        return availableArcs(
                level.getBlockState(this.targetPos),
                this.targetPos,
                horizontal(cameraForward));
    }

    public void updateHover(
            Level level,
            Vec3 origin,
            Vec3 direction,
            Direction cameraForward) {
        if (!targetStillMatches(level)) {
            clear();
            return;
        }
        this.hoveredGesture = nearestHit(
                arcs(level, cameraForward), origin, direction, MAX_HIT_DISTANCE);
    }

    public PlacedBlockRotationGesture hitGesture(
            Level level,
            Vec3 origin,
            Vec3 direction,
            Direction cameraForward) {
        updateHover(level, origin, direction, cameraForward);
        return this.hoveredGesture;
    }

    public boolean targetStillMatches(Level level) {
        return this.targetPos != null
                && level != null
                && level.hasChunkAt(this.targetPos)
                && level.getBlockState(this.targetPos).is(this.targetBlock);
    }

    public void clear() {
        this.targetPos = null;
        this.targetBlock = null;
        this.hoveredGesture = null;
    }

    private static List<ArcHandle> availableArcs(
            BlockState state,
            BlockPos pos,
            Direction cameraForward) {
        Direction forward = horizontal(cameraForward);
        List<ArcHandle> result = new ArrayList<>(4);
        for (PlacedBlockRotationGesture gesture : PlacedBlockRotationGesture.values()) {
            if (PlacedBlockRotationStep.supports(
                    state,
                    gesture.axisDirection(forward),
                    gesture.quarterTurns())) {
                result.add(createArc(pos, forward, gesture));
            }
        }
        return List.copyOf(result);
    }

    private static ArcHandle createArc(
            BlockPos pos,
            Direction cameraForward,
            PlacedBlockRotationGesture gesture) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 forward = directionVector(cameraForward);
        Vec3 near = forward.scale(-1.0D);
        Vec3 right = directionVector(PlacedBlockRotationGesture.rightOf(cameraForward));
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);

        boolean horizontal = gesture == PlacedBlockRotationGesture.HORIZONTAL_LEFT
                || gesture == PlacedBlockRotationGesture.HORIZONTAL_RIGHT;
        Vec3 planeNormal = horizontal ? up : right;
        Vec3 radialBase = near;
        Vec3 angularBase = horizontal ? right : up;
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

        Vec3 arcCenter = horizontal
                ? center.add(0.0D, 0.68D, 0.0D)
                : center;
        List<Vec3> points = new ArrayList<>(ARC_SEGMENTS + 1);
        for (int i = 0; i <= ARC_SEGMENTS; i++) {
            double t = i / (double) ARC_SEGMENTS;
            double angle = Math.toRadians(startDegrees + (endDegrees - startDegrees) * t);
            Vec3 radial = radialBase.scale(Math.cos(angle))
                    .add(angularBase.scale(Math.sin(angle)));
            points.add(arcCenter.add(radial.scale(ARC_RADIUS)));
        }
        return new ArcHandle(
                gesture,
                arcCenter,
                planeNormal,
                List.copyOf(points));
    }

    private static PlacedBlockRotationGesture nearestHit(
            List<ArcHandle> arcs,
            Vec3 origin,
            Vec3 direction,
            double maxDistance) {
        if (origin == null || direction == null || direction.lengthSqr() < 1.0E-8D) {
            return null;
        }
        Vec3 end = origin.add(direction.normalize().scale(maxDistance));
        PlacedBlockRotationGesture nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (ArcHandle arc : arcs) {
            for (Vec3 point : arc.points()) {
                AABB hitBox = new AABB(point, point).inflate(HIT_RADIUS);
                var hit = hitBox.clip(origin, end);
                if (hit.isEmpty()) {
                    continue;
                }
                double distance = hit.get().distanceToSqr(origin);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = arc.gesture();
                }
            }
        }
        return nearest;
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(
                direction.getStepX(),
                direction.getStepY(),
                direction.getStepZ());
    }

    private static Direction horizontal(Direction direction) {
        return direction != null && direction.getAxis().isHorizontal()
                ? direction
                : Direction.NORTH;
    }

    public record ArcHandle(
            PlacedBlockRotationGesture gesture,
            Vec3 center,
            Vec3 planeNormal,
            List<Vec3> points) {
    }
}
