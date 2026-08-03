package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiPaletteTextureBakerTest {
    @Test
    void pr133ThreeTonePixelsResolveToSemanticThemeRoles() {
        UiThemeDefinition theme = UiThemeBuiltins.calibratedDark();
        int[] source = { 0, 0xFF445468, 0xFF1A202A, 0xFFA6CCF2 };

        int[] idle = UiPaletteTextureBaker.bake(source,
                UiIndexedTextureSpec.PR133_THREE_TONE, theme, UiTextureState.INACTIVE);
        assertEquals(0, idle[0]);
        assertEquals(theme.color(UiThemeToken.CONTROL_IDLE).toArgb(), idle[1]);
        assertEquals(theme.color(UiThemeToken.SURFACE_SUNKEN).toArgb(), idle[2]);
        assertEquals(theme.color(UiThemeToken.ICON_MUTED).toArgb(), idle[3]);

        int[] active = UiPaletteTextureBaker.bake(source,
                UiIndexedTextureSpec.PR133_THREE_TONE, theme, UiTextureState.ACTIVE);
        assertEquals(theme.color(UiThemeToken.CONTROL_SELECTED).toArgb(), active[1]);
        assertEquals(theme.color(UiThemeToken.ACCENT_SECONDARY).toArgb(), active[2]);
        assertEquals(theme.color(UiThemeToken.ICON_ON_ACCENT).toArgb(), active[3]);
    }

    @Test
    void unknownOpaquePixelFailsWithItsExactIndex() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> UiPaletteTextureBaker.bake(new int[] { 0xFF010203 },
                        UiIndexedTextureSpec.PR133_THREE_TONE,
                        UiThemeBuiltins.nordCommand(), UiTextureState.HOVER));
        assertTrue(error.getMessage().contains("#FF010203"));
        assertTrue(error.getMessage().contains("at 0"));
    }

    @Test
    void legacyNeverEntersPaletteBaker() {
        assertThrows(IllegalArgumentException.class,
                () -> UiPaletteTextureBaker.bake(new int[0],
                        UiIndexedTextureSpec.PR133_THREE_TONE,
                        UiThemeBuiltins.legacy(), UiTextureState.INACTIVE));
    }
}
