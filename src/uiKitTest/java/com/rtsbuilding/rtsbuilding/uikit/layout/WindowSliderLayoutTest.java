package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowSliderLayoutTest {
    @Test
    void geometryPreservesProductionTrackAndKnobPixels() {
        WindowSliderLayout.Geometry geometry = WindowSliderLayout.geometry(
                new UiRect(10, 20, 100, 18), 1, 256, 129);

        assertEquals(new UiRect(10, 27, 100, 4), geometry.track);
        assertEquals(new UiRect(11, 28, 98, 2), geometry.trackFill);
        assertEquals(new UiRect(56, 23, 8, 12), geometry.knob);
        assertEquals(129, geometry.value);
    }

    @Test
    void valueMappingClampsBothSidesAndUsesTheFullTrack() {
        UiRect bounds = new UiRect(10, 20, 100, 18);
        assertEquals(1, WindowSliderLayout.valueAt(bounds, 1, 256, -100));
        assertEquals(129, WindowSliderLayout.valueAt(bounds, 1, 256, 60));
        assertEquals(256, WindowSliderLayout.valueAt(bounds, 1, 256, 500));
        assertEquals(true, bounds.contains(10, 20));
        assertEquals(false, bounds.contains(110, 20));
    }
}
