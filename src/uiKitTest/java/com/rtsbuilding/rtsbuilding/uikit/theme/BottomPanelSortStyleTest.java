package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BottomPanelSortStyleTest {
    @Test
    void compactButtonsKeepReadableFrameContrast() {
        assertNotEquals(
                BottomPanelSortStyle.BUTTON_BACKGROUND,
                BottomPanelSortStyle.BUTTON_BORDER_LIGHT);
        assertNotEquals(
                BottomPanelSortStyle.BUTTON_BORDER_LIGHT,
                BottomPanelSortStyle.BUTTON_BORDER_DARK);
        assertNotEquals(
                BottomPanelSortStyle.BUTTON_BACKGROUND,
                BottomPanelSortStyle.BUTTON_TEXT);
    }
}
