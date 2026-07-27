package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.util.EnumFacing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedBlockRotationStepTest {
    @Test
    void cameraNorthUsesEastAxisToTurnForwardTowardScreenUp() {
        assertEquals(
                EnumFacing.UP,
                PlacedBlockRotationStep.rotateDirection(
                        EnumFacing.NORTH,
                        EnumFacing.EAST,
                        1));
        assertEquals(
                EnumFacing.DOWN,
                PlacedBlockRotationStep.rotateDirection(
                        EnumFacing.NORTH,
                        EnumFacing.EAST,
                        -1));
    }

    @Test
    void cameraEastUsesSouthAxisToTurnForwardTowardScreenUp() {
        assertEquals(
                EnumFacing.UP,
                PlacedBlockRotationStep.rotateDirection(
                        EnumFacing.EAST,
                        EnumFacing.SOUTH,
                        1));
        assertEquals(
                EnumFacing.DOWN,
                PlacedBlockRotationStep.rotateDirection(
                        EnumFacing.EAST,
                        EnumFacing.SOUTH,
                        -1));
    }
}
