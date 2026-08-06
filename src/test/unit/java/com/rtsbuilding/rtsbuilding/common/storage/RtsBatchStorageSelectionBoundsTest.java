package com.rtsbuilding.rtsbuilding.common.storage;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RtsBatchStorageSelectionBoundsTest {
    @Test
    void normalizesReversedInclusiveCorners() {
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(
                        new BlockPos(5, 9, 7), new BlockPos(2, 3, 4));

        assertNotNull(bounds);
        assertEquals(new BlockPos(2, 3, 4), bounds.min());
        assertEquals(new BlockPos(5, 9, 7), bounds.max());
        assertEquals(4, bounds.width());
        assertEquals(7, bounds.height());
        assertEquals(4, bounds.depth());
        assertEquals(112L, bounds.volume());
    }

    @Test
    void acceptsExactHardBoundary() {
        RtsBatchStorageSelectionBounds.Bounds bounds =
                RtsBatchStorageSelectionBounds.normalize(
                        BlockPos.ZERO, new BlockPos(63, 63, 63));

        assertNotNull(bounds);
        assertEquals(RtsBatchStorageSelectionBounds.MAX_VOLUME, bounds.volume());
    }

    @Test
    void rejectsAnyAxisBeyondHardBoundary() {
        assertNull(RtsBatchStorageSelectionBounds.normalize(
                BlockPos.ZERO, new BlockPos(64, 0, 0)));
        assertNull(RtsBatchStorageSelectionBounds.normalize(
                BlockPos.ZERO, new BlockPos(0, 64, 0)));
        assertNull(RtsBatchStorageSelectionBounds.normalize(
                BlockPos.ZERO, new BlockPos(0, 0, 64)));
    }
}
