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

    @Test
    void nativePixelTextureIsCenteredWithoutChangingItsSize() {
        assertEquals(104, WindowButtonLayout.nativeTextureX(100, 32, 24));
        assertEquals(54, WindowButtonLayout.nativeTextureY(50, 32, 24));
        assertEquals(20, WindowButtonLayout.nativeTextureX(20, 24, 24));
    }
}
