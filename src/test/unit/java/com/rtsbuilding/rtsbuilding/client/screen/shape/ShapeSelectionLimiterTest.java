package com.rtsbuilding.rtsbuilding.client.screen.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeSelectionLimiterTest {
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
}
