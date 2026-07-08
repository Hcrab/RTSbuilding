package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 范围剔除盒的世界空间轴向手柄。
 *
 * <p>这个类只描述六个面向箭头的可视几何和命中热区，不负责修改盒子尺寸。
 * 渲染器和输入命中共用这里的 AABB，避免出现玩家看见箭头却无法滚轮选中的错位。</p>
 */
public final class RtsCullingAxisHandle {
    private static final double GAP = 0.10D;
    private static final double SHAFT_LENGTH = 0.58D;
    private static final double HEAD_LENGTH = 0.30D;
    private static final double SHAFT_HALF = 0.055D;
    private static final double HEAD_HALF = 0.18D;
    private static final double EPSILON = 1.0E-7D;

    private RtsCullingAxisHandle() {
    }

    public static List<Handle> handles(RtsCullingBox box) {
        if (box == null) {
            return List.of();
        }
        return handles(box.asAabb());
    }

    public static List<Handle> handles(AABB box) {
        if (box == null) {
            return List.of();
        }
        List<Handle> result = new ArrayList<>(6);
        result.add(handle(box, Direction.EAST));
        result.add(handle(box, Direction.WEST));
        result.add(handle(box, Direction.UP));
        result.add(handle(box, Direction.DOWN));
        result.add(handle(box, Direction.SOUTH));
        result.add(handle(box, Direction.NORTH));
        return result;
    }

    public static Optional<HandleHit> nearestHit(RtsCullingBox box, Vec3 origin, Vec3 direction, double maxDistance) {
        if (box == null || origin == null || direction == null || direction.lengthSqr() < EPSILON) {
            return Optional.empty();
        }
        Vec3 normalized = direction.normalize();
        return handles(box).stream()
                .map(handle -> handle.hit(origin, normalized, maxDistance))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(HandleHit::distance));
    }

    private static Handle handle(AABB box, Direction direction) {
        double minX = box.minX;
        double minY = box.minY;
        double minZ = box.minZ;
        double maxX = box.maxX;
        double maxY = box.maxY;
        double maxZ = box.maxZ;
        double centerX = (minX + maxX) * 0.5D;
        double centerY = (minY + maxY) * 0.5D;
        double centerZ = (minZ + maxZ) * 0.5D;

        return switch (direction) {
            case EAST -> new Handle(
                    direction,
                    new AABB(
                            maxX + GAP, centerY - SHAFT_HALF, centerZ - SHAFT_HALF,
                            maxX + GAP + SHAFT_LENGTH, centerY + SHAFT_HALF, centerZ + SHAFT_HALF),
                    new AABB(
                            maxX + GAP + SHAFT_LENGTH, centerY - HEAD_HALF, centerZ - HEAD_HALF,
                            maxX + GAP + SHAFT_LENGTH + HEAD_LENGTH, centerY + HEAD_HALF, centerZ + HEAD_HALF));
            case WEST -> new Handle(
                    direction,
                    new AABB(
                            minX - GAP - SHAFT_LENGTH, centerY - SHAFT_HALF, centerZ - SHAFT_HALF,
                            minX - GAP, centerY + SHAFT_HALF, centerZ + SHAFT_HALF),
                    new AABB(
                            minX - GAP - SHAFT_LENGTH - HEAD_LENGTH, centerY - HEAD_HALF, centerZ - HEAD_HALF,
                            minX - GAP - SHAFT_LENGTH, centerY + HEAD_HALF, centerZ + HEAD_HALF));
            case UP -> new Handle(
                    direction,
                    new AABB(
                            centerX - SHAFT_HALF, maxY + GAP, centerZ - SHAFT_HALF,
                            centerX + SHAFT_HALF, maxY + GAP + SHAFT_LENGTH, centerZ + SHAFT_HALF),
                    new AABB(
                            centerX - HEAD_HALF, maxY + GAP + SHAFT_LENGTH, centerZ - HEAD_HALF,
                            centerX + HEAD_HALF, maxY + GAP + SHAFT_LENGTH + HEAD_LENGTH, centerZ + HEAD_HALF));
            case DOWN -> new Handle(
                    direction,
                    new AABB(
                            centerX - SHAFT_HALF, minY - GAP - SHAFT_LENGTH, centerZ - SHAFT_HALF,
                            centerX + SHAFT_HALF, minY - GAP, centerZ + SHAFT_HALF),
                    new AABB(
                            centerX - HEAD_HALF, minY - GAP - SHAFT_LENGTH - HEAD_LENGTH, centerZ - HEAD_HALF,
                            centerX + HEAD_HALF, minY - GAP - SHAFT_LENGTH, centerZ + HEAD_HALF));
            case SOUTH -> new Handle(
                    direction,
                    new AABB(
                            centerX - SHAFT_HALF, centerY - SHAFT_HALF, maxZ + GAP,
                            centerX + SHAFT_HALF, centerY + SHAFT_HALF, maxZ + GAP + SHAFT_LENGTH),
                    new AABB(
                            centerX - HEAD_HALF, centerY - HEAD_HALF, maxZ + GAP + SHAFT_LENGTH,
                            centerX + HEAD_HALF, centerY + HEAD_HALF, maxZ + GAP + SHAFT_LENGTH + HEAD_LENGTH));
            case NORTH -> new Handle(
                    direction,
                    new AABB(
                            centerX - SHAFT_HALF, centerY - SHAFT_HALF, minZ - GAP - SHAFT_LENGTH,
                            centerX + SHAFT_HALF, centerY + SHAFT_HALF, minZ - GAP),
                    new AABB(
                            centerX - HEAD_HALF, centerY - HEAD_HALF, minZ - GAP - SHAFT_LENGTH - HEAD_LENGTH,
                            centerX + HEAD_HALF, centerY + HEAD_HALF, minZ - GAP - SHAFT_LENGTH));
        };
    }

    private static Optional<Double> rayHit(AABB box, Vec3 origin, Vec3 direction, double maxDistance) {
        double[] x = axis(origin.x, direction.x, box.minX, box.maxX);
        double[] y = axis(origin.y, direction.y, box.minY, box.maxY);
        double[] z = axis(origin.z, direction.z, box.minZ, box.maxZ);
        if (x == null || y == null || z == null) {
            return Optional.empty();
        }
        double enter = Math.max(0.0D, Math.max(x[0], Math.max(y[0], z[0])));
        double exit = Math.min(maxDistance, Math.min(x[1], Math.min(y[1], z[1])));
        return exit >= enter && enter <= maxDistance ? Optional.of(enter) : Optional.empty();
    }

    private static double[] axis(double origin, double direction, double min, double max) {
        if (Math.abs(direction) < EPSILON) {
            return origin >= min && origin <= max
                    ? new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}
                    : null;
        }
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        return new double[] {Math.min(t1, t2), Math.max(t1, t2)};
    }

    public record Handle(Direction direction, AABB shaft, AABB head) {
        public Direction.Axis axis() {
            return direction.getAxis();
        }

        private Optional<HandleHit> hit(Vec3 origin, Vec3 direction, double maxDistance) {
            Optional<Double> shaftHit = rayHit(shaft, origin, direction, maxDistance);
            Optional<Double> headHit = rayHit(head, origin, direction, maxDistance);
            if (shaftHit.isEmpty()) {
                return headHit.map(distance -> new HandleHit(this.direction, distance));
            }
            if (headHit.isEmpty()) {
                return shaftHit.map(distance -> new HandleHit(this.direction, distance));
            }
            return Optional.of(new HandleHit(this.direction, Math.min(shaftHit.get(), headHit.get())));
        }
    }

    public record HandleHit(Direction direction, double distance) {
    }
}
