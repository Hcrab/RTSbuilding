package com.rtsbuilding.rtsbuilding.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RtsCountUtilTest {

    @Test
    void sanitizeCountNegativeToZero() {
        assertEquals(0L, RtsCountUtil.sanitizeCount(-1L));
    }

    @Test
    void sanitizeCountZeroToZero() {
        assertEquals(0L, RtsCountUtil.sanitizeCount(0L));
    }

    @Test
    void sanitizeCountPositivePreserved() {
        assertEquals(42L, RtsCountUtil.sanitizeCount(42L));
    }

    @Test
    void saturatedAddNormal() {
        assertEquals(15L, RtsCountUtil.saturatedAdd(10L, 5L));
    }

    @Test
    void saturatedAddOverflow() {
        assertEquals(Long.MAX_VALUE, RtsCountUtil.saturatedAdd(Long.MAX_VALUE - 1, 100L));
    }

    @Test
    void saturatedAddMaxValue() {
        assertEquals(Long.MAX_VALUE, RtsCountUtil.saturatedAdd(Long.MAX_VALUE, 1L));
    }

    @Test
    void saturatedAddBothMax() {
        assertEquals(Long.MAX_VALUE, RtsCountUtil.saturatedAdd(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void saturatedAddNegativeSanitized() {
        assertEquals(10L, RtsCountUtil.saturatedAdd(10L, -5L));
    }

    @Test
    void mergeCountNullMap() {
        RtsCountUtil.mergeCount(null, "key", 10L);
    }

    @Test
    void mergeCountNullKey() {
        Map<String, Long> map = new HashMap<>();
        RtsCountUtil.mergeCount(map, null, 10L);
        assertTrue(map.isEmpty());
    }

    @Test
    void mergeCountBlankKey() {
        Map<String, Long> map = new HashMap<>();
        RtsCountUtil.mergeCount(map, "  ", 10L);
        assertTrue(map.isEmpty());
    }

    @Test
    void mergeCountNegativeAmount() {
        Map<String, Long> map = new HashMap<>();
        RtsCountUtil.mergeCount(map, "diamond", -5L);
        assertTrue(map.isEmpty());
    }

    @Test
    void mergeCountNewEntry() {
        Map<String, Long> map = new HashMap<>();
        RtsCountUtil.mergeCount(map, "diamond", 64L);
        assertEquals(64L, map.get("diamond"));
    }

    @Test
    void mergeCountExistingEntry() {
        Map<String, Long> map = new HashMap<>();
        map.put("diamond", 64L);
        RtsCountUtil.mergeCount(map, "diamond", 64L);
        assertEquals(128L, map.get("diamond"));
    }

    @Test
    void mergeCountSaturatedOverflow() {
        Map<String, Long> map = new HashMap<>();
        map.put("diamond", Long.MAX_VALUE - 10);
        RtsCountUtil.mergeCount(map, "diamond", 100L);
        assertEquals(Long.MAX_VALUE, map.get("diamond"));
    }
}
