package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickBuildWindowLayoutTest {
    @Test
    void geometryMatchesProductionTwoColumnPanel() {
        QuickBuildWindowLayout.Geometry g = QuickBuildWindowLayout.geometry(100, 50, false);
        assertEquals(108, g.buildModeX);
        assertEquals(191, g.destroyModeX);
        assertEquals(108, g.shapeX(0));
        assertEquals(148, g.shapeX(1));
        assertEquals(108, g.shapeX(2));
        assertEquals(154, g.shapeY(2));
        assertEquals(294, g.windowH);
    }

    @Test
    void destroyAddsExactlyOneShapeRow() {
        assertEquals(38, QuickBuildWindowLayout.windowHeight(true)
                - QuickBuildWindowLayout.windowHeight(false));
    }
}
