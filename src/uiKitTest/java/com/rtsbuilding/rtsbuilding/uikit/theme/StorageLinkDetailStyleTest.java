package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class StorageLinkDetailStyleTest {
    @Test
    void 悬停只切换动作框体的语义状态() {
        assertSame(StorageLinkDetailStyle.IDLE_BACKGROUND,
                StorageLinkDetailStyle.background(false));
        assertSame(StorageLinkDetailStyle.HOVER_BACKGROUND,
                StorageLinkDetailStyle.background(true));
        assertSame(RtsMainlineTheme.WINDOW_BORDER_LIGHT,
                StorageLinkDetailStyle.border(false));
        assertSame(StorageLinkDetailStyle.HOVER_BORDER,
                StorageLinkDetailStyle.border(true));
    }
}
