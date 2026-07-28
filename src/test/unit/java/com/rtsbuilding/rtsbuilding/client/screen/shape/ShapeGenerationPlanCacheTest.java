package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 普通、高级和范围破坏必须通过同一份有界且可复用的形状计划。
 */
class ShapeGenerationPlanCacheTest {
    @Test
    void normalBuildClampsBeforeGeneratingAndPublishesInclusiveBounds() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();

        List<BlockPos> positions = cache.positions(request(
                input(BuildShape.LINE, new BlockPos(100, 0, 0), 0),
                ShapeFillMode.FILL,
                null,
                false,
                limits(64, 64, 64, 262_144),
                5));

        assertFalse(positions.isEmpty());
        assertNotNull(cache.bounds());
        assertTrue(cache.bounds().width() <= 5);
        assertEquals(1, cache.bounds().height());
        assertEquals(1, cache.bounds().depth());
        assertTrue(positions.stream().allMatch(cache.bounds()::contains));
    }

    @Test
    void identicalRequestReusesOneImmutablePlanUntilClear() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        ShapeGenerationPlanCache.Request request = request(
                input(BuildShape.BOX, new BlockPos(2, 0, 2), 2),
                ShapeFillMode.HOLLOW,
                null,
                false,
                limits(64, 64, 64, 262_144),
                8);

        List<BlockPos> first = cache.positions(request);
        List<BlockPos> second = cache.positions(request);
        assertSame(first, second);
        assertNotNull(cache.bounds());

        cache.clear();
        assertNull(cache.bounds());
        List<BlockPos> rebuilt = cache.positions(request);
        assertNotSame(first, rebuilt);
        assertEquals(first, rebuilt);
    }

    @Test
    void fillModeAndAdvancedBoxParticipateInTheCacheKey() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        ShapeBuildTypes.Input input = input(BuildShape.BOX, new BlockPos(1, 0, 1), 1);
        RtsCullingBox firstBox =
                new RtsCullingBox(0, BlockPos.ORIGIN, new BlockPos(2, 2, 2));

        List<BlockPos> fill = cache.positions(request(
                input,
                ShapeFillMode.FILL,
                firstBox,
                false,
                limits(64, 64, 64, 262_144),
                8));
        List<BlockPos> hollow = cache.positions(request(
                input,
                ShapeFillMode.HOLLOW,
                firstBox,
                false,
                limits(64, 64, 64, 262_144),
                8));
        List<BlockPos> moved = cache.positions(request(
                input,
                ShapeFillMode.HOLLOW,
                new RtsCullingBox(0, BlockPos.ORIGIN, new BlockPos(3, 2, 2)),
                false,
                limits(64, 64, 64, 262_144),
                8));

        assertEquals(27, fill.size());
        assertTrue(hollow.size() < fill.size());
        assertNotSame(fill, hollow);
        assertNotSame(hollow, moved);
        assertEquals(4, cache.bounds().width());
    }

    @Test
    void rangeDestroyRectilinearPlanHonorsAxisAndVolumeLimits() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        RangeDestroySelectionLimiter.Limits limits = limits(4, 3, 2, 12);

        List<BlockPos> positions = cache.positions(request(
                input(BuildShape.BOX, new BlockPos(20, 0, 20), 20),
                ShapeFillMode.FILL,
                null,
                true,
                limits,
                32));

        RtsCullingBox bounds = cache.bounds();
        assertNotNull(bounds);
        assertTrue(bounds.width() <= 4);
        assertTrue(bounds.height() <= 3);
        assertTrue(bounds.depth() <= 2);
        assertTrue(positions.size() <= 12);
    }

    @Test
    void roundRangeDestroyPlanCannotEscapeCenteredCaps() {
        ShapeGenerationPlanCache cache = new ShapeGenerationPlanCache();
        RangeDestroySelectionLimiter.Limits limits = limits(7, 1, 7, 49);

        List<BlockPos> positions = cache.positions(request(
                input(BuildShape.CIRCLE, new BlockPos(100, 0, 0), 0),
                ShapeFillMode.FILL,
                null,
                true,
                limits,
                32));

        assertFalse(positions.isEmpty());
        assertTrue(cache.bounds().width() <= 7);
        assertEquals(1, cache.bounds().height());
        assertTrue(cache.bounds().depth() <= 7);
        assertTrue(positions.size() <= 49);
    }

    private static ShapeGenerationPlanCache.Request request(
            ShapeBuildTypes.Input input,
            ShapeFillMode fillMode,
            RtsCullingBox advancedBox,
            boolean rangeDestroy,
            RangeDestroySelectionLimiter.Limits limits,
            int buildMaxDimension) {
        return new ShapeGenerationPlanCache.Request(
                input,
                fillMode,
                advancedBox,
                rangeDestroy,
                limits,
                buildMaxDimension);
    }

    private static ShapeBuildTypes.Input input(
            BuildShape shape,
            BlockPos pointB,
            int heightOffset) {
        return new ShapeBuildTypes.Input(
                shape,
                EnumFacing.UP,
                EnumFacing.UP,
                BlockPos.ORIGIN,
                pointB,
                heightOffset,
                false);
    }

    private static RangeDestroySelectionLimiter.Limits limits(
            int width,
            int height,
            int depth,
            int volume) {
        return new RangeDestroySelectionLimiter.Limits(width, height, depth, volume);
    }
}
