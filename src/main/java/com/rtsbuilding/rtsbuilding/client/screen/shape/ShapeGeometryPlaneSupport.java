package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil.PlaneCell;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.*;

/** 平面几何、圆盘计数与有界整数运算的共享实现；不负责形状选择或 UI。 */
final class ShapeGeometryPlaneSupport {

// ======================== 实用方法 ========================

    /** 构建圆形平面单元格；fill=false 时只返回外圈。 */
    public static Set<PlaneCell> buildCircleCells(int radius, boolean fill) {
        int safeRadius = Math.max(0, radius);
        long count = circleCellCount(safeRadius, fill, MiningLimits.MAX_VOLUME);
        if (count > MiningLimits.MAX_VOLUME) {
            // 旧 Set API 没有状态返回值；正式计划入口会返回 TOO_LARGE，避免这里
            // 在兼容调用中意外分配数十亿个平面单元。
            return Set.of();
        }
        Set<PlaneCell> cells = new HashSet<>();
        List<PlaneCell> emitted = new ArrayList<>((int) count);
        emitCircleCells(emitted, safeRadius, fill);
        for (PlaneCell cell : emitted) {
            if (cell != null) {
                cells.add(cell);
            }
        }
        return fill ? fillPlaneInteriorHoles(cells) : cells;
    }

    /** 按行直接发射圆盘/圆环，避免先构建圆环的实心包围盒。 */
    static void emitCircleCells(
            List<PlaneCell> cells,
            int radius,
            boolean fill) {
        int innerRadius = Math.max(0, radius - 1);
        long outerSquared = (long) radius * radius;
        long innerSquared = (long) innerRadius * innerRadius;
        for (long a = -radius; a <= radius; a++) {
            long aSquared = a * a;
            long outerRowSquared = outerSquared - aSquared;
            if (outerRowSquared < 0L) {
                continue;
            }
            int outer = floorSqrt(outerRowSquared);
            if (fill) {
                for (int b = -outer; b <= outer; b++) {
                    cells.add(new PlaneCell(toInt(a), b));
                }
                continue;
            }
            long innerRowSquared = innerSquared - aSquared;
            if (innerRowSquared <= 0L) {
                for (int b = -outer; b <= outer; b++) {
                    cells.add(new PlaneCell(toInt(a), b));
                }
                continue;
            }
            int inner = ceilSqrt(innerRowSquared);
            for (int b = -outer; b <= -inner; b++) {
                cells.add(new PlaneCell(toInt(a), b));
            }
            for (int b = inner; b <= outer; b++) {
                cells.add(new PlaneCell(toInt(a), b));
            }
        }
    }

    /** 按行直接发射世界坐标圆盘/圆环。 */
    static void emitCircleCells(
            List<BlockPos> positions,
            BlockPos origin,
            Direction axisA,
            Direction axisB,
            int radius,
            boolean fill) {
        List<PlaneCell> cells = new ArrayList<>();
        emitCircleCells(cells, radius, fill);
        for (PlaneCell cell : cells) {
            positions.add(offsetPos(origin, axisA, cell.a(), axisB, cell.b()));
        }
    }

    private static BlockPos offsetPos(BlockPos origin, Direction axisA, int stepA,
            Direction axisB, int stepB) {
        long dx = (long) axisA.getStepX() * stepA + (long) axisB.getStepX() * stepB;
        long dy = (long) axisA.getStepY() * stepA + (long) axisB.getStepY() * stepB;
        long dz = (long) axisA.getStepZ() * stepA + (long) axisB.getStepZ() * stepB;
        return new BlockPos(toInt((long) origin.getX() + dx),
                toInt((long) origin.getY() + dy),
                toInt((long) origin.getZ() + dz));
    }

    /** 计算圆盘/圆环唯一格数；超过 limit 时提前返回 limit+1。 */
    static long circleCellCount(int radius, boolean fill, int limit) {
        int safeRadius = Math.max(0, radius);
        long budget = Math.max(1L, Math.min((long) MiningLimits.MAX_VOLUME, limit));
        long outerSquared = (long) safeRadius * safeRadius;
        long innerSquared = (long) Math.max(0, safeRadius - 1) * Math.max(0, safeRadius - 1);
        // 每个横坐标至少有一个圆环格；这是比遍历整个平方包围盒便宜的早停条件。
        if (!fill && (long) safeRadius * 2L + 1L > budget) {
            return budget + 1L;
        }
        // 填充圆的中轴行本身就有 2r+1 个目标。
        if (fill && (long) safeRadius * 2L + 1L > budget) {
            return budget + 1L;
        }
        long count = 0L;
        for (long a = -safeRadius; a <= safeRadius; a++) {
            long rowSquared = outerSquared - a * a;
            if (rowSquared < 0L) {
                continue;
            }
            int outer = floorSqrt(rowSquared);
            long rowCount;
            if (fill) {
                rowCount = (long) outer * 2L + 1L;
            } else {
                long innerRowSquared = innerSquared - a * a;
                if (innerRowSquared <= 0L) {
                    rowCount = (long) outer * 2L + 1L;
                } else {
                    int inner = ceilSqrt(innerRowSquared);
                    rowCount = inner > outer ? 0L : (long) (outer - inner + 1) * 2L;
                }
            }
            count = saturatedAdd(count, rowCount);
            if (count > budget) {
                return budget + 1L;
            }
        }
        return count;
    }

