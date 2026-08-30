package com.rtsbuilding.rtsbuilding.client.screen.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BottomPanelLayoutTypesTest {
    @Test
    void panelUsesHalfOpenBoundsLikeKitLayouts() {
        BottomPanelLayoutTypes.BottomPanelLayout layout =
                new BottomPanelLayoutTypes.BottomPanelLayout(
                        100, 200, 900, 240,
                        0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0);

        assertTrue(layout.contains(100, 200));
        assertTrue(layout.contains(999.999, 439.999));
        assertFalse(layout.contains(1000, 200));
        assertFalse(layout.contains(100, 440));
    }
}
