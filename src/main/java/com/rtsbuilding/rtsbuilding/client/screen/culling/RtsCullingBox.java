package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Objects;

/** 只描述客户端范围剔除盒的闭区间方块几何，不持有 UI 或世界状态。 */
public final class RtsCullingBox {
    private static final double EPSILON = 1.0E-7D;
    private static final int MAX_EDGE = 256;

    private final int id;
    private final BlockPos min;
    private final BlockPos max;

    public RtsCullingBox(int id, BlockPos min, BlockPos max) {
        BlockPos rawMin = Objects.requireNonNull(min, "min");
        BlockPos rawMax = Objects.requireNonNull(max, "max");
        this.id = id;
        this.min = new BlockPos(Math.min(rawMin.getX(), rawMax.getX()),
                Math.min(rawMin.getY(), rawMax.getY()), Math.min(rawMin.getZ(), rawMax.getZ()));
        this.max = new BlockPos(Math.max(rawMin.getX(), rawMax.getX()),
                Math.max(rawMin.getY(), rawMax.getY()), Math.max(rawMin.getZ(), rawMax.getZ()));
    }

    public int id() { return this.id; }
    public BlockPos min() { return this.min; }
    public BlockPos max() { return this.max; }

    public static RtsCullingBox fromDiagonal(int id, BlockPos first, BlockPos second, int heightOffset) {
        int offset = MathHelper.clamp(heightOffset, -MAX_EDGE + 1, MAX_EDGE - 1);
        return new RtsCullingBox(id, first,
                new BlockPos(second.getX(), first.getY() + offset, second.getZ()));
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public int width() { return max.getX() - min.getX() + 1; }
    public int height() { return max.getY() - min.getY() + 1; }
    public int depth() { return max.getZ() - min.getZ() + 1; }

    public AxisAlignedBB asAabb() {
        return new AxisAlignedBB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    public RtsCullingBox resize(EnumFacing.Axis axis, int delta) {
        if (delta == 0) return this;
        int x = max.getX();
        int y = max.getY();
        int z = max.getZ();
        switch (axis) {
            case X: x = MathHelper.clamp(x + delta, min.getX(), min.getX() + MAX_EDGE - 1); break;
            case Y: y = MathHelper.clamp(y + delta, min.getY(), min.getY() + MAX_EDGE - 1); break;
            case Z: z = MathHelper.clamp(z + delta, min.getZ(), min.getZ() + MAX_EDGE - 1); break;
            default: throw new AssertionError(axis);
        }
        return new RtsCullingBox(id, min, new BlockPos(x, y, z));
    }

    public RtsCullingBox resizeFromPositiveHandle(EnumFacing.Axis axis, int delta) {
        switch (axis) {
            case X: return resizeFromHandle(EnumFacing.EAST, delta);
            case Y: return resizeFromHandle(EnumFacing.UP, delta);
            case Z: return resizeFromHandle(EnumFacing.SOUTH, delta);
            default: throw new AssertionError(axis);
        }
    }

    public RtsCullingBox resizeFromHandle(EnumFacing direction, int delta) {
        if (delta == 0) return this;
        switch (direction) {
            case EAST: return resizeEdge(delta, true, width(), min.getX(), max.getX(), Coordinate.X);
            case WEST: return resizeEdge(delta, false, width(), min.getX(), max.getX(), Coordinate.X);
            case UP: return resizeEdge(delta, true, height(), min.getY(), max.getY(), Coordinate.Y);
            case DOWN: return resizeEdge(delta, false, height(), min.getY(), max.getY(), Coordinate.Y);
            case SOUTH: return resizeEdge(delta, true, depth(), min.getZ(), max.getZ(), Coordinate.Z);
            case NORTH: return resizeEdge(delta, false, depth(), min.getZ(), max.getZ(), Coordinate.Z);
            default: throw new AssertionError(direction);
        }
    }

    private RtsCullingBox resizeEdge(int delta, boolean positive, int length,
            int minValue, int maxValue, Coordinate coordinate) {
        int newMin = minValue;
        int newMax = maxValue;
        if (positive && delta > 0) newMax = Math.min(minValue + MAX_EDGE - 1, maxValue + delta);
        else if (positive) newMax -= Math.min(-delta, Math.max(0, length - 1));
        else if (delta > 0) newMin = Math.max(maxValue - MAX_EDGE + 1, minValue - delta);
        else newMin += Math.min(-delta, Math.max(0, length - 1));
        return withAxis(coordinate, newMin, newMax);
    }

    private RtsCullingBox withAxis(Coordinate coordinate, int newMin, int newMax) {
        switch (coordinate) {
            case X: return new RtsCullingBox(id,
                    new BlockPos(newMin, min.getY(), min.getZ()), new BlockPos(newMax, max.getY(), max.getZ()));
            case Y: return new RtsCullingBox(id,
                    new BlockPos(min.getX(), newMin, min.getZ()), new BlockPos(max.getX(), newMax, max.getZ()));
            case Z: return new RtsCullingBox(id,
                    new BlockPos(min.getX(), min.getY(), newMin), new BlockPos(max.getX(), max.getY(), newMax));
            default: throw new AssertionError(coordinate);
        }
    }

    public RayHit rayHit(Vec3d origin, Vec3d direction, double maxDistance) {
        if (origin == null || direction == null || direction.lengthSquared() < EPSILON) return null;
        double[] x = axis(origin.x, direction.x, min.getX(), max.getX() + 1.0D);
        double[] y = axis(origin.y, direction.y, min.getY(), max.getY() + 1.0D);
        double[] z = axis(origin.z, direction.z, min.getZ(), max.getZ() + 1.0D);
        if (x == null || y == null || z == null) return null;
        double enter = Math.max(0.0D, Math.max(x[0], Math.max(y[0], z[0])));
        double exit = Math.min(maxDistance, Math.min(x[1], Math.min(y[1], z[1])));
        return exit < enter || enter > maxDistance ? null : new RayHit(this, enter, exit);
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

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RtsCullingBox)) return false;
        RtsCullingBox that = (RtsCullingBox) other;
        return id == that.id && min.equals(that.min) && max.equals(that.max);
    }
    @Override public int hashCode() { return Objects.hash(id, min, max); }
    @Override public String toString() { return "RtsCullingBox[id=" + id + ", min=" + min + ", max=" + max + "]"; }

    public static final class RayHit {
        private final RtsCullingBox box;
        private final double enterDistance;
        private final double exitDistance;
        public RayHit(RtsCullingBox box, double enterDistance, double exitDistance) {
            this.box = box; this.enterDistance = enterDistance; this.exitDistance = exitDistance;
        }
        public RtsCullingBox box() { return box; }
        public double enterDistance() { return enterDistance; }
        public double exitDistance() { return exitDistance; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RayHit)) return false;
            RayHit that = (RayHit) other;
            return Double.compare(enterDistance, that.enterDistance) == 0
                    && Double.compare(exitDistance, that.exitDistance) == 0 && Objects.equals(box, that.box);
        }
        @Override public int hashCode() { return Objects.hash(box, enterDistance, exitDistance); }
        @Override public String toString() { return "RayHit[box=" + box + ", enterDistance=" + enterDistance
                + ", exitDistance=" + exitDistance + "]"; }
    }

    private enum Coordinate { X, Y, Z }
}