    /** 计算球体行段数量；达到预算后立即返回 budget+1。 */
    static long ballCellCount(int radius, boolean fill, int limit) {
        long budget = Math.max(1L, Math.min((long) MiningLimits.MAX_VOLUME, limit));
        int safeRadius = Math.max(0, radius);
        // 球的任一中心轴至少包含直径长度的整数格；巨型输入无需进入逐层/逐列扫描。
        if ((long) safeRadius * 2L + 1L > budget) {
            return budget + 1L;
        }
        long outerSquared = (long) safeRadius * safeRadius;
        long innerRadius = Math.max(0, safeRadius - 1);
        long innerSquared = innerRadius * innerRadius;
        long count = 0L;
        for (long y = -safeRadius; y <= safeRadius; y++) {
            long outerRowSquared = outerSquared - y * y;
            if (outerRowSquared < 0L) {
                continue;
            }
            int outer = floorSqrt(outerRowSquared);
            for (long x = -outer; x <= outer; x++) {
                long zOuterSquared = outerRowSquared - x * x;
                int zOuter = floorSqrt(Math.max(0L, zOuterSquared));
                long rowCount;
                if (fill) {
                    rowCount = (long) zOuter * 2L + 1L;
                } else {
                    long zInnerSquared = innerSquared - y * y - x * x;
                    int zInner = zInnerSquared <= 0L ? 0 : ceilSqrt(zInnerSquared);
                    rowCount = zInner <= 0L
                            ? (long) zOuter * 2L + 1L
                            : (long) (zOuter - zInner + 1) * 2L;
                }
                count = saturatedAdd(count, rowCount);
                if (count > budget) {
                    return budget + 1L;
                }
            }
        }
        return count;
    }

    static int targetLimit(Set<BlockPos> targets) {
        return targets instanceof ShapeGeometryBudget.TargetSet bounded
                ? bounded.limit
                : MiningLimits.MAX_VOLUME;
    }

    static long roundedPlanarRadius(long a, long b) {
        return Math.round(Math.sqrt((double) a * a + (double) b * b));
    }

    static long roundedSpatialRadius(long dx, long dy, long dz) {
        return Math.round(Math.sqrt(
                (double) dx * dx + (double) dy * dy + (double) dz * dz));
    }

