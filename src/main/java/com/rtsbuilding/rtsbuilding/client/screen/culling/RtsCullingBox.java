package com.rtsbuilding.rtsbuilding.client.screen.culling;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 客户端范围剔除盒子，负责保存一个闭区间方块区域。
 *
 * <p>它只描述几何形状，不拥有 UI、渲染刷新或输入状态。这样后续要把剔除区
 * 做成持久化/同步数据时，可以继续复用同一份几何判断。
 */
public record RtsCullingBox(int id, BlockPos min, BlockPos max) {
    private static final double EPSILON = 1.0E-7D;
    private static final int MAX_EDGE = 256;

    public RtsCullingBox {
        BlockPos rawMin = min;
        BlockPos rawMax = max;
        min = new BlockPos(
                Math.min(rawMin.getX(), rawMax.getX()),
                Math.min(rawMin.getY(), rawMax.getY()),
                Math.min(rawMin.getZ(), rawMax.getZ()));
        max = new BlockPos(
                Math.max(rawMin.getX(), rawMax.getX()),
                Math.max(rawMin.getY(), rawMax.getY()),
                Math.max(rawMin.getZ(), rawMax.getZ()));
    }

    public static RtsCullingBox fromDiagonal(int id, BlockPos first, BlockPos second, int heightOffset) {
        int safeHeightOffset = Mth.clamp(heightOffset, -MAX_EDGE + 1, MAX_EDGE - 1);
        BlockPos adjustedSecond = new BlockPos(second.getX(), first.getY() + safeHeightOffset, second.getZ());
        return new RtsCullingBox(id, first, adjustedSecond);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int height() {
        return max.getY() - min.getY() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }

    public AABB asAabb() {
        return new AABB(
                min.getX(), min.getY(), min.getZ(),
                max.getX() + 1.0D, max.getY() + 1.0D, max.getZ() + 1.0D);
    }

    public RtsCullingBox resize(Direction.Axis axis, int delta) {
        if (delta == 0) {
            return this;
        }
        int x = max.getX();
        int y = max.getY();
        int z = max.getZ();
        switch (axis) {
            case X -> x = Mth.clamp(max.getX() + delta, min.getX(), min.getX() + MAX_EDGE - 1);
            case Y -> y = Mth.clamp(max.getY() + delta, min.getY(), min.getY() + MAX_EDGE - 1);
            case Z -> z = Mth.clamp(max.getZ() + delta, min.getZ(), min.getZ() + MAX_EDGE - 1);
        }
        return new RtsCullingBox(id, min, new BlockPos(x, y, z));
    }

    public RtsCullingBox resizeFromPositiveHandle(Direction.Axis axis, int delta) {
        Direction direction = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        return resizeFromHandle(direction, delta);
    }

    public RtsCullingBox resizeFromHandle(Direction direction, int delta) {
        if (delta == 0) {
            return this;
        }
        return switch (direction) {
            case EAST -> resizeEdge(delta, true, width(), min.getX(), max.getX(), Coordinate.X);
            case WEST -> resizeEdge(delta, false, width(), min.getX(), max.getX(), Coordinate.X);
            case UP -> resizeEdge(delta, true, height(), min.getY(), max.getY(), Coordinate.Y);
            case DOWN -> resizeEdge(delta, false, height(), min.getY(), max.getY(), Coordinate.Y);
            case SOUTH -> resizeEdge(delta, true, depth(), min.getZ(), max.getZ(), Coordinate.Z);
            case NORTH -> resizeEdge(delta, false, depth(), min.getZ(), max.getZ(), Coordinate.Z);
        };
    }

    private RtsCullingBox resizeEdge(int delta, boolean positiveFace, int length, int minValue, int maxValue,
            Coordinate coordinate) {
        int newMin = minValue;
        int newMax = maxValue;
        if (positiveFace && delta > 0) {
            newMax = Math.min(minValue + MAX_EDGE - 1, maxValue + delta);
        } else if (positiveFace) {
            int amount = -delta;
            newMax -= Math.min(amount, Math.max(0, length - 1));
        } else if (delta > 0) {
            newMin = Math.max(maxValue - MAX_EDGE + 1, minValue - delta);
        } else {
            int amount = -delta;
            newMin += Math.min(amount, Math.max(0, length - 1));
        }
        return withAxis(coordinate, newMin, newMax);
    }

    private RtsCullingBox withAxis(Coordinate coordinate, int newMin, int newMax) {
        return switch (coordinate) {
            case X -> new RtsCullingBox(id,
                    new BlockPos(newMin, min.getY(), min.getZ()),
                    new BlockPos(newMax, max.getY(), max.getZ()));
            case Y -> new RtsCullingBox(id,
                    new BlockPos(min.getX(), newMin, min.getZ()),
                    new BlockPos(max.getX(), newMax, max.getZ()));
            case Z -> new RtsCullingBox(id,
                    new BlockPos(min.getX(), min.getY(), newMin),
                    new BlockPos(max.getX(), max.getY(), newMax));
        };
    }

    public RayHit rayHit(Vec3 origin, Vec3 direction, double maxDistance) {
        if (origin == null || direction == null || direction.lengthSqr() < EPSILON) {
            return null;
        }
        double[] x = axis(origin.x, direction.x, min.getX(), max.getX() + 1.0D);
        double[] y = axis(origin.y, direction.y, min.getY(), max.getY() + 1.0D);
        double[] z = axis(origin.z, direction.z, min.getZ(), max.getZ() + 1.0D);
        if (x == null || y == null || z == null) {
            return null;
        }
        double enter = Math.max(0.0D, Math.max(x[0], Math.max(y[0], z[0])));
        double exit = Math.min(maxDistance, Math.min(x[1], Math.min(y[1], z[1])));
        if (exit < enter || enter > maxDistance) {
            return null;
        }
        return new RayHit(this, enter, exit);
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

    public record RayHit(RtsCullingBox box, double enterDistance, double exitDistance) {
    }

    private enum Coordinate {
        X,
        Y,
        Z
    }
}
