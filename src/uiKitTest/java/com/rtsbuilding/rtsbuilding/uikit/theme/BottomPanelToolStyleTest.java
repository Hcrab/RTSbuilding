package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BottomPanelToolStyleTest {
    @Test
    void slotStatesUseExplicitSemanticColors() {
        assertEquals(
                BottomPanelToolStyle.HOTBAR_IDLE_BACKGROUND,
                BottomPanelToolStyle.hotbarBackground(false, false));
        assertEquals(
                BottomPanelToolStyle.HOTBAR_SELECTED_BACKGROUND,
                BottomPanelToolStyle.hotbarBackground(false, true));
        assertEquals(
                BottomPanelToolStyle.EMPTY_HAND_SELECTED_BACKGROUND,
                BottomPanelToolStyle.hotbarBackground(true, true));
        assertEquals(
                BottomPanelToolStyle.EMPTY_HAND_BORDER_LIGHT,
                BottomPanelToolStyle.hotbarBorderLight(true));
        assertEquals(
                BottomPanelToolStyle.PIN_FILLED_BACKGROUND,
                BottomPanelToolStyle.pinBackground(true));
        assertEquals(
                BottomPanelToolStyle.PIN_COUNT_EMPTY,
                BottomPanelToolStyle.pinCount(0L));
        assertEquals(
                BottomPanelToolStyle.PIN_COUNT_AVAILABLE,
                BottomPanelToolStyle.pinCount(1L));
    }
}