    static int clampRadius(long radius, int maxRadius) {
        long safeMax = Math.max(0L, maxRadius);
        return (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, Math.min(radius, safeMax)));
    }

    static int floorSqrt(long value) {
        if (value <= 0L) {
            return 0;
        }
        long root = (long) Math.sqrt((double) value);
        while (root < Integer.MAX_VALUE && (root + 1L) * (root + 1L) <= value) {
            root++;
        }
        while (root * root > value) {
            root--;
        }
        return (int) root;
    }

    static int ceilSqrt(long value) {
        int floor = floorSqrt(value);
        return (long) floor * floor == value ? floor : floor + 1;
    }

    static long saturatedAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE : left + right;
    }

    static long saturatedProduct(long left, long middle, long right) {
        if (left <= 0L || middle <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / middle) {
            return Long.MAX_VALUE;
        }
        long partial = left * middle;
        return partial > Long.MAX_VALUE / right ? Long.MAX_VALUE : partial * right;
    }

    static int toInt(long value) {
        return value <= Integer.MIN_VALUE ? Integer.MIN_VALUE
                : value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    static long span(int min, int max) {
        return (long) Math.max(min, max) - Math.min(min, max) + 1L;
    }

    /** 普通与高级矩形共享的唯一边界格数，退化单轴不重复计数。 */
    static long perimeterCount(long width, long height) {
        if (width <= 0L || height <= 0L) {
            return 0L;
        }
        if (width == 1L) {
            return height;
        }
        if (height == 1L) {
            return width;
        }
        return saturatedAdd(width * 2L, (height - 2L) * 2L);
    }

    static long boxTargetCount(long width, long height, long depth, ShapeFillMode fillMode) {
        long volume = saturatedProduct(width, height, depth);
        if (fillMode == ShapeFillMode.FILL) {
            return volume;
        }
        if (fillMode == ShapeFillMode.HOLLOW) {
            if (volume == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long interior = saturatedProduct(
                    Math.max(0L, width - 2L),
                    Math.max(0L, height - 2L),
                    Math.max(0L, depth - 2L));
            return Math.max(0L, volume - interior);
        }
        long corners = Math.min(width, 2L) * Math.min(height, 2L) * Math.min(depth, 2L);
        long xEdges = Math.max(0L, width - 2L)
                * Math.min(height, 2L) * Math.min(depth, 2L);
        long yEdges = Math.max(0L, height - 2L)
                * Math.min(width, 2L) * Math.min(depth, 2L);
        long zEdges = Math.max(0L, depth - 2L)
                * Math.min(width, 2L) * Math.min(height, 2L);
        return saturatedAdd(corners, saturatedAdd(xEdges, saturatedAdd(yEdges, zEdges)));
    }

    static void addBoxHollowLayer(
            Set<BlockPos> targets,
            BlockPos start,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            long iy,
            boolean yBoundary) {
        if (yBoundary) {
            for (long x = minX; x <= maxX; x++) {
                for (long z = minZ; z <= maxZ; z++) {
                    addOffsetTarget(targets, start, x, iy, z);
                }
            }
            return;
        }
        for (long z = minZ; z <= maxZ; z++) {
            addOffsetTarget(targets, start, minX, iy, z);
            if (maxX != minX) {
                addOffsetTarget(targets, start, maxX, iy, z);
            }
        }
        for (long x = (long) minX + 1L; x < maxX; x++) {
            addOffsetTarget(targets, start, x, iy, minZ);
            if (maxZ != minZ) {
                addOffsetTarget(targets, start, x, iy, maxZ);
            }
        }
    }

    static void addBoxSkeletonLayer(
            Set<BlockPos> targets,
            BlockPos start,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            long iy,
            boolean yBoundary) {
        if (yBoundary) {
            for (long z = minZ; z <= maxZ; z++) {
                addOffsetTarget(targets, start, minX, iy, z);
                if (maxX != minX) {
                    addOffsetTarget(targets, start, maxX, iy, z);
                }
            }
            for (long x = (long) minX + 1L; x < maxX; x++) {
                addOffsetTarget(targets, start, x, iy, minZ);
                if (maxZ != minZ) {
                    addOffsetTarget(targets, start, x, iy, maxZ);
                }
            }
            return;
        }
        // 侧面的四条竖边；退化轴由 add 的去重语义自然处理。
        addOffsetTarget(targets, start, minX, iy, minZ);
        if (maxZ != minZ) {
            addOffsetTarget(targets, start, minX, iy, maxZ);
        }
        if (maxX != minX) {
            addOffsetTarget(targets, start, maxX, iy, minZ);
            if (maxZ != minZ) {
                addOffsetTarget(targets, start, maxX, iy, maxZ);
            }
        }
    }

    static void addOffsetTarget(Set<BlockPos> targets, BlockPos start,
            long x, long y, long z) {
        targets.add(new BlockPos(
                toInt((long) start.getX() + x),
                toInt((long) start.getY() + y),
                toInt((long) start.getZ() + z)));
    }

    /** 检查是否平面边界单元格 */
    public static boolean isPlaneBoundaryCell(Set<PlaneCell> filledCells, PlaneCell cell) {
        return !filledCells.contains(new PlaneCell(cell.a() + 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a() - 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() + 1))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() - 1));
    }

    /** 构建旋转矩形填充单元格集合 */
    public static Set<PlaneCell> buildRotatedRectangleFillCells(int minA, int maxA, int minB, int maxB, int degrees) {
        Set<PlaneCell> filled = new HashSet<>();
        int normalized = Math.floorMod(degrees, 360);
        if (normalized == 0) {
            for (int a = minA; a <= maxA; a++) {
                for (int b = minB; b <= maxB; b++) {
                    filled.add(new PlaneCell(a, b));
                }
            }
            return fillPlaneInteriorHoles(filled);
        }

        double centerA = (minA + maxA) * 0.5D;
        double centerB = (minB + maxB) * 0.5D;
        double rad = Math.toRadians(normalized);
        double cos = Math.cos(rad);
        double sin = Math.sin(rad);

        double[][] corners = new double[][] {
                { minA, minB }, { minA, maxB }, { maxA, minB }, { maxA, maxB }
        };
        double minRotA = Double.POSITIVE_INFINITY;
        double maxRotA = Double.NEGATIVE_INFINITY;
        double minRotB = Double.POSITIVE_INFINITY;
        double maxRotB = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            double da = corner[0] - centerA;
            double db = corner[1] - centerB;
            double ra = (da * cos) - (db * sin) + centerA;
            double rb = (da * sin) + (db * cos) + centerB;
            minRotA = Math.min(minRotA, ra);
            maxRotA = Math.max(maxRotA, ra);
            minRotB = Math.min(minRotB, rb);
            maxRotB = Math.max(maxRotB, rb);
        }

        int scanMinA = (int) Math.floor(minRotA) - 1;
        int scanMaxA = (int) Math.ceil(maxRotA) + 1;
        int scanMinB = (int) Math.floor(minRotB) - 1;
        int scanMaxB = (int) Math.ceil(maxRotB) + 1;

        for (int a = scanMinA; a <= scanMaxA; a++) {
            for (int b = scanMinB; b <= scanMaxB; b++) {
                if (isInverseRotatedInsideCellBounds(a, b, minA, maxA, minB, maxB, centerA, centerB, cos, sin)) {
                    filled.add(new PlaneCell(a, b));
                }
            }
        }
        return fillPlaneInteriorHoles(filled);
    }

    /** 逆旋转检测单元格是否在边界内 */
    public static boolean isInverseRotatedInsideCellBounds(
            int targetA, int targetB,
            int minA, int maxA, int minB, int maxB,
            double centerA, double centerB,
            double cos, double sin) {
        double[][] sampleOffsets = new double[][] {
                { 0.0D, 0.0D }, { -0.35D, 0.0D }, { 0.35D, 0.0D },
                { 0.0D, -0.35D }, { 0.0D, 0.35D },
                { -0.3D, -0.3D }, { -0.3D, 0.3D }, { 0.3D, -0.3D }, { 0.3D, 0.3D }
        };
        for (double[] sample : sampleOffsets) {
            double da = (targetA + sample[0]) - centerA;
            double db = (targetB + sample[1]) - centerB;
            double sourceA = (da * cos) + (db * sin) + centerA;
            double sourceB = (-da * sin) + (db * cos) + centerB;
            if (sourceA >= minA - 0.5D && sourceA <= maxA + 0.5D
                    && sourceB >= minB - 0.5D && sourceB <= maxB + 0.5D) {
                return true;
            }
        }
        return false;
    }

    /** 填充平面内部空洞（洪水填充算法） */
    public static Set<PlaneCell> fillPlaneInteriorHoles(Set<PlaneCell> filledCells) {
        if (filledCells == null || filledCells.isEmpty()) {
            return filledCells == null ? Set.of() : filledCells;
        }

        int minA = Integer.MAX_VALUE, maxA = Integer.MIN_VALUE;
        int minB = Integer.MAX_VALUE, maxB = Integer.MIN_VALUE;
        for (PlaneCell cell : filledCells) {
            minA = Math.min(minA, cell.a());
            maxA = Math.max(maxA, cell.a());
            minB = Math.min(minB, cell.b());
            maxB = Math.max(maxB, cell.b());
        }

        int extMinA = minA - 1, extMaxA = maxA + 1;
        int extMinB = minB - 1, extMaxB = maxB + 1;

        Set<PlaneCell> outside = new HashSet<>();
        ArrayDeque<PlaneCell> queue = new ArrayDeque<>();
        for (int a = extMinA; a <= extMaxA; a++) {
            queueOutsidePlaneCell(new PlaneCell(a, extMinB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(a, extMaxB), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }
        for (int b = extMinB + 1; b <= extMaxB - 1; b++) {
            queueOutsidePlaneCell(new PlaneCell(extMinA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(extMaxA, b), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }

        while (!queue.isEmpty()) {
            PlaneCell cell = queue.removeFirst();
            queueOutsidePlaneCell(new PlaneCell(cell.a() + 1, cell.b()), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a() - 1, cell.b()), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a(), cell.b() + 1), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
            queueOutsidePlaneCell(new PlaneCell(cell.a(), cell.b() - 1), filledCells, outside, queue, extMinA, extMaxA, extMinB, extMaxB);
        }

        Set<PlaneCell> dense = new HashSet<>(filledCells);
        for (int a = minA; a <= maxA; a++) {
            for (int b = minB; b <= maxB; b++) {
                PlaneCell cell = new PlaneCell(a, b);
                if (dense.contains(cell)) continue;
                if (!outside.contains(cell)) dense.add(cell);
            }
        }
        return dense;
    }

    /** 将外部单元格加入队列 */
    static void queueOutsidePlaneCell(
            PlaneCell cell, Set<PlaneCell> filledCells, Set<PlaneCell> outside,
            ArrayDeque<PlaneCell> queue, int minA, int maxA, int minB, int maxB) {
        if (cell.a() < minA || cell.a() > maxA || cell.b() < minB || cell.b() > maxB) return;
        if (filledCells.contains(cell) || outside.contains(cell)) return;
        outside.add(cell);
        queue.addLast(cell);
    }
}
