package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TooltipStyleTest {
    @Test
    void 合成可用性不会与数量和普通说明混色() {
        assertSame(TooltipStyle.ACTION_AVAILABLE, TooltipStyle.craftChoice(true));
        assertSame(TooltipStyle.ERROR, TooltipStyle.craftChoice(false));
        assertEquals(0xFFFFD8B8, TooltipStyle.COUNT.toArgb());
        assertEquals(0xFFCFE3F7, TooltipStyle.DETAIL.toArgb());
    }
}
