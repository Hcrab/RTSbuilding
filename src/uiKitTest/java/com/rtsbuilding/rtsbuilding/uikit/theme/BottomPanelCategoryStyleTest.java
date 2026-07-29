package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BottomPanelCategoryStyleTest {
    @Test
    void selectedAndIdleRowsUseExplicitSemanticFamilies() {
        assertEquals(BottomPanelCategoryStyle.ROW_IDLE_BACKGROUND,
                BottomPanelCategoryStyle.rowBackground(false));
        assertEquals(BottomPanelCategoryStyle.ROW_SELECTED_BACKGROUND,
                BottomPanelCategoryStyle.rowBackground(true));
        assertEquals(BottomPanelCategoryStyle.ROW_TEXT,
                BottomPanelCategoryStyle.rowText(false));
        assertEquals(BottomPanelCategoryStyle.ROW_SELECTED_TEXT,
                BottomPanelCategoryStyle.rowText(true));
        assertNotEquals(
                BottomPanelCategoryStyle.ROW_IDLE_BACKGROUND,
                BottomPanelCategoryStyle.ROW_SELECTED_BACKGROUND);
    }
}
