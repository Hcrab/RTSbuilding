package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeBuiltins;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeCoverageCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiThemeDraftTest {
    @Test
    void globalTokenEditUpdatesEveryComponentThatConsumesTheToken() {
        UiThemeDraft draft = new UiThemeDraft(UiThemeBuiltins.nordCommand());
        UiColor replacement = UiColor.opaque(12, 180, 110);
        draft.setColor(UiThemeToken.ACCENT_PRIMARY, replacement);
        UiThemeDefinition snapshot = draft.snapshot();

        assertEquals("user:nord_command_custom", snapshot.id());
        assertEquals(replacement, snapshot.color(UiThemeToken.ACCENT_PRIMARY));
        for (UiThemeCoverageCatalog.ComponentFamily family
                : UiThemeCoverageCatalog.ComponentFamily.values()) {
            if (UiThemeCoverageCatalog.required(family).contains(UiThemeToken.ACCENT_PRIMARY)) {
                assertEquals(replacement,
                        snapshot.componentColor(family, UiThemeToken.ACCENT_PRIMARY));
            }
        }
    }

    @Test
    void legacyCannotEnterTheEditableDraftPath() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiThemeDraft(UiThemeBuiltins.legacy()));
    }
}
