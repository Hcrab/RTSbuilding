package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BottomPanelHeaderStyleTest {
    @Test
    void 页签活动悬停和空闲状态都有明确语义色() {
        assertEquals(
                BottomPanelHeaderStyle.TAB_ACTIVE_BACKGROUND,
                BottomPanelHeaderStyle.tabBackground(true, false));
        assertEquals(
                BottomPanelHeaderStyle.TAB_HOVER_BACKGROUND,
                BottomPanelHeaderStyle.tabBackground(false, true));
        assertEquals(
                BottomPanelHeaderStyle.TAB_IDLE_BACKGROUND,
                BottomPanelHeaderStyle.tabBackground(false, false));
        assertNotEquals(
                BottomPanelHeaderStyle.tabText(true),
                BottomPanelHeaderStyle.tabText(false));
    }

    @Test
    void 刷新脏状态和扫描状态不会退回普通按钮颜色() {
        assertEquals(
                BottomPanelHeaderStyle.REFRESH_SCANNING_BACKGROUND,
                BottomPanelHeaderStyle.refreshBackground(
                        true, true, true));
        assertEquals(
                BottomPanelHeaderStyle.REFRESH_DIRTY_BACKGROUND,
                BottomPanelHeaderStyle.refreshBackground(
                        false, true, false));
        assertEquals(
                BottomPanelHeaderStyle.REFRESH_DIRTY_HOVER_BACKGROUND,
                BottomPanelHeaderStyle.refreshBackground(
                        false, true, true));
        assertEquals(
                BottomPanelHeaderStyle.ACTION_IDLE_BACKGROUND,
                BottomPanelHeaderStyle.refreshBackground(
                        false, false, false));
    }
}
