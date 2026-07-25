package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowTextBoxStyleTest {
    @Test
    void focusChangesOnlyTheBorderFamily() {
        assertEquals(0xFFEAF2FF, WindowTextBoxStyle.TEXT.toArgb());
        assertEquals(0xFF777F8B, WindowTextBoxStyle.TEXT_UNEDITABLE.toArgb());
        assertEquals(0xFF202832, WindowTextBoxStyle.BACKGROUND.toArgb());
        assertEquals(0xFF68778A, WindowTextBoxStyle.PLACEHOLDER.toArgb());
        assertEquals(WindowTextBoxStyle.BORDER, WindowTextBoxStyle.border(false));
        assertEquals(WindowTextBoxStyle.BORDER_FOCUSED, WindowTextBoxStyle.border(true));
    }
}
