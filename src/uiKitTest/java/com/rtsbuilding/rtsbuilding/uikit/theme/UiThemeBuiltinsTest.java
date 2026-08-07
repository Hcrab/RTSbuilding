package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeBuiltinsTest {
    @Test
    void fiveBuiltinsCoverEveryTokenAndKeepTheTwoTracksExplicit() {
        List<UiThemeDefinition> definitions = UiThemeBuiltins.createRegistry().snapshot();
        assertEquals(5, definitions.size());
        for (UiThemeDefinition definition : definitions) {
            assertEquals(UiThemeToken.values().length, definition.tokens().size(), definition.id());
            for (UiThemeToken token : UiThemeToken.values()) {
                assertNotNull(definition.color(token), definition.id() + ":" + token.serializedId());
            }
        }

        UiThemeDefinition legacy = definitions.get(0);
        assertEquals(UiThemeBuiltins.LEGACY_ID, legacy.id());
        assertEquals(UiThemeRenderMode.LEGACY_DIRECT, legacy.renderMode());
        assertFalse(legacy.editable());
        for (int index = 1; index < definitions.size(); index++) {
            assertEquals(UiThemeRenderMode.PALETTE, definitions.get(index).renderMode());
            assertTrue(definitions.get(index).editable());
            assertEquals(UiThemeBuiltins.PIXEL_TEXTURE_SET, definitions.get(index).textureSet());
        }
    }

    @Test
    void currentLegacySeedsStayPinnedToTheExistingLook() {
        UiThemeDefinition legacy = UiThemeBuiltins.legacy();
        assertEquals(0xC0101116, legacy.color(UiThemeToken.TOP_BAR).toArgb());
        assertEquals(0xD014151A, legacy.color(UiThemeToken.BOTTOM_BAR).toArgb());
        assertEquals(0xFF161C24, legacy.color(UiThemeToken.SURFACE).toArgb());
        assertEquals(0xFFF2F7FF, legacy.color(UiThemeToken.TEXT_PRIMARY).toArgb());
        assertEquals(0xFF7CCB93, legacy.color(UiThemeToken.ACCENT_PRIMARY).toArgb());
    }

    @Test
    void missingTokenIsNeverSilentlyFilledFromLegacy() {
        EnumMap<UiThemeToken, UiColor> incomplete =
                new EnumMap<UiThemeToken, UiColor>(UiThemeToken.class);
        incomplete.putAll(UiThemeBuiltins.calibratedDark().tokens());
        incomplete.remove(UiThemeToken.WORLD_INVALID);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new UiThemeDefinition("test:incomplete", "name", "author", "description",
                        UiThemeRenderMode.PALETTE, UiThemeBuiltins.PIXEL_TEXTURE_SET,
                        true, incomplete));
        assertTrue(error.getMessage().contains("world_invalid"));
    }

    @Test
    void userThemeCannotPretendToBeEditableLegacy() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new UiThemeDefinition("test:fake_legacy", "name", "author", "description",
                        UiThemeRenderMode.LEGACY_DIRECT, "test:any", true,
                        UiThemeBuiltins.legacy().tokens()));
        assertTrue(error.getMessage().contains("immutable"));
    }
}
