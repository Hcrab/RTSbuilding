package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowButtonLayoutTest {
    @Test
    void textInsetsAndBaselinePreserveProductionPixels() {
        assertEquals(72, WindowButtonLayout.textWidth(80));
        assertEquals(4, WindowButtonLayout.textWidth(5));
        assertEquals(27, WindowButtonLayout.textY(20, 22));
    }
}
