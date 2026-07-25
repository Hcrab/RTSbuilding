package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class DeveloperScreenStyleTest {
    @Test
    void 开发者页标题复用主线且状态保持诊断强调() {
        assertSame(RtsMainlineTheme.BUTTON_TEXT, DeveloperScreenStyle.TITLE);
        assertEquals(0xF0101820, DeveloperScreenStyle.BACKGROUND.toArgb());
        assertEquals(0xFFFFD27F, DeveloperScreenStyle.ACTIVE_STATUS.toArgb());
    }
}
