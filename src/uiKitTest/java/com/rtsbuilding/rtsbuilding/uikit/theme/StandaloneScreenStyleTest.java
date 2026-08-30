package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StandaloneScreenStyleTest {
    @Test
    void 首页状态不会在生产层重新发明颜色() {
        assertSame(StandaloneScreenStyle.STATUS_ENABLED,
                StandaloneScreenStyle.progressionStatus(true));
        assertSame(StandaloneScreenStyle.STATUS_DISABLED,
                StandaloneScreenStyle.progressionStatus(false));
        assertSame(StandaloneScreenStyle.WARNING_TEXT,
                StandaloneScreenStyle.homeStatus(true));
        assertSame(StandaloneScreenStyle.INFO_VALUE,
                StandaloneScreenStyle.homeStatus(false));
    }

    @Test
    void 独立页面标题复用主线文字主题() {
        assertSame(RtsMainlineTheme.BUTTON_TEXT, StandaloneScreenStyle.TITLE_TEXT);
        assertEquals(0x66263545, StandaloneScreenStyle.SCROLLBAR_TRACK.toArgb());
    }
}
