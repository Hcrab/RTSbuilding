package com.rtsbuilding.rtsbuilding.common.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RtsTraceIdsTest {
  @Test
  void generatedIdsAreNonZeroUniqueAndFixedWidth() {
    Set<Long> ids = new HashSet<>();
    for (int i = 0; i < 10_000; i++) {
      long traceId = RtsTraceIds.nextClientTraceId();
      assertNotEquals(RtsTraceIds.NONE, traceId);
      assertTrue(ids.add(traceId));
      String formatted = RtsTraceIds.format(traceId);
      assertEquals(16, formatted.length());
      assertEquals(formatted.toLowerCase(Locale.ROOT), formatted);
    }
    assertEquals(8, RtsTraceIds.runId().length());
    assertEquals("0000000000000000", RtsTraceIds.format(RtsTraceIds.NONE));
  }
}
