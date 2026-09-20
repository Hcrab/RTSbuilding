package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryPlaneSupport.*;

/** 高级包围盒形状生成器；只负责椭圆、椭球、墙/盒边界，不负责计划状态或 UI。 */
final class ShapeGeometryAdvancedGenerator {

// ======================== 坐标/向量工具 ========================

    /**
     * 将高级选区分派到对应的几何 owner；本类不负责计划状态或 UI。
     * BLOCK/LINE 不进入高级选区，但保留盒体兜底以维持显式入口的确定性。
     */
    static void addAdvancedShapeTargets(
            Set<BlockPos> targets,
            BuildShape shape,
            RtsCullingBox box,
            ShapeFillMode fillMode,
            Direction planeFace) {
        switch (shape) {
            case SQUARE -> addAdvancedSquareTargets(targets, box, fillMode);
            case WALL -> addAdvancedWallTargets(targets, box, fillMode);
            case CIRCLE -> addAdvancedEllipseTargets(targets, box, fillMode, planeFace);
            case CYLINDER -> addAdvancedEllipticCylinderTargets(targets, box, fillMode, planeFace);
            case BALL -> addAdvancedEllipsoidTargets(targets, box, fillMode);
            case BOX, BLOCK, LINE -> addAdvancedBoxLikeTargets(targets, box, fillMode);
        }
    }

