package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 形状几何计算工具类。
 * <p>
 * 提供各种建造形状（直线、方形、墙壁、圆形、立方体）的方块位置计算，
 * 以及形状旋转、面朝向解析、填充模式处理等纯几何运算。
 * 所有方法均为静态无状态方法。
 */
public final class ShapeGeometryUtil {

    // ======================== 形状放置目标生成 ========================

    /**
     * 根据形状构建输入和填充模式生成所有目标方块位置。
     *
     * @param input    形状构建输入（形状类型、锚点等）
     * @param fillMode 填充模式（实心、空心、骨架）
     * @return 目标方块位置列表
     */
    public static List<BlockPos> buildShapePositions(ShapeBuildTypes.Input input, ShapeBuildTypes.ShapeFillMode fillMode) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        BlockPos start = input.pointA();
        BlockPos end = input.pointB();
        switch (input.shape()) {
            case LINE -> addLineTargets(targets, start, end, input.connectedLine());
            case SQUARE -> addSquareTargets(targets, start, end, input.planeFace(), fillMode);
            case WALL -> addWallTargets(targets, start, end, input.boxHeightOffset(), fillMode, input.connectedLine());
            case CIRCLE -> addCircleTargets(targets, start, end, input.planeFace(), fillMode);
            case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), fillMode);
            default -> targets.add(start);
        }
        return new ArrayList<>(targets);
    }

    // ======================== 单个形状算法 ========================

    /** 生成直线方块（Bresenham 线段近似） */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end) {
        addLineTargets(targets, start, end, false);
    }

    /** 生成直线方块，连接模式会填入桥接方块，避免斜向线段断开。 */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, boolean connected) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 0) {
            targets.add(start);
            return;
        }

        if (steps > BuilderScreenConstants.SHAPE_MAX_OFFSET) {
            double scale = BuilderScreenConstants.SHAPE_MAX_OFFSET / (double) steps;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
            dz = (int) Math.round(dz * scale);
            steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        }

        if (connected) {
            addConnectedLineTargets(targets, start, dx, dy, dz, steps);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = start.getX() + (int) Math.round(dx * t);
            int y = start.getY() + (int) Math.round(dy * t);
            int z = start.getZ() + (int) Math.round(dz * t);
            targets.add(new BlockPos(x, y, z));
        }
    }

    /**
     * Adds a face-connected line by stepping the major axis first, then inserting
     * bridge blocks before each secondary-axis move.
     */
    private static void addConnectedLineTargets(Set<BlockPos> targets, BlockPos start,
            int dx, int dy, int dz, int steps) {
        int adx = Math.abs(dx);
        int ady = Math.abs(dy);
        int adz = Math.abs(dz);
        int sx = dx >= 0 ? 1 : -1;
        int sy = dy >= 0 ? 1 : -1;
        int sz = dz >= 0 ? 1 : -1;
        int x = start.getX();
        int y = start.getY();
        int z = start.getZ();
        targets.add(new BlockPos(x, y, z));

        if (adx >= ady && adx >= adz) {
            int errY = adx / 2;
            int errZ = adx / 2;
            for (int i = 0; i < adx; i++) {
                errY -= ady;
                errZ -= adz;
                boolean stepY = errY < 0;
                boolean stepZ = errZ < 0;
                x += sx;
                if (stepY) {
                    targets.add(new BlockPos(x, y, z));
                    y += sy;
                    errY += adx;
                }
                if (stepZ) {
                    targets.add(new BlockPos(x, y, z));
                    z += sz;
                    errZ += adx;
                }
                targets.add(new BlockPos(x, y, z));
            }
        } else if (ady >= adx && ady >= adz) {
            int errX = ady / 2;
            int errZ = ady / 2;
            for (int i = 0; i < ady; i++) {
                errX -= adx;
                errZ -= adz;
                boolean stepX = errX < 0;
                boolean stepZ = errZ < 0;
                y += sy;
                if (stepX) {
                    targets.add(new BlockPos(x, y, z));
                    x += sx;
                    errX += ady;
                }
                if (stepZ) {
                    targets.add(new BlockPos(x, y, z));
                    z += sz;
                    errZ += ady;
                }
                targets.add(new BlockPos(x, y, z));
            }
        } else {
            int errX = adz / 2;
            int errY = adz / 2;
            for (int i = 0; i < adz; i++) {
                errX -= adx;
                errY -= ady;
                boolean stepX = errX < 0;
                boolean stepY = errY < 0;
                z += sz;
                if (stepX) {
                    targets.add(new BlockPos(x, y, z));
                    x += sx;
                    errX += adz;
                }
                if (stepY) {
                    targets.add(new BlockPos(x, y, z));
                    y += sy;
                    errY += adz;
                }
                targets.add(new BlockPos(x, y, z));
            }
        }
    }

    /** 生成正方形方块 */
    public static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeBuildTypes.ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.SQUARE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]));
        addRotatedPlaneRectangleTargets(targets, start, axes[0], axes[1], aOffset, bOffset, fillMode, 0);
    }

    /** 生成墙壁方块 */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeBuildTypes.ShapeFillMode fillMode) {
        addWallTargets(targets, start, end, heightOffset, fillMode, false);
    }

    /** 生成墙壁方块，连接模式会让底部线段保持面相邻。 */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            ShapeBuildTypes.ShapeFillMode fillMode, boolean connected) {
        LinkedHashSet<BlockPos> baseLine = new LinkedHashSet<>();
        addLineTargets(baseLine, start, new BlockPos(end.getX(), start.getY(), end.getZ()), connected);
        if (baseLine.isEmpty()) {
            baseLine.add(start);
        }

        int yOffset = clampShapeOffset(heightOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        List<BlockPos> base = new ArrayList<>(baseLine);
        for (int i = 0; i < base.size(); i++) {
            BlockPos basePos = base.get(i);
            boolean endColumn = i == 0 || i == base.size() - 1;
            for (int iy = minY; iy <= maxY; iy++) {
                if (fillMode != ShapeBuildTypes.ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                targets.add(basePos.above(iy));
            }
        }
    }

    /** 生成圆形方块 */
    public static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeBuildTypes.ShapeFillMode fillMode) {
        int degrees = 0; // 由调用方传入旋转角度
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.CIRCLE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = Mth.clamp((int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b))), 0, BuilderScreenConstants.SHAPE_MAX_RADIUS);
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        Set<PlaneCell> rotatedCells = new HashSet<>();

        for (int ia = -radius; ia <= radius; ia++) {
            for (int ib = -radius; ib <= radius; ib++) {
                int dist2 = (ia * ia) + (ib * ib);
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                if (!inOuter || ((fillMode != ShapeBuildTypes.ShapeFillMode.FILL) && inInner)) {
                    continue;
                }
                RotatedOffset rotated = rotatePlaneOffset(ia, ib, 0.0D, 0.0D, degrees);
                rotatedCells.add(new PlaneCell(rotated.a(), rotated.b()));
            }
        }

        if (fillMode == ShapeBuildTypes.ShapeFillMode.FILL) {
            rotatedCells = fillPlaneInteriorHoles(rotatedCells);
        }

        for (PlaneCell cell : rotatedCells) {
            targets.add(offsetPos(start, axes[0], cell.a(), axes[1], cell.b()));
        }
    }

    /** 生成立方体方块 */
    public static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeBuildTypes.ShapeFillMode fillMode) {
        int degrees = 0; // 由调用方传入旋转角度
        int xOffset = clampShapeOffset(end.getX() - start.getX());
        int zOffset = clampShapeOffset(end.getZ() - start.getZ());
        int yOffset = clampShapeOffset(heightOffset);

        int minX = Math.min(0, xOffset);
        int maxX = Math.max(0, xOffset);
        int minZ = Math.min(0, zOffset);
        int maxZ = Math.max(0, zOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        Set<PlaneCell> rotatedFootprint = buildRotatedRectangleFillCells(minX, maxX, minZ, maxZ, degrees);
        if (rotatedFootprint.isEmpty()) {
            return;
        }

        if (fillMode == ShapeBuildTypes.ShapeFillMode.FILL) {
            for (PlaneCell cell : rotatedFootprint) {
                for (int iy = minY; iy <= maxY; iy++) {
                    targets.add(start.offset(cell.a(), iy, cell.b()));
                }
            }
            return;
        }

        Set<BlockPos> fullVolume = new HashSet<>(rotatedFootprint.size() * Math.max(1, (maxY - minY) + 1));
        for (PlaneCell cell : rotatedFootprint) {
            for (int iy = minY; iy <= maxY; iy++) {
                fullVolume.add(start.offset(cell.a(), iy, cell.b()));
            }
        }

        for (BlockPos pos : fullVolume) {
            boolean xBoundary = !fullVolume.contains(pos.east()) || !fullVolume.contains(pos.west());
            boolean yBoundary = !fullVolume.contains(pos.above()) || !fullVolume.contains(pos.below());
            boolean zBoundary = !fullVolume.contains(pos.north()) || !fullVolume.contains(pos.south());
            int boundaryAxes = (xBoundary ? 1 : 0) + (yBoundary ? 1 : 0) + (zBoundary ? 1 : 0);
            if (fillMode == ShapeBuildTypes.ShapeFillMode.HOLLOW) {
                if (boundaryAxes >= 1) {
                    targets.add(pos);
                }
                continue;
            }
            if (boundaryAxes >= 2) {
                targets.add(pos);
            }
        }
    }

    // ======================== 平面矩形（带旋转） ========================

    /** 生成带旋转的平面矩形方块 */
    public static void addRotatedPlaneRectangleTargets(Set<BlockPos> targets, BlockPos start, Direction axisA, Direction axisB,
            int aOffset, int bOffset, ShapeBuildTypes.ShapeFillMode fillMode, int degrees) {
        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);
        Set<PlaneCell> filledCells = buildRotatedRectangleFillCells(minA, maxA, minB, maxB, degrees);
        for (PlaneCell cell : filledCells) {
            if (fillMode != ShapeBuildTypes.ShapeFillMode.FILL && isPlaneBoundaryCell(filledCells, cell)) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
                continue;
            }
            if (fillMode == ShapeBuildTypes.ShapeFillMode.FILL) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
            }
        }
    }

    // ======================== 实用方法 ========================

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
    private static void queueOutsidePlaneCell(
            PlaneCell cell, Set<PlaneCell> filledCells, Set<PlaneCell> outside,
            ArrayDeque<PlaneCell> queue, int minA, int maxA, int minB, int maxB) {
        if (cell.a() < minA || cell.a() > maxA || cell.b() < minB || cell.b() > maxB) return;
        if (filledCells.contains(cell) || outside.contains(cell)) return;
        outside.add(cell);
        queue.addLast(cell);
    }

    // ======================== 坐标/向量工具 ========================

    /** 限制形状偏移值 */
    public static int clampShapeOffset(int value) {
        return Mth.clamp(value, -BuilderScreenConstants.SHAPE_MAX_OFFSET, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    /** 计算方向上的投影分量 */
    public static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    /** 在两个方向轴上偏移位置 */
    public static BlockPos offsetPos(BlockPos origin, Direction axisA, int stepA, Direction axisB, int stepB) {
        int dx = (axisA.getStepX() * stepA) + (axisB.getStepX() * stepB);
        int dy = (axisA.getStepY() * stepA) + (axisB.getStepY() * stepB);
        int dz = (axisA.getStepZ() * stepA) + (axisB.getStepZ() * stepB);
        return origin.offset(dx, dy, dz);
    }

    /** 旋转平面偏移量 */
    public static RotatedOffset rotatePlaneOffset(int a, int b, double centerA, double centerB, int degrees) {
        int normalized = Math.floorMod(degrees, 360);
        if (normalized == 0) return new RotatedOffset(a, b);
        double rad = Math.toRadians(normalized);
        double da = a - centerA, db = b - centerB;
        int ra = (int) Math.round((da * Math.cos(rad)) - (db * Math.sin(rad)) + centerA);
        int rb = (int) Math.round((da * Math.sin(rad)) + (db * Math.cos(rad)) + centerB);
        return new RotatedOffset(ra, rb);
    }

    // ======================== 面朝向解析 ========================

    /** 解析形状的构建基准面 */
    public static Direction resolveShapeBuildFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape == null) return clickedFace == null ? Direction.UP : clickedFace;
        return switch (shape) {
            case LINE, SQUARE, WALL, BOX -> Direction.UP;
            default -> clickedFace == null ? Direction.UP : clickedFace;
        };
    }

    /** 解析形状的放置面 */
    public static Direction resolveShapePlacementFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (clickedFace != null) return clickedFace;
        return resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    /** 解析形状的平面轴向 */
    public static Direction[] resolveShapePlaneAxes(ClientRtsController.BuildShape shape, Direction face) {
        if (shape == ClientRtsController.BuildShape.SQUARE || shape == ClientRtsController.BuildShape.BOX) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (shape == ClientRtsController.BuildShape.WALL) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (face == null) return new Direction[] { Direction.EAST, Direction.SOUTH };
        return switch (face.getAxis()) {
            case Y -> new Direction[] { Direction.EAST, Direction.SOUTH };
            case X -> new Direction[] { Direction.UP, Direction.SOUTH };
            case Z -> new Direction[] { Direction.EAST, Direction.UP };
        };
    }

    /** 判断形状是否需要第三点（仅立方体需要） */
    public static boolean requiresThirdPoint(ClientRtsController.BuildShape shape) {
        return shape == ClientRtsController.BuildShape.BOX;
    }

    // ======================== 放置命中结果生成 ========================

    /** 创建形状放置的 BlockHitResult */
    public static BlockHitResult createShapePlacementHit(BlockPos pos, Direction face) {
        Vec3 faceNormal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 hitVec = Vec3.atCenterOf(pos).add(faceNormal.scale(0.5D));
        return new BlockHitResult(hitVec, face, pos, false);
    }

    // ======================== 可用填充模式 ========================

    /** 获取形状的可用填充模式列表 */
    public static List<ShapeBuildTypes.ShapeFillMode> availableFillModes(ClientRtsController.BuildShape shape) {
        if (shape == null) return List.of(ShapeBuildTypes.ShapeFillMode.FILL);
        return switch (shape) {
            case LINE -> List.of(ShapeBuildTypes.ShapeFillMode.FILL);
            case SQUARE, WALL, CIRCLE -> List.of(ShapeBuildTypes.ShapeFillMode.FILL, ShapeBuildTypes.ShapeFillMode.HOLLOW);
            case BOX -> List.of(ShapeBuildTypes.ShapeFillMode.FILL, ShapeBuildTypes.ShapeFillMode.HOLLOW, ShapeBuildTypes.ShapeFillMode.SKELETON);
            default -> List.of(ShapeBuildTypes.ShapeFillMode.FILL);
        };
    }

    // ======================== 数据记录 ========================

    /** 旋转偏移量 */
    public record RotatedOffset(int a, int b) {}

    /** 平面单元格 */
    public record PlaneCell(int a, int b) {}

    private ShapeGeometryUtil() {
        // 工具类，禁止实例化
    }
}
