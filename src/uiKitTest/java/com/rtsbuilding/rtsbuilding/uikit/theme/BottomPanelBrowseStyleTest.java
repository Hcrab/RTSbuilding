package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BottomPanelBrowseStyleTest {
    @Test
    void focusAndValueStatesUseExplicitSemanticColors() {
        assertEquals(BottomPanelBrowseStyle.SEARCH_IDLE_BACKGROUND,
                BottomPanelBrowseStyle.searchBackground(false));
        assertEquals(BottomPanelBrowseStyle.SEARCH_FOCUSED_BACKGROUND,
                BottomPanelBrowseStyle.searchBackground(true));
        assertEquals(BottomPanelBrowseStyle.CLEAR_IDLE_BACKGROUND,
                BottomPanelBrowseStyle.clearBackground(false));
        assertEquals(BottomPanelBrowseStyle.CLEAR_FOCUSED_BACKGROUND,
                BottomPanelBrowseStyle.clearBackground(true));
        assertEquals(BottomPanelBrowseStyle.MUTED_TEXT,
                BottomPanelBrowseStyle.clearText(false));
        assertEquals(BottomPanelBrowseStyle.TEXT,
                BottomPanelBrowseStyle.clearText(true));
    }
}
