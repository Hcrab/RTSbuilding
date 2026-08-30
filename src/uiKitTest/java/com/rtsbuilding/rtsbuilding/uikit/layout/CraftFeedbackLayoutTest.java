package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftFeedbackLayoutTest {
    @Test
    void 行数与溢出高度保持有界() {
        assertEquals(0, CraftFeedbackLayout.visibleRows(-1));
        assertEquals(4, CraftFeedbackLayout.visibleRows(8));
        assertEquals(54, CraftFeedbackLayout.panelHeight(0));
        assertEquals(140, CraftFeedbackLayout.panelHeight(8));
        assertEquals(86, CraftFeedbackLayout.panelX(400));
        assertEquals(18, CraftFeedbackLayout.panelY(0));
        assertEquals(58, CraftFeedbackLayout.panelY(58));
    }
}
