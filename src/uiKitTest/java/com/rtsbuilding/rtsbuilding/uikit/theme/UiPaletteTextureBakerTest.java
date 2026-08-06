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

    @Test
    void defaultButtonRecolorPreservesLegacyPixelTopology() {
        int highlight = 0xFFA6CCF2;
        int fill = 0xFF445468;
        int corner = 0xFF404E64;
        int shadow = 0xFF1A202A;
        int[] legacyHoverTile = {
                highlight, highlight, highlight, corner,
                highlight, fill, fill, shadow,
                highlight, fill, fill, shadow,
                corner, shadow, shadow, shadow
        };

        int[] recolored = UiPaletteTextureBaker.bake(
                legacyHoverTile,
                UiIndexedTextureSpec.LEGACY_DEFAULT_BUTTON,
                UiThemeBuiltins.calibratedDark(),
                UiTextureState.HOVER);

        assertSamePixelClasses(legacyHoverTile, recolored);
        assertNotEquals(legacyHoverTile[0], recolored[0]);
    }

    @Test
    void modeButtonKeepsTheLegacyBevelAndTransparentCorners() {
        int[] activePixels = {
                0, 0xFF536679, 0xFFE0FFDA,
                0xFFB3FF9C, 0xFF72BA70, 0xFFDAFFDC
        };
        int[] recolored = UiPaletteTextureBaker.bake(
                activePixels,
                UiIndexedTextureSpec.LEGACY_MODE_BUTTON,
                UiThemeBuiltins.calibratedDark(),
                UiTextureState.ACTIVE);

        assertEquals(0, recolored[0]);
        assertNotEquals(recolored[1], recolored[2]);
        assertNotEquals(recolored[2], recolored[3]);
        assertNotEquals(recolored[3], recolored[4]);
        assertEquals(recolored[2], recolored[5]);
    }

    @Test
    void settingsSwitchIndexesEveryLegacyAtlasColor() {
        int[] atlasColors = {
                0,
                0xFF0D1117, 0xFF363F47, 0xFF54616E, 0xFF696D88,
                0xFF72BA70, 0xFF7692AC, 0xFF788C9F, 0xFF7E91A5,
                0xFF878FA5, 0xFFB3FF9C, 0xFFB4D2EE, 0xFFD4FFFF,
                0xFFE0FFDA
        };

        int[] recolored = UiPaletteTextureBaker.bake(
                atlasColors,
                UiIndexedTextureSpec.LEGACY_SETTINGS_SWITCH,
                UiThemeBuiltins.nordCommand(),
                UiTextureState.ACTIVE);

        assertEquals(0, recolored[0]);
        for (int index = 1; index < recolored.length; index++) {
            assertNotEquals(0, recolored[index]);
        }
    }

    @Test
    void contributorTerminalButtonKeepsHighlightFillAndShadowLayers() {
        int[] source = {
                0xFF536679,
                0xFF324153,
                0xFF1A202A
        };
        int[] idle = UiPaletteTextureBaker.bake(
                source,
                UiIndexedTextureSpec.CONTRIBUTOR_TERMINAL_BUTTON,
                UiThemeBuiltins.calibratedDark(),
                UiTextureState.INACTIVE);
        int[] hover = UiPaletteTextureBaker.bake(
                source,
                UiIndexedTextureSpec.CONTRIBUTOR_TERMINAL_BUTTON,
                UiThemeBuiltins.calibratedDark(),
                UiTextureState.HOVER);

        assertNotEquals(idle[0], idle[1]);
        assertNotEquals(idle[1], idle[2]);
        assertNotEquals(idle[0], idle[2]);
        assertNotEquals(idle[0], hover[0]);
        assertNotEquals(idle[1], hover[1]);
    }

    @Test
    void v2TerminalSortButtonMapsChromeAndBothGlyphTonesWithoutChangingTopology() {
        int[] source = {
                0xFF536679,
                0xFF324153,
                0xFF1A202A,
                0xFFC3C2D0,
                0xFF7F7E8E
        };
        int[] recolored = UiPaletteTextureBaker.bake(
                source,
                UiIndexedTextureSpec.V2_TERMINAL_SORT_BUTTON,
                UiThemeBuiltins.calibratedDark(),
                UiTextureState.HOVER);

        assertSamePixelClasses(source, recolored);
        assertNotEquals(recolored[0], recolored[1]);
        assertNotEquals(recolored[1], recolored[2]);
        assertNotEquals(recolored[3], recolored[4]);
    }

    /** 换色前后，相同源色必须仍相同，不同源色也不得被合并成新几何。 */
    private static void assertSamePixelClasses(int[] source, int[] output) {
        assertEquals(source.length, output.length);
        for (int left = 0; left < source.length; left++) {
            for (int right = 0; right < source.length; right++) {
                assertEquals(source[left] == source[right],
                        output[left] == output[right],
                        "pixel class changed at " + left + "," + right);
            }
        }
    }
}
