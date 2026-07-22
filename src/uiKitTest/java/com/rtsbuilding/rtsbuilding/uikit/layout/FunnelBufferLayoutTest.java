package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FunnelBufferLayoutTest {
    @Test
    void matchesProductionRightEdgeAndRows() {
        assertEquals(1460, FunnelBufferLayout.panelX(1600));
        assertEquals(1532, FunnelBufferLayout.toggleX(1600));
        assertEquals(8, FunnelBufferLayout.visibleRows(196));
    }
}
