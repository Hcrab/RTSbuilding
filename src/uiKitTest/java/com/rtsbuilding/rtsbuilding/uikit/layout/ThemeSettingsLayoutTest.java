package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeSettingsLayoutTest {
    @Test
    void normalScreenKeepsPreferredWidth() {
        assertEquals(ThemeSettingsLayout.PREFERRED_WINDOW_W,
                ThemeSettingsLayout.preferredWindowWidth(1920));
    }

    @Test
    void twoTimesGuiScaleKeepsTheWholeWindowInsideTheViewport() {
        assertEquals(555, ThemeSettingsLayout.preferredWindowWidth(571));
        assertEquals(285, ThemeSettingsLayout.preferredWindowHeight(349));
    }

    @Test
    void extremelyNarrowViewportStopsAtTheThreeColumnHardLimit() {
        assertEquals(ThemeSettingsLayout.MIN_WINDOW_W,
                ThemeSettingsLayout.preferredWindowWidth(400));
        assertEquals(ThemeSettingsLayout.MIN_WINDOW_H,
                ThemeSettingsLayout.preferredWindowHeight(200));
    }
}
