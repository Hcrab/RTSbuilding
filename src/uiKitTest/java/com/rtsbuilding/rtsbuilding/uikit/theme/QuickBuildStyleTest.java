package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class QuickBuildStyleTest {
    @Test
    void 模式状态优先级保持禁用高于活动和悬停() {
        QuickBuildStyle.ModeVisual disabled =
                QuickBuildStyle.mode(false, true, true);
        assertEquals(QuickBuildStyle.MODE_DISABLED_BACKGROUND, disabled.background);
        assertEquals(QuickBuildStyle.MODE_DISABLED_BORDER, disabled.border);
        assertEquals(QuickBuildStyle.MODE_DISABLED_TEXT, disabled.text);

        QuickBuildStyle.ModeVisual active =
                QuickBuildStyle.mode(true, true, true);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_BACKGROUND, active.background);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_BORDER, active.border);
        assertEquals(QuickBuildStyle.MODE_ACTIVE_TEXT, active.text);
    }

    @Test
    void 空闲和悬停只改变按钮chrome而不伪装为活动模式() {
        QuickBuildStyle.ModeVisual idle =
                QuickBuildStyle.mode(true, false, false);
        QuickBuildStyle.ModeVisual hover =
                QuickBuildStyle.mode(true, false, true);

        assertNotEquals(idle.background, hover.background);
        assertNotEquals(idle.border, hover.border);
        assertEquals(idle.text, hover.text);
        assertNotEquals(QuickBuildStyle.MODE_ACTIVE_BACKGROUND, hover.background);
    }

    @Test
    void 正式模式圆点使用明确的不染色色值() {
        assertEquals(0xFFFFFFFF, QuickBuildStyle.ICON_TINT.toArgb());
    }
}
