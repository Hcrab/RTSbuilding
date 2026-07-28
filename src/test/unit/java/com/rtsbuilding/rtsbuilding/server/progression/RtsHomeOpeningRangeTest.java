package com.rtsbuilding.rtsbuilding.server.progression;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsHomeOpeningRangeTest {
    @Test
    void acceptsTheHomeChunkAndAllEightNeighborChunks() {
        BlockPos home = new BlockPos(8, 64, 8);

        assertTrue(RtsHomeManager.isWithinHomeOpeningChunks(home, new BlockPos(8, 70, 8)));
        assertTrue(RtsHomeManager.isWithinHomeOpeningChunks(home, new BlockPos(-1, 70, -1)));
        assertTrue(RtsHomeManager.isWithinHomeOpeningChunks(home, new BlockPos(31, 70, 31)));
    }

    @Test
    void rejectsChunksOutsideTheThreeByThreeArea() {
        BlockPos home = new BlockPos(8, 64, 8);

        assertFalse(RtsHomeManager.isWithinHomeOpeningChunks(home, new BlockPos(32, 70, 8)));
        assertFalse(RtsHomeManager.isWithinHomeOpeningChunks(home, new BlockPos(8, 70, -17)));
    }
}
