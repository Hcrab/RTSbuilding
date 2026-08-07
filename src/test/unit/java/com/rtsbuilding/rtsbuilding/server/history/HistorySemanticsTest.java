package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistorySemanticsTest {
    @Test
    void operationFreezesModeAndActionAtRecordTime() {
        assertTrue(HistoryOperation.CREATIVE_BREAK.creative());
        assertTrue(HistoryOperation.CREATIVE_BREAK.destructive());
        assertFalse(HistoryOperation.SURVIVAL_PLACEMENT.creative());
        assertFalse(HistoryOperation.SURVIVAL_PLACEMENT.destructive());
    }

    @Test
    void executionResultRetainsExactCompletedPositions() {
        BlockPos first = new BlockPos(1, 2, 3);
        BlockPos skippedMiddle = new BlockPos(4, 5, 6);
        BlockPos last = new BlockPos(7, 8, 9);
        HistoryExecutionResult result = new HistoryExecutionResult(2,
                new LinkedHashSet<BlockPos>(Arrays.asList(first, last)));

        assertEquals(2, result.executedCount());
        assertTrue(result.completedPositions().contains(first));
        assertFalse(result.completedPositions().contains(skippedMiddle));
        assertTrue(result.completedPositions().contains(last));
    }
}
