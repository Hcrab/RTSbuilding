package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class WindowButtonStyleTest {
    @Test
    void hoverOnlyChangesBackground() {
        assertEquals(WindowButtonStyle.BACKGROUND, WindowButtonStyle.background(false));
        assertEquals(WindowButtonStyle.HOVER_BACKGROUND, WindowButtonStyle.background(true));
        assertNotEquals(WindowButtonStyle.background(false), WindowButtonStyle.background(true));
    }

    @Test
    void disabledButtonUsesDedicatedTextWithoutChangingFrameTokens() {
        assertEquals(WindowButtonStyle.TEXT, WindowButtonStyle.text(true));
        assertEquals(WindowButtonStyle.TEXT_DISABLED, WindowButtonStyle.text(false));
        assertNotEquals(WindowButtonStyle.text(true), WindowButtonStyle.text(false));
    }
}
