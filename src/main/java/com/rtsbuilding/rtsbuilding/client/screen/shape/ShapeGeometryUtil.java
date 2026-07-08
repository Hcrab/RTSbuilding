package com.rtsbuilding.rtsbuilding.client.screen.shape;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 褰㈢姸鍑犱綍璁＄畻宸ュ叿绫汇??
 * <p>
 * 鎻愪緵鍚勭寤洪€犲舰鐘讹紙鐩寸嚎銆佹柟褰€佸澹併€佸渾褰€佺珛鏂逛綋锛夌殑鏂瑰潡浣嶇疆璁＄畻??
 * 浠ュ強褰㈢姸鏃嬭浆銆侀潰鏈濆悜瑙ｆ瀽銆佸～鍏呮ā寮忓鐞嗙瓑绾嚑浣曡繍绠椼??
 * 鎵€鏈夋柟娉曞潎涓洪潤鎬佹棤鐘舵€佹柟娉曘€?
 */
public final class ShapeGeometryUtil {

    // ======================== 褰㈢姸鏀剧疆鐩爣鐢熸垚 ========================

    /**
     * 鏍规嵁褰㈢姸鏋勫缓杈撳叆鍜屽～鍏呮ā寮忕敓鎴愭墍鏈夌洰鏍囨柟鍧椾綅缃??
     *
     * @param input    褰㈢姸鏋勫缓杈撳叆锛堝舰鐘剁被鍨嬨€侀敋鐐圭瓑??
     * @param fillMode 濉厖妯″紡锛堝疄蹇冦€佺┖蹇冦€侀鏋讹級
     * @return 鐩爣鏂瑰潡浣嶇疆鍒???
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
            case CYLINDER -> addCylinderTargets(targets, start, end, input.boxHeightOffset(), input.planeFace(), fillMode);
            case BALL -> addBallTargets(targets, start, end, fillMode);
            case BOX -> addBoxTargets(targets, start, end, input.boxHeightOffset(), fillMode);
            default -> targets.add(start);
        }
        return new ArrayList<>(targets);
    }

    // ======================== 鍗曚釜褰㈢姸绠楁??========================

    /** 鐢熸垚鐩寸嚎鏂瑰潡锛圔resenham 绾挎杩戜技??*/
    public static void addLineTargets(Set<BlockPos> targets, BlockPos start, BlockPos end) {
        addLineTargets(targets, start, end, false);
    }

    /** 鐢熸垚鐩寸嚎鏂瑰潡锛岃繛鎺ユā寮忎細濉叆妗ユ帴鏂瑰潡锛岄伩鍏嶆枩鍚戠嚎娈垫柇寮€??*/
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

