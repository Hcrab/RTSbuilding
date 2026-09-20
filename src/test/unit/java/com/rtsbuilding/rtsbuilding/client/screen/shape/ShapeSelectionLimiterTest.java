package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeSelectionLimiterTest {
    @Test
    void extremeCoordinateSpanCannotWrapIntoAnApparentlySmallVolume() {
        var input = new ShapeBuildTypes.Input(BuildShape.BOX, Direction.UP, Direction.UP,
                new BlockPos(Integer.MIN_VALUE, 0, 0), new BlockPos(Integer.MAX_VALUE, 0, 0), 0, false);
        assertTrue(ShapeSelectionLimiter.envelopeVolume(input) > 262144);
        var clamped = ShapeSelectionLimiter.clampDimensionsAndVolume(input, 512);
        assertTrue(ShapeSelectionLimiter.envelopeVolume(clamped) <= 512);
    }
    @Test
    void rectilinearSelectionIsShrunkBeforeGeometryWhenVolumeIsTooLarge() {
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.BOX,
                Direction.UP,
                Direction.UP,
                BlockPos.ZERO,
                new BlockPos(255, 0, 255),
                255,
                false);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensionsAndVolume(
                input, 256, 256, 256, 46_656);

        assertTrue(ShapeSelectionLimiter.envelopeVolume(limited) <= 46_656L);
    }

    @Test
    void centeredRoundSelectionNeverExceedsConfiguredAxisLength() {
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.CIRCLE,
                Direction.UP,
                Direction.UP,
                BlockPos.ZERO,
                new BlockPos(100, 0, 0),
                0,
                false);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensionsAndVolume(
                input, 12, 12, 12, 1_728);
        int radius = Math.abs(limited.pointB().getX() - limited.pointA().getX());

        assertTrue((radius * 2) + 1 <= 12,
                "中心对称形状在偶数尺寸上限下宁可少一格，也不能越过服务端限制");
    }

    @Test
    void volumeOnlyClampKeepsLongThinSelection() {
        ShapeBuildTypes.Input input = new ShapeBuildTypes.Input(
                BuildShape.BOX,
                Direction.UP,
                Direction.UP,
                BlockPos.ZERO,
                new BlockPos(511, 0, 0),
                0,
                false);

        ShapeBuildTypes.Input limited = ShapeSelectionLimiter.clampDimensionsAndVolume(input, 512);

        assertTrue(limited.pointB().getX() - limited.pointA().getX() + 1 == 512);
        assertTrue(ShapeSelectionLimiter.envelopeVolume(limited) <= 512L);
    }

    @Test
    void volumeOnlyClampAcceptsFortyEightAndSixtyFourCubesAtTheirBudgets() {
        ShapeBuildTypes.Input cube48 = new ShapeBuildTypes.Input(
                BuildShape.BOX, Direction.UP, Direction.UP,
                BlockPos.ZERO, new BlockPos(47, 0, 47), 47, false);
        ShapeBuildTypes.Input cube64 = new ShapeBuildTypes.Input(
                BuildShape.BOX, Direction.UP, Direction.UP,
                BlockPos.ZERO, new BlockPos(63, 0, 63), 63, false);

        assertTrue(ShapeSelectionLimiter.envelopeVolume(
                ShapeSelectionLimiter.clampDimensionsAndVolume(cube48, 48 * 48 * 48))
                <= 48L * 48L * 48L);
        assertTrue(ShapeSelectionLimiter.envelopeVolume(
                ShapeSelectionLimiter.clampDimensionsAndVolume(cube64, 64 * 64 * 64))
                <= 64L * 64L * 64L);
    }
}
