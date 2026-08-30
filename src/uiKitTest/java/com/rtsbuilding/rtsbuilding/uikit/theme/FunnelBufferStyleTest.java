package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class FunnelBufferStyleTest {
    @Test
    void toggleVisibilityHasDistinctSemanticColors() {
        assertEquals(FunnelBufferStyle.TOGGLE_VISIBLE, FunnelBufferStyle.toggle(true));
        assertEquals(FunnelBufferStyle.TOGGLE_HIDDEN, FunnelBufferStyle.toggle(false));
        assertNotEquals(FunnelBufferStyle.toggle(true), FunnelBufferStyle.toggle(false));
    }
}
