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

    @Test
    void paletteTrackThemesEveryGridFrameWhileLegacyKeepsItsOriginalColors() {
        UiThemeRuntime.manager().activate(UiThemeBuiltins.CALIBRATED_ID);
        try {
            UiThemeDefinition theme = UiThemeRuntime.manager().active();
            assertEquals(theme.color(UiThemeToken.SLOT_IDLE).toArgb() & 0x00FFFFFF,
                    BottomPanelGridStyle.STORAGE.background.toArgb() & 0x00FFFFFF);
            assertEquals(theme.color(UiThemeToken.BORDER_STRONG).toArgb(),
                    BottomPanelGridStyle.CREATIVE.borderLight.toArgb());
            assertEquals(theme.color(UiThemeToken.BORDER_SOFT).toArgb(),
                    BottomPanelGridStyle.FLUID.borderDark.toArgb());
            assertEquals(theme.color(UiThemeToken.SLOT_SELECTED).toArgb(),
                    BottomPanelGridStyle.RECENT.selectedOverlay.toArgb());
            assertEquals(theme.color(UiThemeToken.TEXT_PRIMARY).toArgb(),
                    BottomPanelGridStyle.STORAGE.countText.toArgb());
        } finally {
            UiThemeRuntime.manager().fallBackToLegacy();
        }

        assertEquals(0xAA111111, BottomPanelGridStyle.STORAGE.background.toArgb());
        assertEquals(0xFF596D84, BottomPanelGridStyle.CREATIVE.borderLight.toArgb());
        assertEquals(0x00000000, BottomPanelGridStyle.RECENT.selectedOverlay.toArgb());
        assertEquals(0xFFFFA553, BottomPanelGridStyle.FLUID.borderLight.toArgb());
    }
}
