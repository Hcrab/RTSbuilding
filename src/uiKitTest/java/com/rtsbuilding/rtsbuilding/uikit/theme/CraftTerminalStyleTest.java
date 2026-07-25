package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CraftTerminalStyleTest {
    @Test
    void carried物品与悬停只切换对应语义背景() {
        assertSame(CraftTerminalStyle.IMPORT_EMPTY_BACKGROUND,
                CraftTerminalStyle.importBackground(false));
        assertSame(CraftTerminalStyle.IMPORT_READY_BACKGROUND,
                CraftTerminalStyle.importBackground(true));
        assertSame(CraftTerminalStyle.SLOT_BACKGROUND,
                CraftTerminalStyle.slotBackground(false));
        assertSame(CraftTerminalStyle.SLOT_HOVER_BACKGROUND,
                CraftTerminalStyle.slotBackground(true));
    }

    @Test
    void 公共按钮继续复用主线主题() {
        assertSame(RtsMainlineTheme.BUTTON_BORDER_LIGHT,
                CraftTerminalStyle.BUTTON_BORDER_LIGHT);
        assertSame(RtsMainlineTheme.BUTTON_BORDER_DARK,
                CraftTerminalStyle.BUTTON_BORDER_DARK);
        assertSame(RtsMainlineTheme.BUTTON_TEXT,
                CraftTerminalStyle.BUTTON_TEXT);
        assertEquals(0xFFEAF2FF, CraftTerminalStyle.TEXT.toArgb());
    }
}