    /** 生成高级平面方形；实心先按面积预检，空心只走边界。 */
    static void addAdvancedSquareTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        long minX = box.min().getX();
        long maxX = box.max().getX();
        long minZ = box.min().getZ();
        long maxZ = box.max().getZ();
        long width = span(toInt(minX), toInt(maxX));
        long depth = span(toInt(minZ), toInt(maxZ));
        long expected = fillMode == ShapeFillMode.FILL
                ? saturatedProduct(width, 1L, depth)
                : perimeterCount(width, depth);
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        long y = box.min().getY();
        if (fillMode == ShapeFillMode.FILL) {
            for (long x = minX; x <= maxX; x++) {
                for (long z = minZ; z <= maxZ; z++) {
                    targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
                }
            }
            return;
        }
        addAbsoluteRectangleBoundary(targets, minX, maxX, minZ, maxZ, y);
    }

    /** 生成高级长方体；HOLLOW/SKELETON 不构造完整体积集。 */
    static void addAdvancedBoxLikeTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        long minX = box.min().getX();
        long maxX = box.max().getX();
        long minY = box.min().getY();
        long maxY = box.max().getY();
        long minZ = box.min().getZ();
        long maxZ = box.max().getZ();
        long width = span(toInt(minX), toInt(maxX));
        long height = span(toInt(minY), toInt(maxY));
        long depth = span(toInt(minZ), toInt(maxZ));
        long expected = boxTargetCount(width, height, depth, fillMode);
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        for (long y = minY; y <= maxY; y++) {
            if (fillMode == ShapeFillMode.FILL) {
                for (long x = minX; x <= maxX; x++) {
                    for (long z = minZ; z <= maxZ; z++) {
                        targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
                    }
                }
            } else if (fillMode == ShapeFillMode.HOLLOW) {
                addAbsoluteHollowLayer(targets, minX, maxX, minZ, maxZ, y, y == minY || y == maxY);
            } else {
                addAbsoluteSkeletonLayer(targets, minX, maxX, minZ, maxZ, y, y == minY || y == maxY);
            }
        }
    }

    /** 高级墙面只沿较长的水平轴展开，边界模式直接枚举边线。 */
    static void addAdvancedWallTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        long width = span(box.min().getX(), box.max().getX());
        long depth = span(box.min().getZ(), box.max().getZ());
        boolean useX = width >= depth;
        long horizontalMin = useX ? box.min().getX() : box.min().getZ();
        long horizontalMax = useX ? box.max().getX() : box.max().getZ();
        long minY = box.min().getY();
        long maxY = box.max().getY();
        long horizontalLength = span(toInt(horizontalMin), toInt(horizontalMax));
        long height = span(toInt(minY), toInt(maxY));
        long expected = fillMode == ShapeFillMode.FILL
                ? saturatedProduct(horizontalLength, height, 1L)
                : perimeterCount(horizontalLength, height);
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        long fixed = useX ? box.min().getZ() : box.min().getX();
        if (fillMode != ShapeFillMode.FILL) {
            for (long horizontal = horizontalMin; horizontal <= horizontalMax; horizontal++) {
                targets.add(useX
                        ? new BlockPos(toInt(horizontal), toInt(minY), toInt(fixed))
                        : new BlockPos(toInt(fixed), toInt(minY), toInt(horizontal)));
                if (maxY != minY) {
                    targets.add(useX
                            ? new BlockPos(toInt(horizontal), toInt(maxY), toInt(fixed))
                            : new BlockPos(toInt(fixed), toInt(maxY), toInt(horizontal)));
                }
            }
            for (long y = minY + 1L; y < maxY; y++) {
                targets.add(useX
                        ? new BlockPos(toInt(horizontalMin), toInt(y), toInt(fixed))
                        : new BlockPos(toInt(fixed), toInt(y), toInt(horizontalMin)));
                if (horizontalMax != horizontalMin) {
                    targets.add(useX
                            ? new BlockPos(toInt(horizontalMax), toInt(y), toInt(fixed))
                            : new BlockPos(toInt(fixed), toInt(y), toInt(horizontalMax)));
                }
            }
            return;
        }
        for (long y = minY; y <= maxY; y++) {
            for (long horizontal = horizontalMin; horizontal <= horizontalMax; horizontal++) {
                targets.add(useX
                        ? new BlockPos(toInt(horizontal), toInt(y), toInt(fixed))
                        : new BlockPos(toInt(fixed), toInt(y), toInt(horizontal)));
            }
        }
    }

    static void addAdvancedEllipseTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode,
            Direction planeFace) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(BuildShape.CIRCLE, planeFace);
        Direction normal = ShapeGeometryUtil.normalizePlaneFace(planeFace);
        addEllipseTargetsOnAxes(targets, box, axes[0], axes[1], normal, fillMode, false, false);
    }

    /** 以闭区间行段生成椭圆，避免对巨大包围盒扫描无效内部。 */
    static void addEllipseTargetsOnAxes(
            Set<BlockPos> targets,
            RtsCullingBox box,
            Direction axisA,
            Direction axisB,
            Direction normal,
            ShapeFillMode fillMode,
            boolean cylinderLayer,
            boolean includeCaps) {
        long minA = minCoordLong(box, axisA.getAxis());
        long maxA = maxCoordLong(box, axisA.getAxis());
        long minB = minCoordLong(box, axisB.getAxis());
        long maxB = maxCoordLong(box, axisB.getAxis());
        long fixedNormal = minCoordLong(box, normal.getAxis());
        long width = span(toInt(minA), toInt(maxA));
        long height = span(toInt(minB), toInt(maxB));
        long lowerBound = fillMode == ShapeFillMode.FILL
                ? ellipseInteriorLowerBound(width, height)
                : ellipseBoundaryLowerBound(width, height);
        if (lowerBound > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(lowerBound);
        }
        long[] activeA = nonEmptyEllipseAxisRange(minA, maxA, minB, maxB);
        if (activeA == null) {
            return;
        }
        for (long a = activeA[0]; a <= activeA[1]; a++) {
            long[] interval = ellipseRowInterval(a, minA, maxA, minB, maxB);
            if (interval == null) {
                continue;
            }
            if (fillMode == ShapeFillMode.FILL || includeCaps) {
                for (long b = interval[0]; b <= interval[1]; b++) {
                    targets.add(positionAtAxes(box, axisA, a, axisB, b, normal, fixedNormal));
                }
            } else {
                addEllipseBoundaryRow(targets, box, axisA, axisB, normal, fixedNormal,
                        a, interval, minA, maxA, minB, maxB);
            }
        }
    }

    static void addAdvancedEllipticCylinderTargets(Set<BlockPos> targets, RtsCullingBox box,
            ShapeFillMode fillMode, Direction planeFace) {
        Direction[] axes = ShapeGeometryUtil.resolveShapePlaneAxes(BuildShape.CYLINDER, planeFace);
        Direction normal = ShapeGeometryUtil.normalizePlaneFace(planeFace);
        long normalMin = minCoordLong(box, normal.getAxis());
        long normalMax = maxCoordLong(box, normal.getAxis());
        long minA = minCoordLong(box, axes[0].getAxis());
        long maxA = maxCoordLong(box, axes[0].getAxis());
        long minB = minCoordLong(box, axes[1].getAxis());
        long maxB = maxCoordLong(box, axes[1].getAxis());
        boolean singleLayer = normalMin == normalMax;
        long width = span(toInt(minA), toInt(maxA));
        long height = span(toInt(minB), toInt(maxB));
        long boundaryLowerBound = ellipseBoundaryLowerBound(width, height);
        long interiorLowerBound = ellipseInteriorLowerBound(width, height);
        long layerCount = span(toInt(normalMin), toInt(normalMax));
        long lowerBound;
        if (fillMode == ShapeFillMode.FILL) {
            lowerBound = saturatedProduct(interiorLowerBound, 1L, layerCount);
        } else if (singleLayer) {
            lowerBound = boundaryLowerBound;
        } else {
            long capExtra = Math.max(0L, interiorLowerBound - boundaryLowerBound);
            lowerBound = saturatedAdd(
                    saturatedProduct(boundaryLowerBound, 1L, layerCount),
                    saturatedProduct(capExtra, 1L, 2L));
        }
        if (lowerBound > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(lowerBound);
        }
        for (long layer = normalMin; layer <= normalMax; layer++) {
            boolean cap = layer == normalMin || layer == normalMax;
            long[] activeA = nonEmptyEllipseAxisRange(minA, maxA, minB, maxB);
            if (activeA == null) {
                return;
            }
            for (long a = activeA[0]; a <= activeA[1]; a++) {
                long[] interval = ellipseRowInterval(a, minA, maxA, minB, maxB);
                if (interval == null) {
                    continue;
                }
                if (fillMode == ShapeFillMode.FILL || (!singleLayer && cap)) {
                    for (long b = interval[0]; b <= interval[1]; b++) {
                        targets.add(positionAtAxes(box, axes[0], a, axes[1], b, normal, layer));
                    }
                } else {
                    addEllipseBoundaryRow(targets, box, axes[0], axes[1], normal, layer,
                            a, interval, minA, maxA, minB, maxB);
                }
            }
        }
    }

    /** 非填充椭圆只发射当前行相对相邻行的边界，避免扫描整块内部。 */
    static void addEllipseBoundaryRow(
            Set<BlockPos> targets,
            RtsCullingBox box,
            Direction axisA,
            Direction axisB,
            Direction normal,
            long normalCoordinate,
            long a,
            long[] interval,
            long minA,
            long maxA,
            long minB,
            long maxB) {
        long lower = interval[0];
        long upper = interval[1];
        targets.add(positionAtAxes(box, axisA, a, axisB, lower, normal, normalCoordinate));
        if (upper <= lower) {
            return;
        }
        targets.add(positionAtAxes(box, axisA, a, axisB, upper, normal, normalCoordinate));

        long[] previous = ellipseRowInterval(a - 1L, minA, maxA, minB, maxB);
        long[] next = ellipseRowInterval(a + 1L, minA, maxA, minB, maxB);
        if (previous == null || next == null) {
            for (long b = lower + 1L; b < upper; b++) {
                targets.add(positionAtAxes(box, axisA, a, axisB, b, normal, normalCoordinate));
            }
            return;
        }

        long sharedLower = Math.max(lower + 1L, Math.max(previous[0], next[0]));
        long sharedUpper = Math.min(upper - 1L, Math.min(previous[1], next[1]));
        if (sharedLower > sharedUpper) {
            for (long b = lower + 1L; b < upper; b++) {
                targets.add(positionAtAxes(box, axisA, a, axisB, b, normal, normalCoordinate));
            }
            return;
        }
        for (long b = lower + 1L; b < sharedLower; b++) {
            targets.add(positionAtAxes(box, axisA, a, axisB, b, normal, normalCoordinate));
        }
        for (long b = sharedUpper + 1L; b < upper; b++) {
            targets.add(positionAtAxes(box, axisA, a, axisB, b, normal, normalCoordinate));
        }
    }

    static void addAdvancedEllipsoidTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        long minX = box.min().getX();
        long maxX = box.max().getX();
        long minY = box.min().getY();
        long maxY = box.max().getY();
        long minZ = box.min().getZ();
        long maxZ = box.max().getZ();
        long width = span(toInt(minX), toInt(maxX));
        long height = span(toInt(minY), toInt(maxY));
        long depth = span(toInt(minZ), toInt(maxZ));
        long lowerBound = fillMode == ShapeFillMode.FILL
                ? ellipsoidInteriorLowerBound(width, height, depth)
                : ellipsoidBoundaryLowerBound(width, height, depth);
        if (lowerBound > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(lowerBound);
        }
        if (width == 1L && depth == 1L) {
            addAdvancedLineTargets(targets, minY, maxY, minX, minZ, fillMode, Direction.Axis.Y);
            return;
        }
        if (width == 1L && height == 1L) {
            addAdvancedLineTargets(targets, minZ, maxZ, minX, minY, fillMode, Direction.Axis.Z);
            return;
        }
        if (height == 1L && depth == 1L) {
            addAdvancedLineTargets(targets, minX, maxX, minY, minZ, fillMode, Direction.Axis.X);
            return;
        }
        long[] activeY = nonEmptyEllipsoidAxisRange(minY, maxY, minX, maxX, minZ, maxZ);
        if (activeY == null) {
            return;
        }
        for (long y = activeY[0]; y <= activeY[1]; y++) {
            double yRemaining = 1.0D - normalizedCellDistance(y, minY, maxY);
            long[] xInterval = nonEmptyEllipsoidRowRange(y, minX, maxX, minY, maxY, minZ, maxZ);
            if (xInterval == null) {
                continue;
            }
            for (long x = xInterval[0]; x <= xInterval[1]; x++) {
                double remaining = yRemaining - normalizedCellDistance(x, minX, maxX);
                long[] interval = ellipsoidRowInterval(remaining, minZ, maxZ);
                if (interval == null) {
                    continue;
                }
                if (fillMode == ShapeFillMode.FILL) {
                    for (long z = interval[0]; z <= interval[1]; z++) {
                        targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
                    }
                    continue;
                }
                long[] previousX = ellipsoidRowInterval(
                        yRemaining - normalizedCellDistance(x - 1L, minX, maxX), minZ, maxZ);
                long[] nextX = ellipsoidRowInterval(
                        yRemaining - normalizedCellDistance(x + 1L, minX, maxX), minZ, maxZ);
                long[] previousY = ellipsoidRowInterval(
                        1.0D - normalizedCellDistance(y - 1L, minY, maxY)
                                - normalizedCellDistance(x, minX, maxX), minZ, maxZ);
                long[] nextY = ellipsoidRowInterval(
                        1.0D - normalizedCellDistance(y + 1L, minY, maxY)
                                - normalizedCellDistance(x, minX, maxX), minZ, maxZ);
                addEllipsoidBoundaryRow(targets, x, y, interval, previousX, nextX, previousY, nextY);
            }
        }
    }

    /** 非填充椭球只发射当前截面的边界，避免逐格扫描球壳内部。 */
    static void addEllipsoidBoundaryRow(
            Set<BlockPos> targets,
            long x,
            long y,
            long[] interval,
            long[] previousX,
            long[] nextX,
            long[] previousY,
            long[] nextY) {
        long lower = interval[0];
        long upper = interval[1];
        targets.add(new BlockPos(toInt(x), toInt(y), toInt(lower)));
        if (upper <= lower) {
            return;
        }
        targets.add(new BlockPos(toInt(x), toInt(y), toInt(upper)));
        if (previousX == null || nextX == null || previousY == null || nextY == null) {
            for (long z = lower + 1L; z < upper; z++) {
                targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
            }
            return;
        }
        long sharedLower = Math.max(lower + 1L,
                Math.max(Math.max(previousX[0], nextX[0]), Math.max(previousY[0], nextY[0])));
        long sharedUpper = Math.min(upper - 1L,
                Math.min(Math.min(previousX[1], nextX[1]), Math.min(previousY[1], nextY[1])));
        if (sharedLower > sharedUpper) {
            for (long z = lower + 1L; z < upper; z++) {
                targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
            }
            return;
        }
        for (long z = lower + 1L; z < sharedLower; z++) {
            targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
        }
        for (long z = sharedUpper + 1L; z < upper; z++) {
            targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
        }
    }

    static void addAdvancedLineTargets(
            Set<BlockPos> targets, long min, long max, long fixedA, long fixedB,
            ShapeFillMode fillMode, Direction.Axis axis) {
        long length = span(toInt(min), toInt(max));
        long expected = length;
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        for (long value = min; value <= max; value++) {
            targets.add(axis == Direction.Axis.X
                    ? new BlockPos(toInt(value), toInt(fixedA), toInt(fixedB))
                    : axis == Direction.Axis.Y
                            ? new BlockPos(toInt(fixedA), toInt(value), toInt(fixedB))
                            : new BlockPos(toInt(fixedA), toInt(fixedB), toInt(value)));
        }
    }

    static void addAbsoluteRectangleBoundary(
            Set<BlockPos> targets, long minX, long maxX, long minZ, long maxZ, long y) {
        for (long x = minX; x <= maxX; x++) {
            targets.add(new BlockPos(toInt(x), toInt(y), toInt(minZ)));
            if (maxZ != minZ) {
                targets.add(new BlockPos(toInt(x), toInt(y), toInt(maxZ)));
            }
        }
        for (long z = minZ + 1L; z < maxZ; z++) {
            targets.add(new BlockPos(toInt(minX), toInt(y), toInt(z)));
            if (maxX != minX) {
                targets.add(new BlockPos(toInt(maxX), toInt(y), toInt(z)));
            }
        }
    }

    static void addAbsoluteHollowLayer(
            Set<BlockPos> targets, long minX, long maxX, long minZ, long maxZ, long y, boolean yBoundary) {
        if (yBoundary) {
            for (long x = minX; x <= maxX; x++) {
                for (long z = minZ; z <= maxZ; z++) {
                    targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
                }
            }
            return;
        }
        addAbsoluteRectangleBoundary(targets, minX, maxX, minZ, maxZ, y);
    }

    static void addAbsoluteSkeletonLayer(
            Set<BlockPos> targets, long minX, long maxX, long minZ, long maxZ, long y, boolean yBoundary) {
        if (yBoundary) {
            addAbsoluteRectangleBoundary(targets, minX, maxX, minZ, maxZ, y);
            return;
        }
        addAbsoluteTarget(targets, minX, y, minZ);
        addAbsoluteTarget(targets, minX, y, maxZ);
        addAbsoluteTarget(targets, maxX, y, minZ);
        addAbsoluteTarget(targets, maxX, y, maxZ);
    }

    static void addAbsoluteTarget(Set<BlockPos> targets, long x, long y, long z) {
        targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
    }

    /** 椭圆填充目标的保守下界：中心半轴矩形中的单元一定在椭圆内。 */
    static long ellipseInteriorLowerBound(long width, long height) {
        if (width <= 0L || height <= 0L) {
            return 0L;
        }
        if (width == 1L) {
            return height;
        }
        if (height == 1L) {
            return width;
        }
        return Math.max(Math.max(width, height),
                saturatedProduct(Math.max(1L, width / 2L), Math.max(1L, height / 2L), 1L));
    }

    /** 椭圆填充/边界至少覆盖较长轴数量的格；这是不会超过真实集合的早停下界。 */
    static long ellipseBoundaryLowerBound(long width, long height) {
        return width <= 0L || height <= 0L ? 0L : Math.max(width, height);
    }

    /** 椭球填充目标的保守下界；退化轴复用低维椭圆/线段下界。 */
    static long ellipsoidInteriorLowerBound(long width, long height, long depth) {
        if (width <= 0L || height <= 0L || depth <= 0L) {
            return 0L;
        }
        if (width == 1L) {
            return ellipseInteriorLowerBound(height, depth);
        }
        if (height == 1L) {
            return ellipseInteriorLowerBound(width, depth);
        }
        if (depth == 1L) {
            return ellipseInteriorLowerBound(width, height);
        }
        return Math.max(Math.max(width, Math.max(height, depth)),
                saturatedProduct(
                        Math.max(1L, width / 2L),
                        Math.max(1L, height / 2L),
                        Math.max(1L, depth / 2L)));
    }

    /** 椭球边界至少覆盖三个轴中最长轴数量的格。 */
    static long ellipsoidBoundaryLowerBound(long width, long height, long depth) {
        return width <= 0L || height <= 0L || depth <= 0L
                ? 0L : Math.max(width, Math.max(height, depth));
    }

    /** 返回含有整数格心的最小轴区间，避免逐格遍历必为空的投影尾部。 */
    static long[] nonEmptyEllipseAxisRange(long minA, long maxA, long minB, long maxB) {
        return axisIntervalForRemaining(minA, maxA, 1.0D - nearestCellDistance(minB, maxB));
    }

    /** 返回在固定 Y 下仍能容纳至少一个 Z 格的 X 行区间。 */
    static long[] nonEmptyEllipsoidRowRange(
            long y, long minX, long maxX, long minY, long maxY, long minZ, long maxZ) {
        double remaining = 1.0D - normalizedCellDistance(y, minY, maxY)
                - nearestCellDistance(minZ, maxZ);
        return axisIntervalForRemaining(minX, maxX, remaining);
    }

    /** 返回在 X/Z 轴各至少一个整数格时可能非空的 Y 区间。 */
    static long[] nonEmptyEllipsoidAxisRange(
            long minY, long maxY, long minX, long maxX, long minZ, long maxZ) {
        double remaining = 1.0D - nearestCellDistance(minX, maxX)
                - nearestCellDistance(minZ, maxZ);
        return axisIntervalForRemaining(minY, maxY, remaining);
    }

    static long[] axisIntervalForRemaining(long min, long max, double remaining) {
        if (remaining < -1.0E-9D) {
            return null;
        }
        if (min >= max) {
            return new long[] {min, max};
        }
        double center = ((double) min + (double) max + 1.0D) * 0.5D;
        double radius = ((double) max - (double) min + 1.0D) * 0.5D;
        double extent = Math.sqrt(Math.max(0.0D, remaining)) * radius;
        long lower = Math.max(min, (long) Math.ceil(center - extent - 0.5D));
        long upper = Math.min(max, (long) Math.floor(center + extent - 0.5D));
        return lower <= upper ? new long[] {lower, upper} : null;
    }

    static double nearestCellDistance(long min, long max) {
        if (min >= max) {
            return 0.0D;
        }
        double center = ((double) min + (double) max + 1.0D) * 0.5D;
        long nearest = (long) Math.floor(center - 0.5D);
        nearest = Math.max(min, Math.min(max, nearest));
        return normalizedCellDistance(nearest, min, max);
    }

    static long[] ellipseRowInterval(long a, long minA, long maxA, long minB, long maxB) {
        double remaining = 1.0D - normalizedCellDistance(a, minA, maxA);
        if (remaining < -1.0E-9D) {
            return null;
        }
        if (minB >= maxB) {
            return new long[] {minB, maxB};
        }
        double center = ((double) minB + (double) maxB + 1.0D) * 0.5D;
        double radius = ((double) maxB - (double) minB + 1.0D) * 0.5D;
        double extent = Math.sqrt(Math.max(0.0D, remaining)) * radius;
        long lower = Math.max(minB, (long) Math.ceil(center - extent - 0.5D));
        long upper = Math.min(maxB, (long) Math.floor(center + extent - 0.5D));
        return lower <= upper ? new long[] {lower, upper} : null;
    }

    static boolean insideEllipseCoordinate(
            long a, long b, long minA, long maxA, long minB, long maxB) {
        return normalizedCellDistance(a, minA, maxA)
                + normalizedCellDistance(b, minB, maxB) <= 1.0D;
    }

    static long[] ellipsoidRowInterval(double remaining, long minZ, long maxZ) {
        if (remaining < -1.0E-9D) {
            return null;
        }
        if (minZ >= maxZ) {
            return remaining >= -1.0E-9D ? new long[] {minZ, maxZ} : null;
        }
        double center = ((double) minZ + (double) maxZ + 1.0D) * 0.5D;
        double radius = ((double) maxZ - (double) minZ + 1.0D) * 0.5D;
        double extent = Math.sqrt(Math.max(0.0D, remaining)) * radius;
        long lower = Math.max(minZ, (long) Math.ceil(center - extent - 0.5D));
        long upper = Math.min(maxZ, (long) Math.floor(center + extent - 0.5D));
        return lower <= upper ? new long[] {lower, upper} : null;
    }

    static boolean insideEllipsoidCoordinate(
            long z, double remaining, long minZ, long maxZ) {
        return remaining >= -1.0E-9D
                && normalizedCellDistance(z, minZ, maxZ) <= remaining + 1.0E-9D;
    }

    static BlockPos positionAtAxes(
            RtsCullingBox box, Direction axisA, long a, Direction axisB, long b,
            Direction normal, long normalCoordinate) {
        long x = box.min().getX();
        long y = box.min().getY();
        long z = box.min().getZ();
        long[] coordinates = new long[] {x, y, z};
        coordinates[axisA.getAxis().ordinal()] = a;
        coordinates[axisB.getAxis().ordinal()] = b;
        coordinates[normal.getAxis().ordinal()] = normalCoordinate;
        return new BlockPos(toInt(coordinates[0]), toInt(coordinates[1]), toInt(coordinates[2]));
    }

    static long minCoordLong(RtsCullingBox box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.min().getX();
            case Y -> box.min().getY();
            case Z -> box.min().getZ();
        };
    }

    static long maxCoordLong(RtsCullingBox box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.max().getX();
            case Y -> box.max().getY();
            case Z -> box.max().getZ();
        };
    }

    private static int coord(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static int minCoord(RtsCullingBox box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.min().getX();
            case Y -> box.min().getY();
            case Z -> box.min().getZ();
        };
    }

    private static int maxCoord(RtsCullingBox box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.max().getX();
            case Y -> box.max().getY();
            case Z -> box.max().getZ();
        };
    }

    static boolean insideEllipseCell(int x, int z, RtsCullingBox box) {
        return normalizedCellDistance(x, box.min().getX(), box.max().getX())
                + normalizedCellDistance(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    static boolean insideEllipseCell(BlockPos pos, RtsCullingBox box, Direction[] axes) {
        return normalizedCellDistance(coord(pos, axes[0].getAxis()),
                minCoord(box, axes[0].getAxis()), maxCoord(box, axes[0].getAxis()))
                + normalizedCellDistance(coord(pos, axes[1].getAxis()),
                        minCoord(box, axes[1].getAxis()), maxCoord(box, axes[1].getAxis())) <= 1.0D;
    }

    static boolean insideEllipsoidCell(int x, int y, int z, RtsCullingBox box) {
        return normalizedCellDistance(x, box.min().getX(), box.max().getX())
                + normalizedCellDistance(y, box.min().getY(), box.max().getY())
                + normalizedCellDistance(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    static double normalizedCellDistance(int value, int min, int max) {
        return normalizedCellDistance((long) value, min, max);
    }

    static double normalizedCellDistance(long value, long min, long max) {
        if (min >= max) {
            return value == min ? 0.0D : Double.POSITIVE_INFINITY;
        }
        double center = ((double) min + (double) max + 1.0D) * 0.5D;
        double radius = ((double) max - (double) min + 1.0D) * 0.5D;
        double delta = ((double) value + 0.5D - center) / radius;
        return delta * delta;
    }
}
