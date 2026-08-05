package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.AxisAlignedBB;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 六个面向箭头的可见几何和射线热区；不负责改变盒子尺寸。 */
public final class RtsCullingAxisHandle {
    private static final double GAP = 0.10D;
    private static final double SHAFT_LENGTH = 0.58D;
    private static final double HEAD_LENGTH = 0.30D;
    private static final double SHAFT_HALF = 0.055D;
    private static final double HEAD_HALF = 0.18D;
    private static final double EPSILON = 1.0E-7D;

    private RtsCullingAxisHandle() { }

    public static List<Handle> handles(RtsCullingBox box) {
        return box == null ? Collections.<Handle>emptyList() : handles(box.asAabb());
    }

    public static List<Handle> handles(AxisAlignedBB box) { return handles(box, null); }

    public static List<Handle> handles(AxisAlignedBB box, Set<EnumFacing> allowedDirections) {
        if (box == null) return Collections.emptyList();
        List<Handle> result = new ArrayList<Handle>(6);
        add(result, box, EnumFacing.EAST, allowedDirections);
        add(result, box, EnumFacing.WEST, allowedDirections);
        add(result, box, EnumFacing.UP, allowedDirections);
        add(result, box, EnumFacing.DOWN, allowedDirections);
        add(result, box, EnumFacing.SOUTH, allowedDirections);
        add(result, box, EnumFacing.NORTH, allowedDirections);
        return result;
    }

    public static Optional<HandleHit> nearestHit(RtsCullingBox box, Vec3d origin,
            Vec3d direction, double maxDistance) {
        return nearestHit(box, origin, direction, maxDistance, null);
    }

    public static Optional<HandleHit> nearestHit(RtsCullingBox box, Vec3d origin,
            Vec3d direction, double maxDistance, Set<EnumFacing> allowedDirections) {
        return box == null ? Optional.<HandleHit>empty()
                : nearestHit(box.asAabb(), origin, direction, maxDistance, allowedDirections);
    }

    public static Optional<HandleHit> nearestHit(AxisAlignedBB box, Vec3d origin,
            Vec3d direction, double maxDistance, Set<EnumFacing> allowedDirections) {
        if (box == null || origin == null || direction == null
                || direction.lengthSquared() < EPSILON || maxDistance < 0.0D) {
            return Optional.empty();
        }
        Vec3d normalized = direction.normalize();
        HandleHit nearest = null;
        for (Handle handle : handles(box, allowedDirections)) {
            Optional<HandleHit> hit = handle.hit(origin, normalized, maxDistance);
            if (hit.isPresent() && (nearest == null || hit.get().distance() < nearest.distance())) {
                nearest = hit.get();
            }
        }
        return Optional.ofNullable(nearest);
    }

    private static void add(List<Handle> result, AxisAlignedBB box, EnumFacing direction,
            Set<EnumFacing> allowed) {
        if (allowed == null || allowed.contains(direction)) result.add(handle(box, direction));
    }

    private static Handle handle(AxisAlignedBB box, EnumFacing direction) {
        double cx = (box.minX + box.maxX) * 0.5D;
        double cy = (box.minY + box.maxY) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        switch (direction) {
            case EAST: return new Handle(direction,
                    new AxisAlignedBB(box.maxX + GAP, cy - SHAFT_HALF, cz - SHAFT_HALF,
                            box.maxX + GAP + SHAFT_LENGTH, cy + SHAFT_HALF, cz + SHAFT_HALF),
                    new AxisAlignedBB(box.maxX + GAP + SHAFT_LENGTH, cy - HEAD_HALF, cz - HEAD_HALF,
                            box.maxX + GAP + SHAFT_LENGTH + HEAD_LENGTH, cy + HEAD_HALF, cz + HEAD_HALF));
            case WEST: return new Handle(direction,
                    new AxisAlignedBB(box.minX - GAP - SHAFT_LENGTH, cy - SHAFT_HALF, cz - SHAFT_HALF,
                            box.minX - GAP, cy + SHAFT_HALF, cz + SHAFT_HALF),
                    new AxisAlignedBB(box.minX - GAP - SHAFT_LENGTH - HEAD_LENGTH, cy - HEAD_HALF, cz - HEAD_HALF,
                            box.minX - GAP - SHAFT_LENGTH, cy + HEAD_HALF, cz + HEAD_HALF));
            case UP: return new Handle(direction,
                    new AxisAlignedBB(cx - SHAFT_HALF, box.maxY + GAP, cz - SHAFT_HALF,
                            cx + SHAFT_HALF, box.maxY + GAP + SHAFT_LENGTH, cz + SHAFT_HALF),
                    new AxisAlignedBB(cx - HEAD_HALF, box.maxY + GAP + SHAFT_LENGTH, cz - HEAD_HALF,
                            cx + HEAD_HALF, box.maxY + GAP + SHAFT_LENGTH + HEAD_LENGTH, cz + HEAD_HALF));
            case DOWN: return new Handle(direction,
                    new AxisAlignedBB(cx - SHAFT_HALF, box.minY - GAP - SHAFT_LENGTH, cz - SHAFT_HALF,
                            cx + SHAFT_HALF, box.minY - GAP, cz + SHAFT_HALF),
                    new AxisAlignedBB(cx - HEAD_HALF, box.minY - GAP - SHAFT_LENGTH - HEAD_LENGTH, cz - HEAD_HALF,
                            cx + HEAD_HALF, box.minY - GAP - SHAFT_LENGTH, cz + HEAD_HALF));
            case SOUTH: return new Handle(direction,
                    new AxisAlignedBB(cx - SHAFT_HALF, cy - SHAFT_HALF, box.maxZ + GAP,
                            cx + SHAFT_HALF, cy + SHAFT_HALF, box.maxZ + GAP + SHAFT_LENGTH),
                    new AxisAlignedBB(cx - HEAD_HALF, cy - HEAD_HALF, box.maxZ + GAP + SHAFT_LENGTH,
                            cx + HEAD_HALF, cy + HEAD_HALF, box.maxZ + GAP + SHAFT_LENGTH + HEAD_LENGTH));
            case NORTH: return new Handle(direction,
                    new AxisAlignedBB(cx - SHAFT_HALF, cy - SHAFT_HALF, box.minZ - GAP - SHAFT_LENGTH,
                            cx + SHAFT_HALF, cy + SHAFT_HALF, box.minZ - GAP),
                    new AxisAlignedBB(cx - HEAD_HALF, cy - HEAD_HALF, box.minZ - GAP - SHAFT_LENGTH - HEAD_LENGTH,
                            cx + HEAD_HALF, cy + HEAD_HALF, box.minZ - GAP - SHAFT_LENGTH));
            default: throw new AssertionError(direction);
        }
    }

