package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G10DR 几何回归：独立格点 oracle、半径/尺寸边界、真实容量和缓存清理。
 *
 * <p>测试故意只调用显式计划入口，不读取客户端配置；这样能证明真实几何调用链
 * 在默认 32/32、宽配置和 262144 唯一目标容量下仍保持完整结果。</p>
 */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class G10DRGeometryRegressionTest {
    private static final int TARGET_LIMIT = MiningLimits.MAX_VOLUME;

    @Test
    void defaultDimensionAndRadiusStayIndependentAt32() {
        BlockPos origin = new BlockPos(0, 64, 0);

        ShapeGenerationResult circle = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.CIRCLE, origin, new BlockPos(32, 64, 0), 0, Direction.UP),
                ShapeFillMode.FILL, 32, 32, TARGET_LIMIT);
        ShapeGenerationResult ball = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.BALL, origin, new BlockPos(32, 64, 0), 0, Direction.UP),
                ShapeFillMode.FILL, 32, 32, TARGET_LIMIT);
        ShapeGenerationResult cylinder = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.CYLINDER, origin, new BlockPos(32, 64, 0), 31, Direction.UP),
                ShapeFillMode.HOLLOW, 32, 32, TARGET_LIMIT);

        assertEquals(ShapeGenerationStatus.READY, circle.status());
        assertEquals(ShapeGenerationStatus.READY, ball.status());
        assertEquals(ShapeGenerationStatus.READY, cylinder.status());
        assertBounds(circle.positions(), -32, 32, 64, 64, -32, 32);
        assertBounds(ball.positions(), -32, 32, 32, 96, -32, 32);
        assertBounds(cylinder.positions(), -32, 32, 64, 95, -32, 32);
        assertTrue(ball.positions().size() < TARGET_LIMIT);
        assertTrue(circle.positions().size() < TARGET_LIMIT);
        assertTrue(cylinder.positions().size() < TARGET_LIMIT);
    }

    @Test
    void cacheSeparatesRadiusDimensionAndClearsEmptyOrTooLargeResults() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        BlockPos origin = BlockPos.ZERO;
        ShapeBuildTypes.Input circleInput =
                input(BuildShape.CIRCLE, origin, new BlockPos(64, 0, 0), 0, Direction.UP);

        ShapeGenerationResult radius32 = cache.plan(request(circleInput, ShapeFillMode.FILL, 32, 32));
        ShapeGenerationResult radius16 = cache.plan(request(circleInput, ShapeFillMode.FILL, 32, 16));
        ShapeGenerationResult radius64 = cache.plan(request(circleInput, ShapeFillMode.FILL, 64, 64));

        assertNotSame(radius32, radius16);
        assertNotSame(radius16, radius64);
        assertEquals(65, spanX(radius32.positions()));
        assertEquals(33, spanX(radius16.positions()));
        assertEquals(129, spanX(radius64.positions()));

        ShapeBuildTypes.Input line =
                input(BuildShape.LINE, origin, new BlockPos(262_144, 0, 0), 0, Direction.UP);
        ShapeGenerationResult tooLarge = cache.plan(request(line, ShapeFillMode.FILL,
                Integer.MAX_VALUE, 0));
        assertEquals(ShapeGenerationStatus.TOO_LARGE, tooLarge.status());
        assertTrue(tooLarge.positions().isEmpty());
        assertTrue(tooLarge.discoveredTargets() > TARGET_LIMIT);
        assertFalse(cache.bounds() != null);

        ShapeGenerationResult empty = cache.plan(null);
        assertEquals(ShapeGenerationStatus.EMPTY, empty.status());
        assertTrue(empty.positions().isEmpty());
        assertEquals(null, cache.bounds());

        ShapeGenerationResult rebuilt = cache.plan(request(
                input(BuildShape.BOX, origin, new BlockPos(1, 0, 1), 1, Direction.UP),
                ShapeFillMode.HOLLOW, 4, 4));
        assertEquals(ShapeGenerationStatus.READY, rebuilt.status());
        assertTrue(cache.bounds() != null);
        cache.clear();
        assertEquals(null, cache.bounds());
        assertEquals(ShapeGenerationStatus.EMPTY, cache.plan(null).status());
    }

    @Test
    void normalAndAdvancedSmallShapesMatchIndependentOracles() {
        BlockPos origin = new BlockPos(0, 10, 0);
        for (int radius = 0; radius <= 3; radius++) {
            ShapeBuildTypes.Input circleInput =
                    input(BuildShape.CIRCLE, origin, new BlockPos(radius, 10, 0), 0, Direction.UP);
            Set<BlockPos> expectedFill = oracleCircle(origin, radius, true);
            Set<BlockPos> expectedShell = oracleCircle(origin, radius, false);
            assertEquals(expectedFill, new HashSet<>(ShapeGeometryUtil.buildShapePlan(
                    circleInput, ShapeFillMode.FILL, 32, 32, TARGET_LIMIT).positions()));
            assertEquals(expectedShell, new HashSet<>(ShapeGeometryUtil.buildShapePlan(
                    circleInput, ShapeFillMode.HOLLOW, 32, 32, TARGET_LIMIT).positions()));

            ShapeGenerationResult ball = ShapeGeometryUtil.buildShapePlan(
                    input(BuildShape.BALL, origin, new BlockPos(radius, 10, 0), 0, Direction.UP),
                    ShapeFillMode.HOLLOW, 32, 32, TARGET_LIMIT);
            assertEquals(oracleBall(origin, radius, false), new HashSet<>(ball.positions()));

            RtsCullingBox ellipseBox = new RtsCullingBox(
                    0, new BlockPos(-radius, 10, -radius), new BlockPos(radius, 10, radius));
            ShapeGenerationResult advanced = ShapeGeometryUtil.buildAdvancedShapePlan(
                    BuildShape.CIRCLE, ellipseBox, ShapeFillMode.FILL, Direction.UP, TARGET_LIMIT);
            assertEquals(oracleEllipse(ellipseBox, true), new HashSet<>(advanced.positions()));

            for (Direction face : List.of(Direction.UP, Direction.EAST, Direction.NORTH)) {
                RtsCullingBox orientedBox = orientedEllipseBox(radius, face);
                ShapeGenerationResult orientedAdvanced = ShapeGeometryUtil.buildAdvancedShapePlan(
                        BuildShape.CIRCLE, orientedBox, ShapeFillMode.FILL, face, TARGET_LIMIT);
                assertEquals(oracleEllipseOnFace(orientedBox, face, true),
                        new HashSet<>(orientedAdvanced.positions()));
            }
        }

        ShapeBuildTypes.Input eastCircle =
                input(BuildShape.CIRCLE, origin, new BlockPos(0, 10, 3), 0, Direction.EAST);
        Set<BlockPos> oriented = new HashSet<>(ShapeGeometryUtil.buildShapePlan(
                eastCircle, ShapeFillMode.FILL, 32, 32, TARGET_LIMIT).positions());
        assertTrue(oriented.stream().allMatch(pos -> pos.getX() == 0));
        assertTrue(oriented.contains(new BlockPos(0, 10, 3)));
        assertTrue(oriented.contains(new BlockPos(0, 10, -3)));
        assertTrue(oriented.contains(new BlockPos(0, 13, 0)));
        assertTrue(oriented.contains(new BlockPos(0, 7, 0)));

        ShapeBuildTypes.Input degenerate =
                input(BuildShape.BALL, origin, new BlockPos(3, 10, 0), 0, Direction.UP);
        ShapeGenerationResult degenerateLine = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.BALL,
                new RtsCullingBox(0, new BlockPos(0, 10, 0), new BlockPos(3, 10, 0)),
                ShapeFillMode.HOLLOW, Direction.UP, TARGET_LIMIT);
        assertEquals(4, degenerateLine.positions().size());
        assertEquals(ShapeGenerationStatus.READY, degenerateLine.status());
        assertEquals(ShapeGenerationStatus.READY, ShapeGeometryUtil.buildShapePlan(
                degenerate, ShapeFillMode.HOLLOW, 32, 32, TARGET_LIMIT).status());

        RtsCullingBox ellipsoidBox = new RtsCullingBox(
                0, new BlockPos(0, 0, 0), new BlockPos(2, 2, 2));
        ShapeGenerationResult ellipsoid = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.BALL, ellipsoidBox, ShapeFillMode.FILL, Direction.UP, TARGET_LIMIT);
        assertEquals(oracleEllipsoid(ellipsoidBox, true), new HashSet<>(ellipsoid.positions()));
    }

    @Test
    void sparseBoundariesRemainBoundedForLargeSelections() {
        ShapeGenerationResult giantCircle = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.CIRCLE, BlockPos.ZERO,
                        new BlockPos(Integer.MAX_VALUE, 0, 0), 0, Direction.UP),
                ShapeFillMode.FILL, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);
        ShapeGenerationResult giantBall = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.BALL, BlockPos.ZERO,
                        new BlockPos(Integer.MAX_VALUE, 0, 0), 0, Direction.UP),
                ShapeFillMode.HOLLOW, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);
        assertEquals(ShapeGenerationStatus.TOO_LARGE, giantCircle.status());
        assertEquals(ShapeGenerationStatus.TOO_LARGE, giantBall.status());

        ShapeGenerationResult box = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.BOX, BlockPos.ZERO, new BlockPos(127, 0, 127), 127, Direction.UP),
                ShapeFillMode.HOLLOW, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);
        ShapeGenerationResult skeleton = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.BOX, BlockPos.ZERO, new BlockPos(127, 0, 127), 127, Direction.UP),
                ShapeFillMode.SKELETON, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);
        ShapeGenerationResult square = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.SQUARE, BlockPos.ZERO, new BlockPos(65_535, 0, 65_535), 0, Direction.UP),
                ShapeFillMode.HOLLOW, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);
        ShapeGenerationResult wall = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.WALL, BlockPos.ZERO, new BlockPos(65_535, 0, 0), 65_535, Direction.UP),
                ShapeFillMode.HOLLOW, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT);

        assertEquals(ShapeGenerationStatus.READY, box.status());
        assertEquals(ShapeGenerationStatus.READY, skeleton.status());
        assertEquals(ShapeGenerationStatus.READY, square.status());
        assertEquals(ShapeGenerationStatus.READY, wall.status());
        assertTrue(box.positions().size() < TARGET_LIMIT);
        assertTrue(skeleton.positions().size() < TARGET_LIMIT);
        assertEquals(262_140, square.positions().size());
        assertEquals(262_140, wall.positions().size());
        assertEquals(262_140, new HashSet<>(square.positions()).size());
        assertEquals(262_140, new HashSet<>(wall.positions()).size());

        ShapeGenerationResult thinEllipse = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.CIRCLE,
                new RtsCullingBox(0, new BlockPos(0, 0, 0), new BlockPos(139_999, 1, 1)),
                ShapeFillMode.FILL, Direction.UP, TARGET_LIMIT);
        ShapeGenerationResult tooThinEllipse = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.CIRCLE,
                new RtsCullingBox(0, new BlockPos(0, 0, 0), new BlockPos(279_999, 1, 1)),
                ShapeFillMode.FILL, Direction.UP, TARGET_LIMIT);
        ShapeGenerationResult thinEllipsoid = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.BALL,
                new RtsCullingBox(0, new BlockPos(0, 0, 0), new BlockPos(89_999, 1, 1)),
                ShapeFillMode.HOLLOW, Direction.UP, TARGET_LIMIT);
        ShapeGenerationResult tooThinEllipsoid = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.BALL,
                new RtsCullingBox(0, new BlockPos(0, 0, 0), new BlockPos(299_999, 1, 1)),
                ShapeFillMode.HOLLOW, Direction.UP, TARGET_LIMIT);
        ShapeGenerationResult emptyCrossSection = ShapeGeometryUtil.buildAdvancedShapePlan(
                BuildShape.BALL,
                new RtsCullingBox(0, new BlockPos(0, 0, 0), new BlockPos(262_143, 1, 262_143)),
                ShapeFillMode.FILL, Direction.UP, TARGET_LIMIT);

        assertEquals(ShapeGenerationStatus.READY, thinEllipse.status());
        assertEquals(242_488, thinEllipse.positions().size());
        assertEquals(ShapeGenerationStatus.TOO_LARGE, tooThinEllipse.status());
        assertEquals(ShapeGenerationStatus.READY, thinEllipsoid.status());
        assertEquals(254_560, thinEllipsoid.positions().size());
        assertEquals(ShapeGenerationStatus.TOO_LARGE, tooThinEllipsoid.status());
        assertEquals(ShapeGenerationStatus.TOO_LARGE, emptyCrossSection.status());
    }

    @Test
    void exactTargetCapacityRejectsOnlyTheFirstOverTarget() {
        ShapeBuildTypes.Input legal =
                input(BuildShape.LINE, BlockPos.ZERO, new BlockPos(262_143, 0, 0), 0, Direction.UP);
        ShapeBuildTypes.Input over =
                input(BuildShape.LINE, BlockPos.ZERO, new BlockPos(262_144, 0, 0), 0, Direction.UP);

        ShapeGenerationResult legalResult =
                ShapeGeometryUtil.buildRangeDestroyShapePlan(legal, ShapeFillMode.FILL, TARGET_LIMIT);
        ShapeGenerationResult overResult =
                ShapeGeometryUtil.buildRangeDestroyShapePlan(over, ShapeFillMode.FILL, TARGET_LIMIT);

        assertEquals(ShapeGenerationStatus.READY, legalResult.status());
        assertEquals(TARGET_LIMIT, legalResult.positions().size());
        assertEquals(ShapeGenerationStatus.TOO_LARGE, overResult.status());
        assertTrue(overResult.positions().isEmpty());
        assertEquals(TARGET_LIMIT + 1L, overResult.discoveredTargets());
        assertThrows(UnsupportedOperationException.class,
                () -> legalResult.positions().add(BlockPos.ZERO));
    }


    @Test
    void advancedShapesMatchSmallOddEvenAndDegenerateLattices() {
        for (int width : new int[] {1, 2, 3, 4, 7}) {
            for (int height : new int[] {1, 2, 5}) {
                for (int depth : new int[] {1, 2, 3, 6}) {
                    RtsCullingBox box = new RtsCullingBox(0, new BlockPos(-3, 8, -4),
                            new BlockPos(width - 4, height + 7, depth - 5));
                    for (ShapeFillMode mode : ShapeFillMode.values()) {
                        String context = width + "x" + height + "x" + depth + " " + mode;
                        ShapeGenerationResult ball = ShapeGeometryUtil.buildAdvancedShapePlan(
                                BuildShape.BALL, box, mode, Direction.UP, TARGET_LIMIT);
                        assertEquals(oracleEllipsoid(box, mode == ShapeFillMode.FILL),
                                Set.copyOf(ball.positions()), context);
                        assertOrdered(ball.positions(), layerOrder(), context);
                        for (Direction face : Direction.values()) {
                            ShapeGenerationResult ellipse = ShapeGeometryUtil.buildAdvancedShapePlan(
                                    BuildShape.CIRCLE, box, mode, face, TARGET_LIMIT);
                            assertEquals(oracleEllipseOnFace(box, face, mode == ShapeFillMode.FILL),
                                    Set.copyOf(ellipse.positions()), context + " circle " + face);
                            assertOrdered(ellipse.positions(), axisOrder(), context + " circle " + face);
                            ShapeGenerationResult cylinder = ShapeGeometryUtil.buildAdvancedShapePlan(
                                    BuildShape.CYLINDER, box, mode, face, TARGET_LIMIT);
                            assertEquals(oracleCylinder(box, face, mode == ShapeFillMode.FILL),
                                    Set.copyOf(cylinder.positions()), context + " cylinder " + face);
                            assertOrdered(cylinder.positions(), layerOrder(), context + " cylinder " + face);
                        }
                    }
                }
            }
        }
    }

    @Test
    void boundariesPreserveVisibleLayerAndDistancePriority() {
        BlockPos origin = new BlockPos(3, 20, 4);
        for (ShapeFillMode mode : ShapeFillMode.values()) {
            ShapeGenerationResult box = ShapeGeometryUtil.buildShapePlan(
                    input(BuildShape.BOX, origin, origin.offset(-4, 0, 5), -3, Direction.UP),
                    mode, 32, 32, TARGET_LIMIT);
            assertOrdered(box.positions(), Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingDouble(pos -> pos.distSqr(origin)), "ordinary box " + mode);
            ShapeGenerationResult wall = ShapeGeometryUtil.buildShapePlan(
                    input(BuildShape.WALL, origin, origin.offset(-4, 0, 0), -3, Direction.UP),
                    mode, 32, 32, TARGET_LIMIT);
            assertOrdered(wall.positions(), Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingDouble(pos -> pos.distSqr(origin)), "ordinary wall " + mode);
            RtsCullingBox advancedBox = new RtsCullingBox(0, origin, origin.offset(4, 3, 5));
            for (BuildShape shape : List.of(BuildShape.SQUARE, BuildShape.WALL, BuildShape.BOX)) {
                assertOrdered(ShapeGeometryUtil.buildAdvancedShapePlan(
                        shape, advancedBox, mode, Direction.UP, TARGET_LIMIT).positions(),
                        layerOrder(), "advanced " + shape + " " + mode);
            }
        }
        ShapeGenerationResult singleColumn = ShapeGeometryUtil.buildShapePlan(
                input(BuildShape.WALL, origin, origin, 6, Direction.UP),
                ShapeFillMode.HOLLOW, 32, 32, 7);
        assertEquals(ShapeGenerationStatus.READY, singleColumn.status());
        assertEquals(7, singleColumn.positions().size());
    }

    @Test
    void giantHollowEllipsoidSkipsEmptyCrossSectionsAndRejectsWholePlan() {
        for (RtsCullingBox box : List.of(
                new RtsCullingBox(0, BlockPos.ZERO, new BlockPos(262_143, 262_143, 1)),
                new RtsCullingBox(0, BlockPos.ZERO, new BlockPos(1, 262_143, 262_143)),
                new RtsCullingBox(0, BlockPos.ZERO, new BlockPos(262_143, 1, 262_143)))) {
            ShapeGenerationResult result = ShapeGeometryUtil.buildAdvancedShapePlan(
                    BuildShape.BALL, box, ShapeFillMode.HOLLOW, Direction.UP, TARGET_LIMIT);
            assertEquals(ShapeGenerationStatus.TOO_LARGE, result.status());
            assertTrue(result.positions().isEmpty());
        }
    }

    @Test
    void cacheDimensionChangesIndependentlyOfRadius() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        ShapeBuildTypes.Input line = input(
                BuildShape.LINE, BlockPos.ZERO, new BlockPos(63, 0, 0), 0, Direction.UP);
        ShapeGenerationResult small = cache.plan(request(line, ShapeFillMode.FILL, 16, 32));
        ShapeGenerationResult large = cache.plan(request(line, ShapeFillMode.FILL, 64, 32));
        assertEquals(16, small.positions().size());
        assertEquals(64, large.positions().size());
        assertNotSame(small, large);
        assertThrows(UnsupportedOperationException.class, () -> large.positions().clear());
    }

    private static Set<BlockPos> oracleCylinder(RtsCullingBox box, Direction face, boolean fill) {
        Set<BlockPos> filled = oracleEllipseOnFace(box, face, true);
        Set<BlockPos> boundary = oracleEllipseOnFace(box, face, false);
        Direction positive = switch (face.getAxis()) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        int layers = switch (face.getAxis()) {
            case X -> box.max().getX() - box.min().getX() + 1;
            case Y -> box.max().getY() - box.min().getY() + 1;
            case Z -> box.max().getZ() - box.min().getZ() + 1;
        };
        Set<BlockPos> result = new HashSet<>();
        for (int layer = 0; layer < layers; layer++) {
            boolean cap = layers > 1 && (layer == 0 || layer == layers - 1);
            for (BlockPos pos : fill || cap ? filled : boundary) {
                result.add(pos.relative(positive, layer));
            }
        }
        return result;
    }

    private static Comparator<BlockPos> axisOrder() {
        return Comparator.<BlockPos>comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ);
    }

    private static Comparator<BlockPos> layerOrder() {
        return Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    }

    private static void assertOrdered(
            List<BlockPos> positions, Comparator<BlockPos> order, String context) {
        for (int index = 1; index < positions.size(); index++) {
            assertTrue(order.compare(positions.get(index - 1), positions.get(index)) <= 0,
                    context + ": placement priority changed at " + index);
        }
    }

    private static ShapeGenerationPlanCache.Request request(
            ShapeBuildTypes.Input input, ShapeFillMode mode, int dimension, int radius) {
        return new ShapeGenerationPlanCache.Request(
                input, mode, null, false,
                new RangeDestroySelectionLimiter.Limits(
                        Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, TARGET_LIMIT),
                dimension, radius);
    }

    private static ShapeBuildTypes.Input input(
            BuildShape shape, BlockPos pointA, BlockPos pointB, int heightOffset, Direction face) {
        return new ShapeBuildTypes.Input(
                shape, face, Direction.UP, pointA, pointB, heightOffset, false);
    }

    private static Set<BlockPos> oracleCircle(BlockPos origin, int radius, boolean fill) {
        Set<BlockPos> result = new HashSet<>();
        long outer = (long) radius * radius;
        long innerRadius = Math.max(0, radius - 1);
        long inner = innerRadius * innerRadius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long distance = (long) dx * dx + (long) dz * dz;
                if (distance <= outer && (fill || distance >= inner)) {
                    result.add(origin.offset(dx, 0, dz));
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> oracleBall(BlockPos origin, int radius, boolean fill) {
        Set<BlockPos> result = new HashSet<>();
        long outer = (long) radius * radius;
        long innerRadius = Math.max(0, radius - 1);
        long inner = innerRadius * innerRadius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    long distance = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distance <= outer && (fill || distance >= inner)) {
                        result.add(origin.offset(dx, dy, dz));
                    }
                }
            }
        }
        return result;
    }

    private static Set<BlockPos> oracleEllipse(RtsCullingBox box, boolean fill) {
        Set<BlockPos> result = new HashSet<>();
        for (int x = box.min().getX(); x <= box.max().getX(); x++) {
            for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                double distance = normalized(x, box.min().getX(), box.max().getX())
                        + normalized(z, box.min().getZ(), box.max().getZ());
                if (distance <= 1.0D && (fill || boundaryEllipse(x, z, box))) {
                    result.add(new BlockPos(x, box.min().getY(), z));
                }
            }
        }
        return result;
    }

    private static RtsCullingBox orientedEllipseBox(int radius, Direction face) {
        int diameter = radius * 2;
        return switch (face) {
            case EAST, WEST -> new RtsCullingBox(
                    0, new BlockPos(0, 0, 0), new BlockPos(0, diameter, diameter));
            case NORTH, SOUTH -> new RtsCullingBox(
                    0, new BlockPos(0, 0, 0), new BlockPos(diameter, diameter, 0));
            default -> new RtsCullingBox(
                    0, new BlockPos(0, 0, 0), new BlockPos(diameter, 0, diameter));
        };
    }

    private static Set<BlockPos> oracleEllipseOnFace(
            RtsCullingBox box, Direction face, boolean fill) {
        Set<BlockPos> result = new HashSet<>();
        int minA;
        int maxA;
        int minB;
        int maxB;
        switch (face) {
            case EAST, WEST -> {
                minA = box.min().getY();
                maxA = box.max().getY();
                minB = box.min().getZ();
                maxB = box.max().getZ();
                for (int y = minA; y <= maxA; y++) {
                    for (int z = minB; z <= maxB; z++) {
                        if (insideEllipse(y, z, minA, maxA, minB, maxB)
                                && (fill || boundaryEllipseOnFace(box, face, y, z))) {
                            result.add(new BlockPos(box.min().getX(), y, z));
                        }
                    }
                }
            }
            case NORTH, SOUTH -> {
                minA = box.min().getX();
                maxA = box.max().getX();
                minB = box.min().getY();
                maxB = box.max().getY();
                for (int x = minA; x <= maxA; x++) {
                    for (int y = minB; y <= maxB; y++) {
                        if (insideEllipse(x, y, minA, maxA, minB, maxB)
                                && (fill || boundaryEllipseOnFace(box, face, x, y))) {
                            result.add(new BlockPos(x, y, box.min().getZ()));
                        }
                    }
                }
            }
            default -> {
                minA = box.min().getX();
                maxA = box.max().getX();
                minB = box.min().getZ();
                maxB = box.max().getZ();
                for (int x = minA; x <= maxA; x++) {
                    for (int z = minB; z <= maxB; z++) {
                        if (insideEllipse(x, z, minA, maxA, minB, maxB)
                                && (fill || boundaryEllipseOnFace(box, face, x, z))) {
                            result.add(new BlockPos(x, box.min().getY(), z));
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean insideEllipse(int a, int b, int minA, int maxA, int minB, int maxB) {
        return normalized(a, minA, maxA) + normalized(b, minB, maxB) <= 1.0D;
    }

    private static boolean boundaryEllipseOnFace(RtsCullingBox box, Direction face, int a, int b) {
        return switch (face) {
            case EAST, WEST -> !insideEllipse(a + 1, b, box.min().getY(), box.max().getY(),
                    box.min().getZ(), box.max().getZ())
                    || !insideEllipse(a - 1, b, box.min().getY(), box.max().getY(),
                    box.min().getZ(), box.max().getZ())
                    || !insideEllipse(a, b + 1, box.min().getY(), box.max().getY(),
                    box.min().getZ(), box.max().getZ())
                    || !insideEllipse(a, b - 1, box.min().getY(), box.max().getY(),
                    box.min().getZ(), box.max().getZ());
            case NORTH, SOUTH -> !insideEllipse(a + 1, b, box.min().getX(), box.max().getX(),
                    box.min().getY(), box.max().getY())
                    || !insideEllipse(a - 1, b, box.min().getX(), box.max().getX(),
                    box.min().getY(), box.max().getY())
                    || !insideEllipse(a, b + 1, box.min().getX(), box.max().getX(),
                    box.min().getY(), box.max().getY())
                    || !insideEllipse(a, b - 1, box.min().getX(), box.max().getX(),
                    box.min().getY(), box.max().getY());
            default -> boundaryEllipse(a, b, box);
        };
    }

    private static Set<BlockPos> oracleEllipsoid(RtsCullingBox box, boolean fill) {
        Set<BlockPos> result = new HashSet<>();
        for (int x = box.min().getX(); x <= box.max().getX(); x++) {
            for (int y = box.min().getY(); y <= box.max().getY(); y++) {
                for (int z = box.min().getZ(); z <= box.max().getZ(); z++) {
                    boolean inside = normalized(x, box.min().getX(), box.max().getX())
                            + normalized(y, box.min().getY(), box.max().getY())
                            + normalized(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
                    if (inside && (fill
                            || !containsEllipsoid(box, x + 1, y, z)
                            || !containsEllipsoid(box, x - 1, y, z)
                            || !containsEllipsoid(box, x, y + 1, z)
                            || !containsEllipsoid(box, x, y - 1, z)
                            || !containsEllipsoid(box, x, y, z + 1)
                            || !containsEllipsoid(box, x, y, z - 1))) {
                        result.add(new BlockPos(x, y, z));
                    }
                }
            }
        }
        return result;
    }

    private static boolean containsEllipsoid(RtsCullingBox box, int x, int y, int z) {
        return normalized(x, box.min().getX(), box.max().getX())
                + normalized(y, box.min().getY(), box.max().getY())
                + normalized(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    private static boolean boundaryEllipse(int x, int z, RtsCullingBox box) {
        return !containsEllipse(box, x + 1, z) || !containsEllipse(box, x - 1, z)
                || !containsEllipse(box, x, z + 1) || !containsEllipse(box, x, z - 1);
    }

    private static boolean containsEllipse(RtsCullingBox box, int x, int z) {
        return normalized(x, box.min().getX(), box.max().getX())
                + normalized(z, box.min().getZ(), box.max().getZ()) <= 1.0D;
    }

    private static double normalized(int value, int min, int max) {
        if (min >= max) return value == min ? 0.0D : Double.POSITIVE_INFINITY;
        double center = ((double) min + max + 1.0D) * 0.5D;
        double radius = ((double) max - min + 1.0D) * 0.5D;
        double delta = (value + 0.5D - center) / radius;
        return delta * delta;
    }

    private static void assertBounds(
            List<BlockPos> positions,
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        assertFalse(positions.isEmpty());
        for (BlockPos pos : positions) {
            assertTrue(pos.getX() >= minX && pos.getX() <= maxX);
            assertTrue(pos.getY() >= minY && pos.getY() <= maxY);
            assertTrue(pos.getZ() >= minZ && pos.getZ() <= maxZ);
        }
        assertEquals(minX, positions.stream().mapToInt(BlockPos::getX).min().orElseThrow());
        assertEquals(maxX, positions.stream().mapToInt(BlockPos::getX).max().orElseThrow());
        assertEquals(minY, positions.stream().mapToInt(BlockPos::getY).min().orElseThrow());
        assertEquals(maxY, positions.stream().mapToInt(BlockPos::getY).max().orElseThrow());
        assertEquals(minZ, positions.stream().mapToInt(BlockPos::getZ).min().orElseThrow());
        assertEquals(maxZ, positions.stream().mapToInt(BlockPos::getZ).max().orElseThrow());
    }

    private static int spanX(List<BlockPos> positions) {
        return positions.stream().mapToInt(BlockPos::getX).max().orElseThrow()
                - positions.stream().mapToInt(BlockPos::getX).min().orElseThrow() + 1;
    }
}
