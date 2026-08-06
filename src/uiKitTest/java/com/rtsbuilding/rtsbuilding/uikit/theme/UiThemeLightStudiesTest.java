package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiThemeLightStudiesTest {
    @Test
    void fiveStudiesCoverEveryTokenWithoutEnteringTheBuiltinRegistry() {
        List<UiThemeDefinition> studies = UiThemeLightStudies.all();
        assertEquals(5, studies.size());
        for (UiThemeDefinition study : studies) {
            assertEquals(UiThemeToken.values().length, study.tokens().size(), study.id());
            assertTrue(study.editable(), study.id());
            assertEquals(UiThemeRenderMode.PALETTE, study.renderMode(), study.id());
            assertFalse(UiThemeBuiltins.createRegistry().contains(study.id()), study.id());
        }
    }

    @Test
    void criticalLightThemePairsMeetTheExistingProductContrastGate() {
        for (UiThemeDefinition study : UiThemeLightStudies.all()) {
            assertContrast(study, UiThemeToken.TEXT_PRIMARY, UiThemeToken.SURFACE, 4.5D);
            assertContrast(study, UiThemeToken.TEXT_SECONDARY, UiThemeToken.SURFACE, 4.5D);
            assertContrast(study, UiThemeToken.TEXT_ON_ACCENT, UiThemeToken.ACCENT_PRIMARY, 3.0D);
            assertContrast(study, UiThemeToken.FOCUS_RING, UiThemeToken.SURFACE, 3.0D);
        }
    }

    private static void assertContrast(UiThemeDefinition study, UiThemeToken foreground,
                                       UiThemeToken background, double minimum) {
        double ratio = contrast(study.color(foreground), study.color(background));
        assertTrue(ratio >= minimum, study.id() + ":" + foreground + "/" + background
                + " contrast=" + ratio + " requires=" + minimum);
    }

    private static double contrast(UiColor first, UiColor second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05D)
                / (Math.min(firstLuminance, secondLuminance) + 0.05D);
    }

    private static double luminance(UiColor color) {
        return 0.2126D * channel(color.red())
                + 0.7152D * channel(color.green())
                + 0.0722D * channel(color.blue());
    }

    private static double channel(int value) {
        double normalized = value / 255.0D;
        return normalized <= 0.04045D ? normalized / 12.92D
                : Math.pow((normalized + 0.055D) / 1.055D, 2.4D);
    }
}
