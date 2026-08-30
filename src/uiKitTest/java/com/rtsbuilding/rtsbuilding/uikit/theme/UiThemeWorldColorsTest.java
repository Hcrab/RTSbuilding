package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UiThemeWorldColorsTest {
    @AfterEach
    void restoreLegacy() {
        UiThemeRuntime.manager().activate(UiThemeBuiltins.LEGACY_ID);
    }

    @Test
    void legacyTrackKeepsExistingWorldOverlayColors() {
        UiThemeRuntime.manager().activate(UiThemeBuiltins.LEGACY_ID);

        assertEquals(0xFF4CBFFF, UiThemeWorldColors.SHAPE_SELECTION.toArgb());
        assertEquals(0xFF29C7FF, UiThemeWorldColors.CHUNK_GUIDE_PRIMARY.toArgb());
        assertEquals(0xFFFFE029, UiThemeWorldColors.CHUNK_GUIDE_SECONDARY.toArgb());
        assertEquals(0xFF61FF6B, UiThemeWorldColors.DESTROY_CONFIRMED.toArgb());
    }

    @Test
    void paletteTrackReadsWorldRenderingComponentOverridesDynamically() {
        UiThemeRuntime.manager().activate(UiThemeBuiltins.NORD_ID);

        int expected = UiThemeRuntime.manager().active().componentColor(
                UiThemeCoverageCatalog.ComponentFamily.WORLD_RENDERING,
                UiThemeToken.WORLD_SELECTION).toArgb();
        assertEquals(expected, UiThemeWorldColors.SHAPE_SELECTION.toArgb());
        assertEquals(expected, UiThemeWorldColors.CHUNK_GUIDE_PRIMARY.toArgb());
        assertNotEquals(0xFF4CBFFF, UiThemeWorldColors.SHAPE_SELECTION.toArgb());
    }
}
