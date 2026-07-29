package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BottomPanelGridStyleTest {
    @Test
    void eachGridKeepsItsSemanticFrameAndSelectionColor() {
        assertEquals(0xAA111111, BottomPanelGridStyle.STORAGE.background.toArgb());
        assertEquals(0xFF596D84, BottomPanelGridStyle.CREATIVE.borderLight.toArgb());
        assertEquals(0xFFFFA553, BottomPanelGridStyle.FLUID.borderLight.toArgb());
        assertNotEquals(BottomPanelGridStyle.STORAGE.selectedOverlay.toArgb(),
                BottomPanelGridStyle.FLUID.selectedOverlay.toArgb());
    }

    @Test
    void recentFluidCountRemainsDistinctFromRecentItemCount() {
        assertEquals(0xFFE8F4C0, BottomPanelGridStyle.RECENT.countText.toArgb());
        assertEquals(0xFFBEE6FF, BottomPanelGridStyle.RECENT_FLUID_COUNT.toArgb());
    }
}
