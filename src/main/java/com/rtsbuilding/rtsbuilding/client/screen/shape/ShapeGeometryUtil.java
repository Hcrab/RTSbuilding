package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

import static com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryPlaneSupport.*;

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
        return buildShapePlan(
                input,
                fillMode,
                BuilderScreenConstants.shapeMaxDimension(),
                BuilderScreenConstants.shapeMaxRadius(),
                MiningLimits.MAX_VOLUME).positions();
    }

    /** 普通形状入口：dimension 与 radius 是独立配置。 */
    public static ShapeGenerationResult buildShapePlan(
            ShapeBuildTypes.Input input,
            ShapeFillMode fillMode,
            int maxDimension,
            int maxRadius,
            int maxTargets) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return new ShapeGenerationResult(ShapeGenerationStatus.EMPTY, List.of(), 0);
        }
        int safeDimension = Math.max(1, maxDimension);
        int safeRadius = Math.max(0, maxRadius);
        ShapeBuildTypes.Input effectiveInput = ShapeSelectionLimiter.clampShapeDimensions(
                input, safeDimension, safeRadius);
        return buildResolvedShapePlan(
                effectiveInput, fillMode, safeDimension - 1, safeRadius, maxTargets);
    }

    /**
     * 为范围挖掘生成已经由服务端配置限幅过的形状。
     *
     * <p>调用方必须先通过 {@link ShapeSelectionLimiter#clampDimensionsAndVolume} 收紧 XYZ 与总体积。
     * 本入口不会再套用范围建造专用的 32 格上限，否则服务端公开的范围挖掘配置无法真正生效。</p>
     */
    public static List<BlockPos> buildRangeDestroyShapePositions(
            ShapeBuildTypes.Input input, ShapeFillMode fillMode) {
        return buildRangeDestroyShapePlan(input, fillMode, MiningLimits.MAX_VOLUME).positions();
    }

    /** 范围破坏入口：输入由范围限制器负责 X/Y/Z+体积，生成器只负责真实目标容量。 */
    public static ShapeGenerationResult buildRangeDestroyShapePlan(
            ShapeBuildTypes.Input input, ShapeFillMode fillMode, int maxTargets) {
        if (input == null || input.pointA() == null || input.pointB() == null || input.shape() == null) {
            return new ShapeGenerationResult(ShapeGenerationStatus.EMPTY, List.of(), 0);
        }
        return buildResolvedShapePlan(input, fillMode, Integer.MAX_VALUE, Integer.MAX_VALUE, maxTargets);
    }

    private static ShapeGenerationResult buildResolvedShapePlan(
            ShapeBuildTypes.Input input, ShapeFillMode fillMode,
            int maxOffset, int maxRadius, int maxTargets) {
        ShapeGeometryBudget.TargetSet targets = new ShapeGeometryBudget.TargetSet(maxTargets);
        BlockPos start = input.pointA();
        BlockPos end = input.pointB();
        ShapeFillMode safeFillMode = fillMode == null ? ShapeFillMode.FILL : fillMode;
        try {
            switch (input.shape()) {
                case LINE -> addLineTargets(targets, start, end, input.connectedLine(), maxOffset);
                case SQUARE -> addSquareTargets(targets, start, end, input.planeFace(), safeFillMode, maxOffset);
                case WALL -> addWallTargets(targets, start, end, input.boxHeightOffset(), safeFillMode,
                        input.connectedLine(), maxOffset);
                case CIRCLE -> addCircleTargets(targets, start, end, input.planeFace(), safeFillMode, maxRadius);
                case CYLINDER -> addCylinderTargets(targets, start, end, input.boxHeightOffset(),
                        input.planeFace(), safeFillMode, maxRadius, maxOffset);
                case BALL -> addBallTargets(targets, start, end, safeFillMode, maxRadius);
                case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), safeFillMode, maxOffset);
                default -> targets.add(start);
            }
        } catch (ShapeGeometryBudget.LimitExceeded exceeded) {
            return tooLarge(exceeded.discoveredTargets);
        }
        return new ShapeGenerationResult(
                targets.isEmpty() ? ShapeGenerationStatus.EMPTY : ShapeGenerationStatus.READY,
                new ArrayList<>(targets), targets.size());
    }

    private static ShapeGenerationResult tooLarge(long discoveredTargets) {
        return new ShapeGenerationResult(ShapeGenerationStatus.TOO_LARGE, List.of(), discoveredTargets);
    }

    public static List<BlockPos> buildAdvancedShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode) {
        return buildAdvancedShapePlan(shape, box, fillMode, Direction.UP, MiningLimits.MAX_VOLUME).positions();
    }

    public static List<BlockPos> buildAdvancedShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode, Direction planeFace) {
        return buildAdvancedShapePlan(shape, box, fillMode, planeFace, MiningLimits.MAX_VOLUME).positions();
    }

    public static ShapeGenerationResult buildAdvancedShapePlan(
            BuildShape shape, RtsCullingBox box, ShapeFillMode fillMode,
            Direction planeFace, int maxTargets) {
        if (shape == null || box == null) {
            return new ShapeGenerationResult(ShapeGenerationStatus.EMPTY, List.of(), 0);
        }
        ShapeGeometryBudget.TargetSet targets = new ShapeGeometryBudget.TargetSet(maxTargets);
        ShapeFillMode safeFillMode = fillMode == null ? ShapeFillMode.FILL : fillMode;
        try {
            ShapeGeometryAdvancedGenerator.addAdvancedShapeTargets(
                    targets, shape, box, safeFillMode,
                    planeFace == null ? Direction.UP : planeFace);
        } catch (ShapeGeometryBudget.LimitExceeded exceeded) {
            return tooLarge(exceeded.discoveredTargets);
        }
        // 有界生成可以跳过内部，但不能改变玩家看到的旧轴向放置顺序。
        List<BlockPos> ordered = new ArrayList<>(targets);
        Comparator<BlockPos> axisOrder = Comparator.<BlockPos>comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
        ordered.sort(shape == BuildShape.CIRCLE ? axisOrder
                : Comparator.<BlockPos>comparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return new ShapeGenerationResult(
                targets.isEmpty() ? ShapeGenerationStatus.EMPTY : ShapeGenerationStatus.READY,
                ordered, targets.size());
    }



    public static List<BlockPos> buildAdvancedRangeDestroyShapePositions(BuildShape shape, RtsCullingBox box,
            ShapeFillMode fillMode) {
        return buildAdvancedShapePlan(shape, box, fillMode, Direction.UP, MiningLimits.MAX_VOLUME).positions();
    }

    // ======================== 单个形状算法 ========================

    /** 生成直线方块（Bresenham 线段近似） */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end) {
        addLineTargets(targets, start, end, false);
    }

    /** 生成直线方块，支持连接模式（斜线断点填充） */
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, boolean connected) {
        addLineTargets(targets, start, end, connected, BuilderScreenConstants.shapeMaxOffset());
    }

    private static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            boolean connected, int maxOffset) {
        int dx = clampSignedOffset((long) end.getX() - start.getX(), maxOffset);
        int dy = clampSignedOffset((long) end.getY() - start.getY(), maxOffset);
        int dz = clampSignedOffset((long) end.getZ() - start.getZ(), maxOffset);
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
            int x = toInt((long) start.getX() + Math.round(dx * t));
            int y = toInt((long) start.getY() + Math.round(dy * t));
            int z = toInt((long) start.getZ() + Math.round(dz * t));
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

        long x = start.getX();
        long y = start.getY();
        long z = start.getZ();
        addConnectedTarget(targets, x, y, z);

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
                    addConnectedTarget(targets, x, y, z);
                    y += sy;
                    errY += adx;
                }
                if (stepZ) {
                    addConnectedTarget(targets, x, y, z);
                    z += sz;
                    errZ += adx;
                }
                addConnectedTarget(targets, x, y, z);
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
                    addConnectedTarget(targets, x, y, z);
                    x += sx;
                    errX += ady;
                }
                if (stepZ) {
                    addConnectedTarget(targets, x, y, z);
                    z += sz;
                    errZ += ady;
                }
                addConnectedTarget(targets, x, y, z);
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
                    addConnectedTarget(targets, x, y, z);
                    x += sx;
                    errX += adz;
                }
                if (stepY) {
                    addConnectedTarget(targets, x, y, z);
                    y += sy;
                    errY += adz;
                }
                addConnectedTarget(targets, x, y, z);
            }
        }
    }

    private static void addConnectedTarget(Set<BlockPos> targets, long x, long y, long z) {
        targets.add(new BlockPos(toInt(x), toInt(y), toInt(z)));
    }

    /** 生成正方形方块 */
    public static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeFillMode fillMode) {
        addSquareTargets(targets, start, end, face, fillMode, BuilderScreenConstants.shapeMaxOffset());
    }

    private static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            Direction face, ShapeFillMode fillMode, int maxOffset) {
        Direction[] axes = resolveShapePlaneAxes(BuildShape.SQUARE, face);
        long dx = (long) end.getX() - start.getX();
        long dy = (long) end.getY() - start.getY();
        long dz = (long) end.getZ() - start.getZ();
        int aOffset = clampSignedOffset(dotDelta(dx, dy, dz, axes[0]), maxOffset);
        int bOffset = clampSignedOffset(dotDelta(dx, dy, dz, axes[1]), maxOffset);
        long width = span(Math.min(0, aOffset), Math.max(0, aOffset));
        long depth = span(Math.min(0, bOffset), Math.max(0, bOffset));
        long expected = fillMode == ShapeFillMode.FILL
                ? saturatedProduct(width, 1L, depth)
                : perimeterCount(width, depth);
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        // 零度普通矩形直接按边界/面积发射，不能为超大空心面分配实心平面。
        ShapeGeometryBudget.TargetSet tmp = new ShapeGeometryBudget.TargetSet(targetLimit(targets));
        int minA = Math.min(0, aOffset);
        int maxA = Math.max(0, aOffset);
        int minB = Math.min(0, bOffset);
        int maxB = Math.max(0, bOffset);
        if (fillMode == ShapeFillMode.FILL) {
            for (int a = minA; a <= maxA; a++) {
                for (int b = minB; b <= maxB; b++) {
                    tmp.add(offsetPos(start, axes[0], a, axes[1], b));
                }
            }
        } else {
            for (int a = minA; a <= maxA; a++) {
                tmp.add(offsetPos(start, axes[0], a, axes[1], minB));
                if (maxB != minB) {
                    tmp.add(offsetPos(start, axes[0], a, axes[1], maxB));
                }
            }
            for (int b = minB + 1; b < maxB; b++) {
                tmp.add(offsetPos(start, axes[0], minA, axes[1], b));
                if (maxA != minA) {
                    tmp.add(offsetPos(start, axes[0], maxA, axes[1], b));
                }
            }
        }
        List<BlockPos> sorted = new ArrayList<>(tmp);
        sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        targets.addAll(sorted);
    }

    /** 生成墙壁方块，支持连接模式 */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        addWallTargets(targets, start, end, heightOffset, fillMode, false);
    }

    /** 生成墙壁方块，支持连接模式（斜线断点填充） */
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode, boolean connected) {
        addWallTargets(targets, start, end, heightOffset, fillMode, connected,
                BuilderScreenConstants.shapeMaxOffset());
    }

    private static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            int heightOffset, ShapeFillMode fillMode, boolean connected, int maxOffset) {
        int horizontalA = clampSignedOffset((long) end.getX() - start.getX(), maxOffset);
        int horizontalB = clampSignedOffset((long) end.getZ() - start.getZ(), maxOffset);
        long lineLength = (long) Math.max(Math.abs(horizontalA), Math.abs(horizontalB)) + 1L;
        long height = (long) Math.abs(clampShapeOffset(heightOffset, maxOffset)) + 1L;
        long minimumTargets = fillMode == ShapeFillMode.FILL
                ? saturatedProduct(lineLength, height, 1L)
                : perimeterCount(lineLength, height);
        if (minimumTargets > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(minimumTargets);
        }

        // 连接线可能比几何线段多出桥接方块；临时基线也必须受同一目标预算约束。
        ShapeGeometryBudget.TargetSet baseLine = new ShapeGeometryBudget.TargetSet(targetLimit(targets));
        addLineTargets(baseLine, start, new BlockPos(end.getX(), start.getY(), end.getZ()), connected, maxOffset);
        if (baseLine.isEmpty()) {
            baseLine.add(start);
        }

        int yOffset = clampShapeOffset(heightOffset, maxOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        List<BlockPos> base = new ArrayList<>(baseLine);
        // 仍从下往上沿基线放置；中间层直接取两根端柱，不扫描空内部。
        for (int iy = minY; iy <= maxY; iy++) {
            if (fillMode == ShapeFillMode.FILL || iy == minY || iy == maxY) {
                for (BlockPos basePos : base) {
                    targets.add(offsetFrom(basePos, 0L, iy, 0L));
                }
            } else {
                targets.add(offsetFrom(base.get(0), 0L, iy, 0L));
                if (base.size() > 1) {
                    targets.add(offsetFrom(base.get(base.size() - 1), 0L, iy, 0L));
                }
            }
        }
    }

    /** 生成圆形方块 */
    public static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeFillMode fillMode) {
        addCircleTargets(targets, start, end, face, fillMode, BuilderScreenConstants.shapeMaxRadius());
    }

    private static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            Direction face, ShapeFillMode fillMode, int maxRadius) {
        int degrees = 0; // 由调用方传入旋转角度
        Direction[] axes = resolveShapePlaneAxes(BuildShape.CIRCLE, face);
        long dx = (long) end.getX() - start.getX();
        long dy = (long) end.getY() - start.getY();
        long dz = (long) end.getZ() - start.getZ();
        long a = dotDelta(dx, dy, dz, axes[0]);
        long b = dotDelta(dx, dy, dz, axes[1]);
        int radius = clampRadius(roundedPlanarRadius(a, b), maxRadius);
        boolean fill = fillMode == ShapeFillMode.FILL;
        long expected = circleCellCount(radius, fill, targetLimit(targets));
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }

        // 先收集到列表，再按距点击点距离排序
        List<BlockPos> positions = new ArrayList<>((int) expected);
        emitCircleCells(positions, start, axes[0], axes[1], radius, fill);
        if (degrees != 0) {
            List<BlockPos> rotated = new ArrayList<>(positions.size());
            for (BlockPos position : positions) {
                long cellA = dotDelta((long) position.getX() - start.getX(),
                        (long) position.getY() - start.getY(),
                        (long) position.getZ() - start.getZ(), axes[0]);
                long cellB = dotDelta((long) position.getX() - start.getX(),
                        (long) position.getY() - start.getY(),
                        (long) position.getZ() - start.getZ(), axes[1]);
                RotatedOffset rotatedOffset = rotatePlaneOffset(toInt(cellA), toInt(cellB), 0.0D, 0.0D, degrees);
                rotated.add(offsetPos(start, axes[0], rotatedOffset.a(), axes[1], rotatedOffset.b()));
            }
            positions = rotated;
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        targets.addAll(positions);
    }

    /** 生成圆柱体方块：圆形底面 + 高度偏移 */
    public static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            Direction face, ShapeFillMode fillMode) {
        addCylinderTargets(targets, start, end, heightOffset, face, fillMode,
                BuilderScreenConstants.shapeMaxRadius(), BuilderScreenConstants.shapeMaxOffset());
    }

    private static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            Direction face, ShapeFillMode fillMode, int maxRadius, int maxOffset) {
        Direction[] axes = resolveShapePlaneAxes(BuildShape.CYLINDER, face);
        Direction normal = normalizePlaneFace(face);
        long dx = (long) end.getX() - start.getX();
        long dy = (long) end.getY() - start.getY();
        long dz = (long) end.getZ() - start.getZ();
        long a = dotDelta(dx, dy, dz, axes[0]);
        long b = dotDelta(dx, dy, dz, axes[1]);
        int radius = clampRadius(roundedPlanarRadius(a, b), maxRadius);
        int yOffset = clampShapeOffset(heightOffset, maxOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        boolean fill = fillMode == ShapeFillMode.FILL;
        boolean singleLayer = minY == maxY;
        long baseCount = circleCellCount(radius, true, targetLimit(targets));
        long shellCount = circleCellCount(radius, false, targetLimit(targets));
        long layerCount = (long) maxY - minY + 1L;
        long expected = fill
                ? saturatedProduct(baseCount, 1L, layerCount)
                : singleLayer
                        ? shellCount
                        : saturatedAdd(
                                saturatedProduct(baseCount, 1L, 2L),
                                saturatedProduct(shellCount, 1L, Math.max(0L, layerCount - 2L)));
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }

        for (int iy = minY; iy <= maxY; iy++) {
            boolean capLayer = iy == minY || iy == maxY;
            List<BlockPos> layerPositions = new ArrayList<>();
            BlockPos layerOrigin = offsetAlong(start, normal, iy);
            boolean fullLayer = fill || (!singleLayer && capLayer);
            emitCircleCells(layerPositions, layerOrigin, axes[0], axes[1], radius, fullLayer);
            layerPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            targets.addAll(layerPositions);
        }
    }

    /** 生成球体方块：A 点为球心，B 点决定半径 */
    public static void addBallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, ShapeFillMode fillMode) {
        addBallTargets(targets, start, end, fillMode, BuilderScreenConstants.shapeMaxRadius());
    }

    private static void addBallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            ShapeFillMode fillMode, int maxRadius) {
        long dx = (long) end.getX() - start.getX();
        long dy = (long) end.getY() - start.getY();
        long dz = (long) end.getZ() - start.getZ();
        int radius = clampRadius(roundedSpatialRadius(dx, dy, dz), maxRadius);
        boolean fill = fillMode == ShapeFillMode.FILL;
        long expected = ballCellCount(radius, fill, targetLimit(targets));
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        List<BlockPos> positions = new ArrayList<>((int) expected);

        for (int y = -radius; y <= radius; y++) {
            long y2 = (long) y * y;
            long outerSquared = (long) radius * radius - y2;
            long innerRadius = Math.max(0, radius - 1);
            long innerSquared = (long) innerRadius * innerRadius - y2;
            int outer = floorSqrt(Math.max(0L, outerSquared));
            int inner = innerSquared <= 0L ? 0 : ceilSqrt(innerSquared);
            for (int x = -outer; x <= outer; x++) {
                long x2 = (long) x * x;
                long zOuterSquared = outerSquared - x2;
                if (zOuterSquared < 0L) {
                    continue;
                }
                int zOuter = floorSqrt(zOuterSquared);
                if (fill) {
                    for (int z = -zOuter; z <= zOuter; z++) {
                        positions.add(offsetFrom(start, x, y, z));
                    }
                } else {
                    long zInnerSquared = innerSquared - x2;
                    int zInner = zInnerSquared <= 0L ? 0 : ceilSqrt(zInnerSquared);
                    if (zInner <= 0) {
                        for (int z = -zOuter; z <= zOuter; z++) {
                            positions.add(offsetFrom(start, x, y, z));
                        }
                    } else {
                        for (int z = -zOuter; z <= -zInner; z++) {
                            positions.add(offsetFrom(start, x, y, z));
                        }
                        for (int z = zInner; z <= zOuter; z++) {
                            positions.add(offsetFrom(start, x, y, z));
                        }
                    }
                }
            }
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        targets.addAll(positions);
    }

    /** 生成立方体方块 */
    public static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeFillMode fillMode) {
        addBoxTargets(targets, start, end, heightOffset, fillMode, BuilderScreenConstants.shapeMaxOffset());
    }

    private static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            int heightOffset, ShapeFillMode fillMode, int maxOffset) {
        int xOffset = clampSignedOffset((long) end.getX() - start.getX(), maxOffset);
        int zOffset = clampSignedOffset((long) end.getZ() - start.getZ(), maxOffset);
        int yOffset = clampShapeOffset(heightOffset, maxOffset);

        int minX = Math.min(0, xOffset);
        int maxX = Math.max(0, xOffset);
        int minZ = Math.min(0, zOffset);
        int maxZ = Math.max(0, zOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        if (fillMode == ShapeFillMode.FILL) {
            long width = span(minX, maxX);
            long height = span(minY, maxY);
            long depth = span(minZ, maxZ);
            long expected = boxTargetCount(width, height, depth, fillMode);
            if (expected > targetLimit(targets)) {
                throw new ShapeGeometryBudget.LimitExceeded(expected);
            }
            for (long iy = minY; iy <= maxY; iy++) {
                List<BlockPos> layerPositions = new ArrayList<>((int) Math.min(width * depth, Integer.MAX_VALUE));
                for (long x = minX; x <= maxX; x++) {
                    for (long z = minZ; z <= maxZ; z++) {
                        layerPositions.add(new BlockPos(toInt((long) start.getX() + x),
                                toInt((long) start.getY() + iy), toInt((long) start.getZ() + z)));
                    }
                }
                layerPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
                targets.addAll(layerPositions);
            }
            return;
        }
        long width = span(minX, maxX);
        long height = span(minY, maxY);
        long depth = span(minZ, maxZ);
        long expected = boxTargetCount(width, height, depth, fillMode);
        if (expected > targetLimit(targets)) {
            throw new ShapeGeometryBudget.LimitExceeded(expected);
        }
        for (long iy = minY; iy <= maxY; iy++) {
            boolean yBoundary = iy == minY || iy == maxY;
            ShapeGeometryBudget.TargetSet layer = new ShapeGeometryBudget.TargetSet(targetLimit(targets));
            if (fillMode == ShapeFillMode.HOLLOW) {
                addBoxHollowLayer(layer, start, minX, maxX, minZ, maxZ, iy, yBoundary);
            } else {
                addBoxSkeletonLayer(layer, start, minX, maxX, minZ, maxZ, iy, yBoundary);
            }
            List<BlockPos> sorted = new ArrayList<>(layer);
            sorted.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            targets.addAll(sorted);
        }
    }

    // ======================== 平面矩形（带旋转） ========================

    /** 生成带旋转的平面矩形方块 */
    public static void addRotatedPlaneRectangleTargets(Set<BlockPos> targets, BlockPos start, Direction axisA, Direction axisB,
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

        /** 兼容旧调用方的圆形平面单元格入口。 */
    public static Set<PlaneCell> buildCircleCells(int radius, boolean fill) {
        return ShapeGeometryPlaneSupport.buildCircleCells(radius, fill);
    }

    /** 兼容旧调用方的旋转矩形单元格入口。 */
    public static Set<PlaneCell> buildRotatedRectangleFillCells(
            int minA, int maxA, int minB, int maxB, int degrees) {
        return ShapeGeometryPlaneSupport.buildRotatedRectangleFillCells(minA, maxA, minB, maxB, degrees);
    }

    public static boolean isPlaneBoundaryCell(Set<PlaneCell> filledCells, PlaneCell cell) {
        return ShapeGeometryPlaneSupport.isPlaneBoundaryCell(filledCells, cell);
    }

    public static boolean isInverseRotatedInsideCellBounds(
            int targetA, int targetB, int minA, int maxA, int minB, int maxB,
            double centerA, double centerB, double cos, double sin) {
        return ShapeGeometryPlaneSupport.isInverseRotatedInsideCellBounds(
                targetA, targetB, minA, maxA, minB, maxB, centerA, centerB, cos, sin);
    }

    public static Set<PlaneCell> fillPlaneInteriorHoles(Set<PlaneCell> filledCells) {
        return ShapeGeometryPlaneSupport.fillPlaneInteriorHoles(filledCells);
    }

    public static int clampShapeOffset(int value) {
        return Mth.clamp(value, -BuilderScreenConstants.shapeMaxOffset(), BuilderScreenConstants.shapeMaxOffset());
    }

    static int clampShapeOffset(int value, int maxOffset) {
        int safeMaxOffset = Math.max(0, maxOffset);
        return Mth.clamp(value, -safeMaxOffset, safeMaxOffset);
    }

    private static int clampSignedOffset(long value, int maxMagnitude) {
        long bound = Math.max(0L, maxMagnitude);
        return toInt(Math.max(-bound, Math.min(bound, value)));
    }

    /** 计算方向上的投影分量 */
    public static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return ShapeGeometryPlaneSupport.toInt((long) dx * axis.getStepX()
                + (long) dy * axis.getStepY() + (long) dz * axis.getStepZ());
    }

    private static long dotDelta(long dx, long dy, long dz, Direction axis) {
        return dx * axis.getStepX() + dy * axis.getStepY() + dz * axis.getStepZ();
    }

    /** 在两个方向轴上偏移位置 */
    public static BlockPos offsetPos(BlockPos origin, Direction axisA, int stepA, Direction axisB, int stepB) {
        long dx = (long) axisA.getStepX() * stepA
                + (long) axisB.getStepX() * stepB;
        long dy = (long) axisA.getStepY() * stepA
                + (long) axisB.getStepY() * stepB;
        long dz = (long) axisA.getStepZ() * stepA
                + (long) axisB.getStepZ() * stepB;
        return offsetFrom(origin, dx, dy, dz);
    }

    /** 按三个坐标差偏移并在绝对坐标处收敛，避免 BlockPos.offset 的 int 回绕。 */
    public static BlockPos offsetPos(BlockPos origin, int dx, int dy, int dz) {
        return offsetFrom(origin, dx, dy, dz);
    }

    private static BlockPos offsetAlong(BlockPos origin, Direction axis, int step) {
        return offsetFrom(origin,
                (long) axis.getStepX() * step,
                (long) axis.getStepY() * step,
                (long) axis.getStepZ() * step);
    }

    private static BlockPos offsetFrom(BlockPos origin, long dx, long dy, long dz) {
        return new BlockPos(toInt((long) origin.getX() + dx),
                toInt((long) origin.getY() + dy),
                toInt((long) origin.getZ() + dz));
    }

    private static int toInt(long value) {
        return ShapeGeometryPlaneSupport.toInt(value);
    }

    /** 将 long 坐标差收敛到 BlockPos 可表达的整数范围，避免边界加减回绕。 */
    public static int clampCoordinate(long value) {
        return ShapeGeometryPlaneSupport.toInt(value);
    }

    static Direction normalizePlaneFace(Direction face) {
        return face == null ? Direction.UP : face;
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
    public static Direction resolveShapeBuildFace(BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape == null) return clickedFace == null ? Direction.UP : clickedFace;
        return switch (shape) {
            case LINE, SQUARE, WALL, CYLINDER, BOX -> Direction.UP;
            default -> clickedFace == null ? Direction.UP : clickedFace;
        };
    }

    /** 解析形状的放置面 */
    public static Direction resolveShapePlacementFace(BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (clickedFace != null) return clickedFace;
        return resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    /** 解析形状的平面轴向 */
    public static Direction[] resolveShapePlaneAxes(BuildShape shape, Direction face) {
        if (shape == BuildShape.SQUARE || shape == BuildShape.BOX) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (shape == BuildShape.WALL) {
            return new Direction[] { Direction.EAST, Direction.SOUTH };
        }
        if (face == null) return new Direction[] { Direction.EAST, Direction.SOUTH };
        return switch (face.getAxis()) {
            case Y -> new Direction[] { Direction.EAST, Direction.SOUTH };
            case X -> new Direction[] { Direction.UP, Direction.SOUTH };
            case Z -> new Direction[] { Direction.EAST, Direction.UP };
        };
    }

    /** 判断形状是否需要第三阶段高度调整。 */
    public static boolean requiresThirdPoint(BuildShape shape) {
        return shape == BuildShape.CYLINDER || shape == BuildShape.BOX;
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
    public static List<ShapeFillMode> availableFillModes(BuildShape shape) {
        if (shape == null) return List.of(ShapeFillMode.FILL);
        return switch (shape) {
            case LINE -> List.of(ShapeFillMode.FILL);
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL -> List.of(ShapeFillMode.FILL, ShapeFillMode.HOLLOW);
            case BOX -> List.of(ShapeFillMode.FILL, ShapeFillMode.HOLLOW, ShapeFillMode.SKELETON);
            default -> List.of(ShapeFillMode.FILL);
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
