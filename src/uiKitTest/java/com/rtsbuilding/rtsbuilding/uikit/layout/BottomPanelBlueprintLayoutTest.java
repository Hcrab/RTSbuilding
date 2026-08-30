package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelBlueprintLayoutTest {
    @Test
    void resolvesSharedProductionContentArea() {
        BottomPanelBlueprintLayout layout =
                BottomPanelBlueprintLayout.resolve(100, 200, 900, 240);

        assertEquals(108, layout.content.x);
        assertEquals(222, layout.content.y);
        assertEquals(884, layout.content.width);
        assertEquals(214, layout.content.height);
        assertTrue(layout.content.contains(108, 222));
        assertFalse(layout.content.contains(992, 222));
    }

    @Test
    void preservesMinimumContentSizeForNarrowPanels() {
        BottomPanelBlueprintLayout layout =
                BottomPanelBlueprintLayout.resolve(10, 20, 12, 22);

        assertEquals(80, layout.content.width);
        assertEquals(24, layout.content.height);
    }
}
