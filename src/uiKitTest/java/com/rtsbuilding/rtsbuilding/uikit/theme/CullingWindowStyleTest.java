package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class CullingWindowStyleTest {
    @Test
    void hoverOnlyPromotesTheDangerousButtonSurface() {
        CullingWindowStyle.DeleteVisual idle = CullingWindowStyle.deleteButton(false);
        CullingWindowStyle.DeleteVisual hover = CullingWindowStyle.deleteButton(true);

        assertNotEquals(idle.background, hover.background);
        assertNotEquals(idle.border, hover.border);
        assertEquals(idle.darkBorder, hover.darkBorder);
        assertEquals(CullingWindowStyle.DELETE_DARK_BORDER, idle.darkBorder);
    }
}
