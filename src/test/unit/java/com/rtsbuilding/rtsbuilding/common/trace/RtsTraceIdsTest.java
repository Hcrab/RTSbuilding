package com.rtsbuilding.rtsbuilding.common.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsTraceIdsTest {
    @Test
    void generatedIdsArePositiveUniqueAndFixedWidth() {
        long first = RtsTraceIds.nextClientTraceId();
        long second = RtsTraceIds.nextClientTraceId();

        assertTrue(first > 0L);
        assertTrue(second > 0L);
        assertNotEquals(first, second);
        assertEquals(16, RtsTraceIds.format(first).length());
        assertTrue(RtsTraceIds.format(first).matches("[0-9a-f]{16}"));
        assertEquals("-", RtsTraceIds.format(RtsTraceIds.NONE));
    }
}
