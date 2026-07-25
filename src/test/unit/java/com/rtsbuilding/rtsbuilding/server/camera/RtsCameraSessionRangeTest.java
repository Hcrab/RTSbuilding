package com.rtsbuilding.rtsbuilding.server.camera;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCameraSessionRangeTest {
    @Test
    void sessionActionRangeIsSquareAroundOpeningAnchor() {
        double anchorX = 100.5D;
        double anchorZ = -39.5D;

        assertTrue(RtsCameraManager.isWithinSessionSquare(
                anchorX, anchorZ, 16.0D, new BlockPos(116, 200, -24)));
        assertTrue(RtsCameraManager.isWithinSessionSquare(
                anchorX, anchorZ, 16.0D, new BlockPos(84, -20, -56)));
        assertFalse(RtsCameraManager.isWithinSessionSquare(
                anchorX, anchorZ, 16.0D, new BlockPos(117, 70, -39)));
        assertFalse(RtsCameraManager.isWithinSessionSquare(
                anchorX, anchorZ, 16.0D, new BlockPos(100, 70, -57)));
    }

    @Test
    void invalidGeometryNeverAllowsAnAction() {
        assertFalse(RtsCameraManager.isWithinSessionSquare(
                Double.NaN, 0.5D, 16.0D, BlockPos.ZERO));
        assertFalse(RtsCameraManager.isWithinSessionSquare(
                0.5D, 0.5D, -1.0D, BlockPos.ZERO));
        assertFalse(RtsCameraManager.isWithinSessionSquare(
                0.5D, 0.5D, 16.0D, null));
    }
}
