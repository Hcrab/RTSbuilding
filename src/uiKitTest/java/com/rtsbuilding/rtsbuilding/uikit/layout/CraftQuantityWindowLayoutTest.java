package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CraftQuantityWindowLayoutTest {
    @Test
    void defaultGeometryMatchesProductionPopup() {
        CraftQuantityWindowLayout.Layout layout = CraftQuantityWindowLayout.resolve(1, 20, 236, 175);
        assertEquals(9, layout.x);
        assertEquals(27, layout.y);
        assertEquals(220, layout.w);
        assertEquals(2, CraftQuantityWindowLayout.visibleOptionRows(layout));
        assertEquals(177, layout.confirmX);
    }
}