    private static Optional<Double> rayHit(AxisAlignedBB box, Vec3d origin,
            Vec3d direction, double maxDistance) {
        double[] x = axis(origin.x, direction.x, box.minX, box.maxX);
        double[] y = axis(origin.y, direction.y, box.minY, box.maxY);
        double[] z = axis(origin.z, direction.z, box.minZ, box.maxZ);
        if (x == null || y == null || z == null) return Optional.empty();
        double enter = Math.max(0.0D, Math.max(x[0], Math.max(y[0], z[0])));
        double exit = Math.min(maxDistance, Math.min(x[1], Math.min(y[1], z[1])));
        return exit >= enter && enter <= maxDistance ? Optional.of(enter) : Optional.<Double>empty();
    }

    private static double[] axis(double origin, double direction, double min, double max) {
        if (Math.abs(direction) < EPSILON) {
            return origin >= min && origin <= max
                    ? new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY} : null;
        }
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        return new double[] {Math.min(t1, t2), Math.max(t1, t2)};
    }

    public static final class Handle {
        private final EnumFacing direction;
        private final AxisAlignedBB shaft;
        private final AxisAlignedBB head;
        public Handle(EnumFacing direction, AxisAlignedBB shaft, AxisAlignedBB head) {
            this.direction = direction; this.shaft = shaft; this.head = head;
        }
        public EnumFacing direction() { return direction; }
        public AxisAlignedBB shaft() { return shaft; }
        public AxisAlignedBB head() { return head; }
        public EnumFacing.Axis axis() { return direction.getAxis(); }
        private Optional<HandleHit> hit(Vec3d origin, Vec3d direction, double maxDistance) {
            Optional<Double> shaftHit = rayHit(shaft, origin, direction, maxDistance);
            Optional<Double> headHit = rayHit(head, origin, direction, maxDistance);
            if (!shaftHit.isPresent()) return headHit.isPresent()
                    ? Optional.of(new HandleHit(this.direction, headHit.get())) : Optional.<HandleHit>empty();
            if (!headHit.isPresent()) return Optional.of(new HandleHit(this.direction, shaftHit.get()));
            return Optional.of(new HandleHit(this.direction, Math.min(shaftHit.get(), headHit.get())));
        }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Handle)) return false;
            Handle that = (Handle) other;
            return direction == that.direction && Objects.equals(shaft, that.shaft) && Objects.equals(head, that.head);
        }
        @Override public int hashCode() { return Objects.hash(direction, shaft, head); }
        @Override public String toString() { return "Handle[direction=" + direction + ", shaft=" + shaft + ", head=" + head + "]"; }
    }

    public static final class HandleHit {
        private final EnumFacing direction;
        private final double distance;
        public HandleHit(EnumFacing direction, double distance) { this.direction = direction; this.distance = distance; }
        public EnumFacing direction() { return direction; }
        public double distance() { return distance; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof HandleHit)) return false;
            HandleHit that = (HandleHit) other;
            return direction == that.direction && Double.compare(distance, that.distance) == 0;
        }
        @Override public int hashCode() { return Objects.hash(direction, distance); }
        @Override public String toString() { return "HandleHit[direction=" + direction + ", distance=" + distance + "]"; }
    }
}
