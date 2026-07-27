package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;

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
    public static List<BlockPos> buildShapePositions(ShapeBuildTypes.Input input, ShapeFillMode fillMode) {
        return buildShapePositions(input, fillMode, true);
    }

    /**
     * 为范围挖掘生成已经由服务端配置限幅过的形状。
     *
     * <p>调用方必须先通过 {@link ShapeSelectionLimiter#clampDimensionsAndVolume} 收紧 XYZ 与总体积。
     * 本入口不会再套用范围建造专用的 32 格上限，否则服务端公开的范围挖掘配置无法真正生效。</p>
     */
    public static List<BlockPos> buildRangeDestroyShapePositions(
            ShapeBuildTypes.Input input, ShapeFillMode fillMode) {
        return buildShapePositions(input, fillMode, false);
    }

    private static List<BlockPos> buildShapePositions(
            ShapeBuildTypes.Input input, ShapeFillMode fillMode, boolean enforceBuildCaps) {
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        BlockPos start = input.pointA();
        BlockPos end = input.pointB();
        int maxOffset = enforceBuildCaps ? BuilderScreenConstants.SHAPE_MAX_OFFSET : Integer.MAX_VALUE;
        int maxRadius = enforceBuildCaps ? BuilderScreenConstants.SHAPE_MAX_RADIUS : Integer.MAX_VALUE;
        switch (input.shape()) {
            case LINE -> addLineTargets(targets, start, end, input.connectedLine(), maxOffset);
            case SQUARE -> addSquareTargets(targets, start, end, input.planeFace(), fillMode, maxOffset);
            case WALL -> addWallTargets(targets, start, end, input.boxHeightOffset(), fillMode,
                    input.connectedLine(), maxOffset);
            case CIRCLE -> addCircleTargets(targets, start, end, input.planeFace(), fillMode, maxRadius);
            case CYLINDER -> addCylinderTargets(targets, start, end, input.boxHeightOffset(),
                    input.planeFace(), fillMode, maxRadius, maxOffset);
            case BALL -> addBallTargets(targets, start, end, fillMode, maxRadius);
            case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), fillMode, maxOffset);
            default -> targets.add(start);
        }
        return new ArrayList<>(targets);
    }

    public static List<BlockPos> buildAdvancedShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode) {
        return buildAdvancedShapePositions(shape, box, fillMode, EnumFacing.UP);
    }

    public static List<BlockPos> buildAdvancedShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode, EnumFacing planeFace) {
        if (shape == null || box == null) {
            return java.util.Collections.emptyList();
        }
        LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
        switch (shape) {
            case SQUARE -> addAdvancedSquareTargets(targets, box, fillMode);
            case WALL -> addAdvancedWallTargets(targets, box, fillMode);
            case CIRCLE -> addAdvancedEllipseTargets(targets, box, fillMode, planeFace);
            case CYLINDER -> addAdvancedEllipticCylinderTargets(targets, box, fillMode, planeFace);
            case BALL -> addAdvancedEllipsoidTargets(targets, box, fillMode);
            case BOX -> addAdvancedBoxLikeTargets(targets, box, fillMode);
            default -> {}
        }
        return new ArrayList<>(targets);
    }

    public static List<BlockPos> buildAdvancedRangeDestroyShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode) {
        return buildAdvancedShapePositions(shape, box, fillMode);
    }

    // ======================== 单个形状算法 ========================

    /** 生成直线方块（Bresenham 线段近似） */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end) {
        addLineTargets(targets, start, end, false);
    }

    /** 生成直线方块，支持连接模式（斜线断点填充） */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, boolean connected) {
        addLineTargets(targets, start, end, connected, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            boolean connected, int maxOffset) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps <= 0) {
            targets.add(start);
            return;
        }

        if (steps > maxOffset) {
            double scale = maxOffset / (double) steps;
            dx = (int) Math.round(dx * scale);
            dy = (int) Math.round(dy * scale);
            dz = (int) Math.round(dz * scale);
            steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        }

        if (connected) {
            // 连接模式：使用3D Bresenham变体，确保连续方块之间总是面相邻（6-连通性）
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
     * 连接模式直线算法：沿最长轴逐格步进，每次步进次要轴之前先添加连接方块，
     * 确保连续方块之间总是面相邻（6-连通性）。
     * <p>例如从 (0,0,0) 到 (3,3,0) 会生成：
     * (0,0,0), (1,0,0), (1,1,0), (2,1,0), (2,2,0), (3,2,0), (3,3,0)</p>
     * <p>核心思路：先步进主轴，在步进次要轴之前，将当前位置的方块加入（此时主轴已前进但次要轴未动），
     * 这个方块就是连接斜对角两个方块的"桥梁"。</p>
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
            // X 为主轴：先步进 X，在 Y/Z 步进之前添加连接方块
            int errY = adx / 2;
            int errZ = adx / 2;
            for (int i = 0; i < adx; i++) {
                errY -= ady;
                errZ -= adz;
                boolean stepY = errY < 0;
                boolean stepZ = errZ < 0;
                x += sx;
                // 步进次要轴之前：添加连接方块（主轴已前进，次要轴尚未步进）
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
            // Y 为主轴：先步进 Y，在 X/Z 步进之前添加连接方块
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
            // Z 为主轴：先步进 Z，在 X/Y 步进之前添加连接方块
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
    public static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, EnumFacing face, ShapeFillMode fillMode) {
        addSquareTargets(targets, start, end, face, fillMode, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            EnumFacing face, ShapeFillMode fillMode, int maxOffset) {
        EnumFacing[] axes = resolveShapePlaneAxes(BuildShape.SQUARE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]), maxOffset);
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]), maxOffset);
        // 先收集到临时集，再按距点击点距离排序
        LinkedHashSet<BlockPos> tmp = new LinkedHashSet<>();
        addRotatedPlaneRectangleTargets(tmp, start, axes[0], axes[1], aOffset, bOffset, fillMode, 0);
        List<BlockPos> sorted = new ArrayList<>(tmp);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distanceSq(start)));
        targets.addAll(sorted);
    }

    /** 生成墙壁方块，支持连接模式 */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        addWallTargets(targets, start, end, heightOffset, fillMode, false);
    }

    /** 生成墙壁方块，支持连接模式（斜线断点填充） */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode, boolean connected) {
        addWallTargets(targets, start, end, heightOffset, fillMode, connected,
                BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            int heightOffset, ShapeFillMode fillMode, boolean connected, int maxOffset) {
        LinkedHashSet<BlockPos> baseLine = new LinkedHashSet<>();
        addLineTargets(baseLine, start, new BlockPos(end.getX(), start.getY(), end.getZ()), connected, maxOffset);
        if (baseLine.isEmpty()) {
            baseLine.add(start);
        }

        int yOffset = clampShapeOffset(heightOffset, maxOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        List<BlockPos> base = new ArrayList<>(baseLine);
        // 从下往上逐层放置
        for (int iy = minY; iy <= maxY; iy++) {
            for (int i = 0; i < base.size(); i++) {
                BlockPos basePos = base.get(i);
                boolean endColumn = i == 0 || i == base.size() - 1;
                if (fillMode != ShapeFillMode.FILL && !endColumn && iy != minY && iy != maxY) {
                    continue;
                }
                targets.add(basePos.up(iy));
            }
        }
    }

    /** 生成圆形方块 */
    public static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, EnumFacing face, ShapeFillMode fillMode) {
        addCircleTargets(targets, start, end, face, fillMode, BuilderScreenConstants.SHAPE_MAX_RADIUS);
    }

    private static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            EnumFacing face, ShapeFillMode fillMode, int maxRadius) {
        int degrees = 0; // 由调用方传入旋转角度
        EnumFacing[] axes = resolveShapePlaneAxes(BuildShape.CIRCLE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = MathHelper.clamp((int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b))),
                0, maxRadius);
        Set<PlaneCell> rotatedCells = new HashSet<>();
        for (PlaneCell cell : buildCircleCells(radius, fillMode == ShapeFillMode.FILL)) {
            RotatedOffset rotated = rotatePlaneOffset(cell.a(), cell.b(), 0.0D, 0.0D, degrees);
            rotatedCells.add(new PlaneCell(rotated.a(), rotated.b()));
        }

        // 先收集到列表，再按距点击点距离排序
        List<BlockPos> positions = new ArrayList<>();
        for (PlaneCell cell : rotatedCells) {
            positions.add(offsetPos(start, axes[0], cell.a(), axes[1], cell.b()));
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distanceSq(start)));
        targets.addAll(positions);
    }

    /** 生成圆柱体方块：圆形底面 + 高度偏移 */
    public static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            EnumFacing face, ShapeFillMode fillMode) {
        addCylinderTargets(targets, start, end, heightOffset, face, fillMode,
                BuilderScreenConstants.SHAPE_MAX_RADIUS, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            EnumFacing face, ShapeFillMode fillMode, int maxRadius, int maxOffset) {
        EnumFacing[] axes = resolveShapePlaneAxes(BuildShape.CYLINDER, face);
        EnumFacing normal = normalizePlaneFace(face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = MathHelper.clamp((int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b))),
                0, maxRadius);
        Set<PlaneCell> filledBase = buildCircleCells(radius, true);
        Set<PlaneCell> shellBase = buildCircleCells(radius, false);
        int yOffset = clampShapeOffset(heightOffset, maxOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        boolean fill = fillMode == ShapeFillMode.FILL;
        boolean singleLayer = minY == maxY;

        for (int iy = minY; iy <= maxY; iy++) {
            boolean capLayer = iy == minY || iy == maxY;
            List<BlockPos> layerPositions = new ArrayList<>();
            BlockPos layerOrigin = offsetAlong(start, normal, iy);
            for (PlaneCell cell : filledBase) {
                if (fill || (!singleLayer && capLayer) || shellBase.contains(cell)) {
                    layerPositions.add(offsetPos(layerOrigin, axes[0], cell.a(), axes[1], cell.b()));
                }
            }
            layerPositions.sort(Comparator.comparingDouble(pos -> pos.distanceSq(start)));
            targets.addAll(layerPositions);
        }
    }

    /** 生成球体方块：A 点为球心，B 点决定半径 */
    public static void addBallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, ShapeFillMode fillMode) {
        addBallTargets(targets, start, end, fillMode, BuilderScreenConstants.SHAPE_MAX_RADIUS);
    }

    private static void addBallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            ShapeFillMode fillMode, int maxRadius) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int radius = MathHelper.clamp((int) Math.round(Math.sqrt(
                dx * (double) dx + dy * (double) dy + dz * (double) dz)),
                0, maxRadius);
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        boolean fill = fillMode == ShapeFillMode.FILL;
        List<BlockPos> positions = new ArrayList<>();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int dist2 = (x * x) + (y * y) + (z * z);
                    if (dist2 <= outer2 && (fill || dist2 >= inner2)) {
                        positions.add(start.add(x, y, z));
                    }
                }
            }
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distanceSq(start)));
        targets.addAll(positions);
    }

    /** 生成立方体方块 */
    public static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        addBoxTargets(targets, start, end, heightOffset, fillMode, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            int heightOffset, ShapeFillMode fillMode, int maxOffset) {
        int degrees = 0; // 由调用方传入旋转角度
        int xOffset = clampShapeOffset(end.getX() - start.getX(), maxOffset);
        int zOffset = clampShapeOffset(end.getZ() - start.getZ(), maxOffset);
        int yOffset = clampShapeOffset(heightOffset, maxOffset);

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

        if (fillMode == ShapeFillMode.FILL) {
            // 从下往上逐层放置，每层内按距点击点距离排序
            for (int iy = minY; iy <= maxY; iy++) {
                List<BlockPos> layerPositions = new ArrayList<>();
                for (PlaneCell cell : rotatedFootprint) {
                    layerPositions.add(start.add(cell.a(), iy, cell.b()));
                }
                layerPositions.sort(Comparator.comparingDouble(pos -> pos.distanceSq(start)));
                targets.addAll(layerPositions);
            }
            return;
        }

        // HOLLOW / SKELETON: 先构建完整体积集以判断边界
        Set<BlockPos> fullVolume = new HashSet<>(rotatedFootprint.size() * Math.max(1, (maxY - minY) + 1));
        for (PlaneCell cell : rotatedFootprint) {
            for (int iy = minY; iy <= maxY; iy++) {
                fullVolume.add(start.add(cell.a(), iy, cell.b()));
            }
        }

        // 先收集所有边界方块
        Set<BlockPos> boundary = new HashSet<>();
        for (BlockPos pos : fullVolume) {
            boolean xBoundary = !fullVolume.contains(pos.east()) || !fullVolume.contains(pos.west());
            boolean yBoundary = !fullVolume.contains(pos.up()) || !fullVolume.contains(pos.down());
            boolean zBoundary = !fullVolume.contains(pos.north()) || !fullVolume.contains(pos.south());
            int boundaryAxes = (xBoundary ? 1 : 0) + (yBoundary ? 1 : 0) + (zBoundary ? 1 : 0);
            if (fillMode == ShapeFillMode.HOLLOW) {
                if (boundaryAxes >= 1) {
                    boundary.add(pos);
                }
            } else if (boundaryAxes >= 2) {
                boundary.add(pos);
            }
        }

        // 从下往上逐层添加边界方块，每层内按距点击点距离排序
        for (int iy = minY; iy <= maxY; iy++) {
            List<BlockPos> layerPositions = new ArrayList<>();
            for (PlaneCell cell : rotatedFootprint) {
                BlockPos pos = start.add(cell.a(), iy, cell.b());
                if (boundary.contains(pos)) {
                    layerPositions.add(pos);
                }
            }
            layerPositions.sort(Comparator.comparingDouble(p -> p.distanceSq(start)));
            targets.addAll(layerPositions);
        }
    }

    // ======================== 平面矩形（带旋转） ========================

    /** 生成带旋转的平面矩形方块 */
    public static void addRotatedPlaneRectangleTargets(Set<BlockPos> targets, BlockPos start, EnumFacing axisA, EnumFacing axisB,
            int aOffset, int bOffset, ShapeFillMode fillMode, int degrees) {
        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);
        Set<PlaneCell> filledCells = buildRotatedRectangleFillCells(minA, maxA, minB, maxB, degrees);
        for (PlaneCell cell : filledCells) {
            if (fillMode != ShapeFillMode.FILL && isPlaneBoundaryCell(filledCells, cell)) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
                continue;
            }
            if (fillMode == ShapeFillMode.FILL) {
                targets.add(offsetPos(start, axisA, cell.a(), axisB, cell.b()));
            }
        }
    }

    // ======================== 实用方法 ========================

    /** 构建圆形平面单元格；fill=false 时只返回外圈。 */
    public static Set<PlaneCell> buildCircleCells(int radius, boolean fill) {
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        Set<PlaneCell> cells = new HashSet<>();
        for (int a = -radius; a <= radius; a++) {
            for (int b = -radius; b <= radius; b++) {
                int dist2 = (a * a) + (b * b);
                if (dist2 <= outer2 && (fill || dist2 >= inner2)) {
                    cells.add(new PlaneCell(a, b));
                }
            }
        }
        return fill ? fillPlaneInteriorHoles(cells) : cells;
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
            return filledCells == null ? java.util.Collections.emptySet() : filledCells;
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
    private static void addAdvancedSquareTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        int y = box.min().getY();
        for (int x = box.min().getX(); x <= box.max().getX(); x++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                boolean boundary = x == box.min().getX() || x == box.max().getX()
                        || z == box.min().getZ() || z == box.max().getZ();
                if (fillMode == ShapeFillMode.FILL || boundary) {
                    targets.add(new BlockPos(x, y, z));
                }
            }
        }
    }

    private static void addAdvancedBoxLikeTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                    int boundaryAxes = 0;
                    if (x == box.min().getX() || x == box.max().getX()) boundaryAxes++;
                    if (y == box.min().getY() || y == box.max().getY()) boundaryAxes++;
                    if (z == box.min().getZ() || z == box.max().getZ()) boundaryAxes++;
                    if (fillMode == ShapeFillMode.FILL
                            || (fillMode == ShapeFillMode.HOLLOW && boundaryAxes >= 1)
                            || (fillMode == ShapeFillMode.SKELETON && boundaryAxes >= 2)) {
                        targets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private static void addAdvancedWallTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        boolean useX = box.width() >= box.depth();
        int fixedX = box.min().getX();
        int fixedZ = box.min().getZ();
        if (useX) {
            for (int y = box.min().getY(); y <= box.max().getY(); y++) {
                for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                    boolean boundary = y == box.min().getY() || y == box.max().getY()
                            || x == box.min().getX() || x == box.max().getX();
                    if (fillMode == ShapeFillMode.FILL || boundary) {
                        targets.add(new BlockPos(x, y, fixedZ));
                    }
                }
            }
            return;
        }
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                boolean boundary = y == box.min().getY() || y == box.max().getY()
                        || z == box.min().getZ() || z == box.max().getZ();
                if (fillMode == ShapeFillMode.FILL || boundary) {
                    targets.add(new BlockPos(fixedX, y, z));
                }
            }
        }
    }

    private static void addAdvancedEllipseTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode,
            EnumFacing planeFace) {
        EnumFacing[] axes = resolveShapePlaneAxes(BuildShape.CIRCLE, planeFace);
        EnumFacing normal = normalizePlaneFace(planeFace);
        int fixedNormal = minCoord(box, normal.getAxis());
        for (int x = box.min().getX(); x <= box.max().getX(); x++) {
            for (int y = box.min().getY(); y <= box.max().getY(); y++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (coord(pos, normal.getAxis()) != fixedNormal || !insideEllipseCell(pos, box, axes)) {
                        continue;
                    }
                    boolean boundary = !insideEllipseCell(offsetAlong(pos, axes[0], -1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[0], 1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[1], -1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[1], 1), box, axes);
                    if (fillMode == ShapeFillMode.FILL || boundary) {
                        targets.add(pos);
                    }
                }
            }
        }
    }

    private static void addAdvancedEllipticCylinderTargets(Set<BlockPos> targets, RtsCullingBox box,
            ShapeFillMode fillMode, EnumFacing planeFace) {
        EnumFacing[] axes = resolveShapePlaneAxes(BuildShape.CYLINDER, planeFace);
        EnumFacing normal = normalizePlaneFace(planeFace);
        int normalMin = minCoord(box, normal.getAxis());
        int normalMax = maxCoord(box, normal.getAxis());
        boolean singleLayer = normalMin == normalMax;
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!insideEllipseCell(pos, box, axes)) {
                        continue;
                    }
                    int normalCoord = coord(pos, normal.getAxis());
                    boolean capLayer = normalCoord == normalMin || normalCoord == normalMax;
                    boolean sideBoundary = !insideEllipseCell(offsetAlong(pos, axes[0], -1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[0], 1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[1], -1), box, axes)
                            || !insideEllipseCell(offsetAlong(pos, axes[1], 1), box, axes);
                    if (fillMode == ShapeFillMode.FILL || (!singleLayer && capLayer) || sideBoundary) {
                        targets.add(pos);
                    }
                }
            }
        }
    }

    private static void addAdvancedEllipsoidTargets(Set<BlockPos> targets, RtsCullingBox box, ShapeFillMode fillMode) {
        for (int y = box.min().getY(); y <= box.max().getY(); y++) {
            for (int x = box.min().getX(); x <= box.max().getX(); x++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                    if (!insideEllipsoidCell(x, y, z, box)) {
                        continue;
                    }
                    boolean boundary = !insideEllipsoidCell(x - 1, y, z, box)
                            || !insideEllipsoidCell(x + 1, y, z, box)
                            || !insideEllipsoidCell(x, y - 1, z, box)
                            || !insideEllipsoidCell(x, y + 1, z, box)
                            || !insideEllipsoidCell(x, y, z - 1, box)
                            || !insideEllipsoidCell(x, y, z + 1, box);
                    if (fillMode == ShapeFillMode.FILL || boundary) {
                        targets.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    private static boolean insideEllipseCell(int x, int z, RtsCullingBox box) {
        return normalizedCellDistance(x, box.min().getX(), box.max().getX())
                + normalizedCellDistance(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    private static boolean insideEllipseCell(BlockPos pos, RtsCullingBox box, EnumFacing[] axes) {
        return normalizedCellDistance(coord(pos, axes[0].getAxis()),
                minCoord(box, axes[0].getAxis()), maxCoord(box, axes[0].getAxis()))
                + normalizedCellDistance(coord(pos, axes[1].getAxis()),
                        minCoord(box, axes[1].getAxis()), maxCoord(box, axes[1].getAxis())) <= 1.0D;
    }

    private static boolean insideEllipsoidCell(int x, int y, int z, RtsCullingBox box) {
        return normalizedCellDistance(x, box.min().getX(), box.max().getX())
                + normalizedCellDistance(y, box.min().getY(), box.max().getY())
                + normalizedCellDistance(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    private static double normalizedCellDistance(int value, int min, int max) {
        if (min >= max) {
            return value == min ? 0.0D : Double.POSITIVE_INFINITY;
        }
        double center = (min + max + 1) * 0.5D;
        double radius = ((max - min) + 1) * 0.5D;
        double delta = ((value + 0.5D) - center) / radius;
        return delta * delta;
    }

    public static int clampShapeOffset(int value) {
        return MathHelper.clamp(value, -BuilderScreenConstants.SHAPE_MAX_OFFSET, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    private static int clampShapeOffset(int value, int maxOffset) {
        int safeMaxOffset = Math.max(0, maxOffset);
        return MathHelper.clamp(value, -safeMaxOffset, safeMaxOffset);
    }

    /** 计算方向上的投影分量 */
    public static int dotDelta(int dx, int dy, int dz, EnumFacing axis) {
        return (dx * axis.getXOffset()) + (dy * axis.getYOffset()) + (dz * axis.getZOffset());
    }

    /** 在两个方向轴上偏移位置 */
    public static BlockPos offsetPos(BlockPos origin, EnumFacing axisA, int stepA, EnumFacing axisB, int stepB) {
        int dx = (axisA.getXOffset() * stepA) + (axisB.getXOffset() * stepB);
        int dy = (axisA.getYOffset() * stepA) + (axisB.getYOffset() * stepB);
        int dz = (axisA.getZOffset() * stepA) + (axisB.getZOffset() * stepB);
        return origin.add(dx, dy, dz);
    }

    private static BlockPos offsetAlong(BlockPos origin, EnumFacing axis, int step) {
        return origin.add(axis.getXOffset() * step, axis.getYOffset() * step, axis.getZOffset() * step);
    }

    private static EnumFacing normalizePlaneFace(EnumFacing face) {
        return face == null ? EnumFacing.UP : face;
    }

    private static int coord(BlockPos pos, EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    private static int minCoord(RtsCullingBox box, EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> box.min().getX();
            case Y -> box.min().getY();
            case Z -> box.min().getZ();
        };
    }

    private static int maxCoord(RtsCullingBox box, EnumFacing.Axis axis) {
        return switch (axis) {
            case X -> box.max().getX();
            case Y -> box.max().getY();
            case Z -> box.max().getZ();
        };
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
    public static EnumFacing resolveShapeBuildFace(BuildShape shape, EnumFacing clickedFace, Vec3d rayDir) {
        if (shape == null) return clickedFace == null ? EnumFacing.UP : clickedFace;
        return switch (shape) {
            case LINE, SQUARE, WALL, CYLINDER, BOX -> EnumFacing.UP;
            default -> clickedFace == null ? EnumFacing.UP : clickedFace;
        };
    }

    /** 解析形状的放置面 */
    public static EnumFacing resolveShapePlacementFace(BuildShape shape, EnumFacing clickedFace, Vec3d rayDir) {
        if (clickedFace != null) return clickedFace;
        return resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    /** 解析形状的平面轴向 */
    public static EnumFacing[] resolveShapePlaneAxes(BuildShape shape, EnumFacing face) {
        if (shape == BuildShape.SQUARE || shape == BuildShape.BOX) {
            return new EnumFacing[] { EnumFacing.EAST, EnumFacing.SOUTH };
        }
        if (shape == BuildShape.WALL) {
            return new EnumFacing[] { EnumFacing.EAST, EnumFacing.SOUTH };
        }
        if (face == null) return new EnumFacing[] { EnumFacing.EAST, EnumFacing.SOUTH };
        return switch (face.getAxis()) {
            case Y -> new EnumFacing[] { EnumFacing.EAST, EnumFacing.SOUTH };
            case X -> new EnumFacing[] { EnumFacing.UP, EnumFacing.SOUTH };
            case Z -> new EnumFacing[] { EnumFacing.EAST, EnumFacing.UP };
        };
    }

    /** 判断形状是否需要第三阶段高度调整。 */
    public static boolean requiresThirdPoint(BuildShape shape) {
        return shape == BuildShape.CYLINDER || shape == BuildShape.BOX;
    }

    // ======================== 放置命中结果生成 ========================

    /** 创建形状放置的 RayTraceResult */
    public static RayTraceResult createShapePlacementHit(BlockPos pos, EnumFacing face) {
        Vec3d faceNormal = new Vec3d(face.getDirectionVec());
        Vec3d hitVec = new Vec3d(pos).add(0.5D, 0.5D, 0.5D).add(faceNormal.scale(0.5D));
        return new RayTraceResult(hitVec, face, pos);
    }

    // ======================== 可用填充模式 ========================

    /** 获取形状的可用填充模式列表 */
    public static List<ShapeFillMode> availableFillModes(BuildShape shape) {
        if (shape == null) return java.util.Collections.singletonList(ShapeFillMode.FILL);
        return switch (shape) {
            case LINE -> java.util.Collections.singletonList(ShapeFillMode.FILL);
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL -> java.util.Collections.unmodifiableList(
                    java.util.Arrays.asList(ShapeFillMode.FILL, ShapeFillMode.HOLLOW));
            case BOX -> java.util.Collections.unmodifiableList(
                    java.util.Arrays.asList(ShapeFillMode.FILL, ShapeFillMode.HOLLOW, ShapeFillMode.SKELETON));
            default -> java.util.Collections.singletonList(ShapeFillMode.FILL);
        };
    }

    // ======================== 数据记录 ========================

    /** 旋转偏移量 */
    public static final class RotatedOffset {
        private final int a;
        private final int b;
        public RotatedOffset(int a, int b) { this.a = a; this.b = b; }
        public int a() { return this.a; }
        public int b() { return this.b; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof RotatedOffset)) return false;
            RotatedOffset that = (RotatedOffset) other;
            return this.a == that.a && this.b == that.b;
        }
        @Override public int hashCode() { return 31 * this.a + this.b; }
    }

    /** 平面单元格 */
    public static final class PlaneCell {
        private final int a;
        private final int b;
        public PlaneCell(int a, int b) { this.a = a; this.b = b; }
        public int a() { return this.a; }
        public int b() { return this.b; }
        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof PlaneCell)) return false;
            PlaneCell that = (PlaneCell) other;
            return this.a == that.a && this.b == that.b;
        }
        @Override public int hashCode() { return 31 * this.a + this.b; }
    }

    private ShapeGeometryUtil() {
        // 工具类，禁止实例化
    }
}
