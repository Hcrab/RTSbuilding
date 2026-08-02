package com.rtsbuilding.rtsbuilding.client.compat;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsGuiCompatWorldStabilityGateTest {
    @Test
    void requiresConsecutiveTicksAtTheSameBlockPosition() {
        RtsGuiCompatWorldStabilityGate gate = new RtsGuiCompatWorldStabilityGate(3);

        assertFalse(gate.tick(true, new BlockPos(10, 64, 10)));
        assertFalse(gate.tick(true, new BlockPos(10, 64, 10)));
        assertTrue(gate.tick(true, new BlockPos(10, 64, 10)));
    }

    @Test
    void positionChangeRestartsTheStabilityWindow() {
        RtsGuiCompatWorldStabilityGate gate = new RtsGuiCompatWorldStabilityGate(3);

        assertFalse(gate.tick(true, new BlockPos(10, 64, 10)));
        assertFalse(gate.tick(true, new BlockPos(10, 64, 10)));
        assertFalse(gate.tick(true, new BlockPos(20, 77, 51)));
        assertFalse(gate.tick(true, new BlockPos(20, 77, 51)));
        assertTrue(gate.tick(true, new BlockPos(20, 77, 51)));
    }

    @Test
    void anUnplayableTickClearsPreviouslyAccumulatedStability() {
        RtsGuiCompatWorldStabilityGate gate = new RtsGuiCompatWorldStabilityGate(2);
        BlockPos spawn = new BlockPos(20, 77, 51);

        assertFalse(gate.tick(true, spawn));
        assertFalse(gate.tick(false, spawn));
        assertFalse(gate.tick(true, spawn));
        assertTrue(gate.tick(true, spawn));
    }
}
