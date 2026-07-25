package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacedBlockRotationStepTest {
    @Test
    void cameraNorthUsesEastAxisToTurnForwardTowardScreenUp() {
        assertEquals(
                Direction.UP,
                PlacedBlockRotationStep.rotateDirection(
                        Direction.NORTH,
                        Direction.EAST,
                        1));
        assertEquals(
                Direction.DOWN,
                PlacedBlockRotationStep.rotateDirection(
                        Direction.NORTH,
                        Direction.EAST,
                        -1));
    }

    @Test
    void cameraEastUsesSouthAxisToTurnForwardTowardScreenUp() {
        assertEquals(
                Direction.UP,
                PlacedBlockRotationStep.rotateDirection(
                        Direction.EAST,
                        Direction.SOUTH,
                        1));
        assertEquals(
                Direction.DOWN,
                PlacedBlockRotationStep.rotateDirection(
                        Direction.EAST,
                        Direction.SOUTH,
                        -1));
    }
}
