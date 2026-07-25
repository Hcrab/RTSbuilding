package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowSliderStyleTest {
    @Test
    void trackAndKnobPreserveProductionColors() {
        assertEquals(0xFF07090D, WindowSliderStyle.TRACK_BACKGROUND.toArgb());
        assertEquals(0xFF313946, WindowSliderStyle.TRACK_FILL.toArgb());
        assertEquals(0xFF5FE36C, WindowSliderStyle.KNOB.toArgb());
    }
}