    /** 鐢熸垚姝ｆ柟褰㈡柟鍧?*/
    public static void addSquareTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeBuildTypes.ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.SQUARE, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int aOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[0]));
        int bOffset = clampShapeOffset(dotDelta(dx, dy, dz, axes[1]));
        addRotatedPlaneRectangleTargets(targets, start, axes[0], axes[1], aOffset, bOffset, fillMode, 0);
    }

    /** 鐢熸垚澧欏鏂瑰??*/
    public static void addWallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeBuildTypes.ShapeFillMode fillMode) {
        addWallTargets(targets, start, end, heightOffset, fillMode, false);
    }

    /** 鐢熸垚澧欏鏂瑰潡锛岃繛鎺ユā寮忎細璁╁簳閮ㄧ嚎娈典繚鎸侀潰鐩搁偦??*/
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

    /** 鐢熸垚鍦嗗舰鏂瑰??*/
    public static void addCircleTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, Direction face, ShapeBuildTypes.ShapeFillMode fillMode) {
        int degrees = 0; // 鐢辫皟鐢ㄦ柟浼犲叆鏃嬭浆瑙掑??
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

    /** 鐢熸垚绔嬫柟浣撴柟鍧?*/
    /** 生成圆柱体方块：圆形底面由第二点决定，高度由滚轮/高度偏移决定。 */
    public static void addCylinderTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset,
            Direction face, ShapeBuildTypes.ShapeFillMode fillMode) {
        Direction[] axes = resolveShapePlaneAxes(ClientRtsController.BuildShape.CYLINDER, face);
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int a = dotDelta(dx, dy, dz, axes[0]);
        int b = dotDelta(dx, dy, dz, axes[1]);
        int radius = Mth.clamp((int) Math.round(Math.sqrt((a * (double) a) + (b * (double) b))),
                0, BuilderScreenConstants.SHAPE_MAX_RADIUS);
        Set<PlaneCell> filledBase = buildCircleCells(radius, true);
        Set<PlaneCell> shellBase = buildCircleCells(radius, false);
        int yOffset = clampShapeOffset(heightOffset);
        int minY = Math.min(0, yOffset);
        int maxY = Math.max(0, yOffset);
        boolean fill = fillMode == ShapeBuildTypes.ShapeFillMode.FILL;
        boolean singleLayer = minY == maxY;

        for (int iy = minY; iy <= maxY; iy++) {
            boolean capLayer = iy == minY || iy == maxY;
            List<BlockPos> layerPositions = new ArrayList<>();
            for (PlaneCell cell : filledBase) {
                if (fill || (!singleLayer && capLayer) || shellBase.contains(cell)) {
                    layerPositions.add(offsetPos(start.above(iy), axes[0], cell.a(), axes[1], cell.b()));
                }
            }
            layerPositions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
            targets.addAll(layerPositions);
        }
    }

    /** 生成球体方块：A 点为球心，B 点到 A 点的距离为半径。 */
    public static void addBallTargets(Set<BlockPos> targets, BlockPos start, BlockPos end,
            ShapeBuildTypes.ShapeFillMode fillMode) {
        int dx = end.getX() - start.getX();
        int dy = end.getY() - start.getY();
        int dz = end.getZ() - start.getZ();
        int radius = Mth.clamp((int) Math.round(Math.sqrt(
                dx * (double) dx + dy * (double) dy + dz * (double) dz)),
                0, BuilderScreenConstants.SHAPE_MAX_RADIUS);
        int outer2 = radius * radius;
        int inner = Math.max(0, radius - 1);
        int inner2 = inner * inner;
        boolean fill = fillMode == ShapeBuildTypes.ShapeFillMode.FILL;
        List<BlockPos> positions = new ArrayList<>();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int dist2 = (x * x) + (y * y) + (z * z);
                    if (dist2 <= outer2 && (fill || dist2 >= inner2)) {
                        positions.add(start.offset(x, y, z));
                    }
                }
            }
        }
        positions.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        targets.addAll(positions);
    }

    public static void addBoxTargets(Set<BlockPos> targets, BlockPos start, BlockPos end, int heightOffset, ShapeBuildTypes.ShapeFillMode fillMode) {
        int degrees = 0; // 鐢辫皟鐢ㄦ柟浼犲叆鏃嬭浆瑙掑??
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

    // ======================== 骞抽潰鐭╁舰锛堝甫鏃嬭浆??========================

    /** 鐢熸垚甯︽棆杞殑骞抽潰鐭╁舰鏂瑰潡 */
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

    // ======================== 瀹炵敤鏂规硶 ========================

    /** 妫€鏌ユ槸鍚﹀钩闈㈣竟鐣屽崟鍏冩牸 */
    /** 构建圆形平面格；fill=false 时只保留外壳。 */
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

    public static boolean isPlaneBoundaryCell(Set<PlaneCell> filledCells, PlaneCell cell) {
        return !filledCells.contains(new PlaneCell(cell.a() + 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a() - 1, cell.b()))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() + 1))
                || !filledCells.contains(new PlaneCell(cell.a(), cell.b() - 1));
    }

    /** 鏋勫缓鏃嬭浆鐭╁舰濉厖鍗曞厓鏍奸泦??*/
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

    /** 閫嗘棆杞娴嬪崟鍏冩牸鏄惁鍦ㄨ竟鐣屽??*/
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

    /** 濉厖骞抽潰鍐呴儴绌烘礊锛堟椽姘村～鍏呯畻娉曪級 */
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

    /** 灏嗗閮ㄥ崟鍏冩牸鍔犲叆闃熷??*/
    private static void queueOutsidePlaneCell(
            PlaneCell cell, Set<PlaneCell> filledCells, Set<PlaneCell> outside,
            ArrayDeque<PlaneCell> queue, int minA, int maxA, int minB, int maxB) {
        if (cell.a() < minA || cell.a() > maxA || cell.b() < minB || cell.b() > maxB) return;
        if (filledCells.contains(cell) || outside.contains(cell)) return;
        outside.add(cell);
        queue.addLast(cell);
    }

    // ======================== 鍧愭??鍚戦噺宸ュ叿 ========================

    /** 闄愬埗褰㈢姸鍋忕Щ??*/
    public static int clampShapeOffset(int value) {
        return Mth.clamp(value, -BuilderScreenConstants.SHAPE_MAX_OFFSET, BuilderScreenConstants.SHAPE_MAX_OFFSET);
    }

    /** 璁＄畻鏂瑰悜涓婄殑鎶曞奖鍒嗛??*/
    public static int dotDelta(int dx, int dy, int dz, Direction axis) {
        return (dx * axis.getStepX()) + (dy * axis.getStepY()) + (dz * axis.getStepZ());
    }

    /** 鍦ㄤ袱涓柟鍚戣酱涓婂亸绉讳綅缃?*/
    public static BlockPos offsetPos(BlockPos origin, Direction axisA, int stepA, Direction axisB, int stepB) {
        int dx = (axisA.getStepX() * stepA) + (axisB.getStepX() * stepB);
        int dy = (axisA.getStepY() * stepA) + (axisB.getStepY() * stepB);
        int dz = (axisA.getStepZ() * stepA) + (axisB.getStepZ() * stepB);
        return origin.offset(dx, dy, dz);
    }

    /** 鏃嬭浆骞抽潰鍋忕Щ??*/
    public static RotatedOffset rotatePlaneOffset(int a, int b, double centerA, double centerB, int degrees) {
        int normalized = Math.floorMod(degrees, 360);
        if (normalized == 0) return new RotatedOffset(a, b);
        double rad = Math.toRadians(normalized);
        double da = a - centerA, db = b - centerB;
        int ra = (int) Math.round((da * Math.cos(rad)) - (db * Math.sin(rad)) + centerA);
        int rb = (int) Math.round((da * Math.sin(rad)) + (db * Math.cos(rad)) + centerB);
        return new RotatedOffset(ra, rb);
    }

    // ======================== 闈㈡湞鍚戣В鏋?========================

    /** 瑙ｆ瀽褰㈢姸鐨勬瀯寤哄熀鍑嗛??*/
    public static Direction resolveShapeBuildFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (shape == null) return clickedFace == null ? Direction.UP : clickedFace;
        return switch (shape) {
            case LINE, SQUARE, WALL, CYLINDER, BOX -> Direction.UP;
            default -> clickedFace == null ? Direction.UP : clickedFace;
        };
    }

    /** 瑙ｆ瀽褰㈢姸鐨勬斁缃??*/
    public static Direction resolveShapePlacementFace(ClientRtsController.BuildShape shape, Direction clickedFace, Vec3 rayDir) {
        if (clickedFace != null) return clickedFace;
        return resolveShapeBuildFace(shape, clickedFace, rayDir);
    }

    /** 瑙ｆ瀽褰㈢姸鐨勫钩闈㈣酱??*/
    public static Direction[] resolveShapePlaneAxes(ClientRtsController.BuildShape shape, Direction face) {
        if (shape == ClientRtsController.BuildShape.SQUARE
                || shape == ClientRtsController.BuildShape.CYLINDER
                || shape == ClientRtsController.BuildShape.BOX) {
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

    /** 鍒ゆ柇褰㈢姸鏄惁闇€瑕佺涓夌偣锛堜粎绔嬫柟浣撻渶瑕侊??*/
    public static boolean requiresThirdPoint(ClientRtsController.BuildShape shape) {
        return shape == ClientRtsController.BuildShape.CYLINDER || shape == ClientRtsController.BuildShape.BOX;
    }

    // ======================== 鏀剧疆鍛戒腑缁撴灉鐢熸??========================

    /** 鍒涘缓褰㈢姸鏀剧疆??BlockHitResult */
    public static BlockHitResult createShapePlacementHit(BlockPos pos, Direction face) {
        Vec3 faceNormal = Vec3.atLowerCornerOf(face.getNormal());
        Vec3 hitVec = Vec3.atCenterOf(pos).add(faceNormal.scale(0.5D));
        return new BlockHitResult(hitVec, face, pos, false);
    }

    // ======================== 鍙敤濉厖妯″紡 ========================

    /** 鑾峰彇褰㈢姸鐨勫彲鐢ㄥ～鍏呮ā寮忓垪琛?*/
    public static List<ShapeBuildTypes.ShapeFillMode> availableFillModes(ClientRtsController.BuildShape shape) {
        if (shape == null) return List.of(ShapeBuildTypes.ShapeFillMode.FILL);
        return switch (shape) {
            case LINE -> List.of(ShapeBuildTypes.ShapeFillMode.FILL);
            case SQUARE, WALL, CIRCLE, CYLINDER, BALL -> List.of(ShapeBuildTypes.ShapeFillMode.FILL, ShapeBuildTypes.ShapeFillMode.HOLLOW);
            case BOX -> List.of(ShapeBuildTypes.ShapeFillMode.FILL, ShapeBuildTypes.ShapeFillMode.HOLLOW, ShapeBuildTypes.ShapeFillMode.SKELETON);
            default -> List.of(ShapeBuildTypes.ShapeFillMode.FILL);
        };
    }

    // ======================== 鏁版嵁璁板綍 ========================

    /** 鏃嬭浆鍋忕Щ閲?*/
    public record RotatedOffset(int a, int b) {}

    /** 骞抽潰鍗曞厓??*/
    public record PlaneCell(int a, int b) {}

    private ShapeGeometryUtil() {
        // 宸ュ叿绫伙紝绂佹瀹炰緥鍖?
    }
}
