package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageLinkDetailStyleTest {
    @Test
    void 悬停只切换动作框体的语义状态() {
        assertEquals(StorageLinkDetailStyle.IDLE_BACKGROUND,
                StorageLinkDetailStyle.background(false));
        assertEquals(StorageLinkDetailStyle.HOVER_BACKGROUND,
                StorageLinkDetailStyle.background(true));
        assertEquals(RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                StorageLinkDetailStyle.border(false));
        assertEquals(StorageLinkDetailStyle.HOVER_BORDER,
                StorageLinkDetailStyle.border(true));
    }
}
