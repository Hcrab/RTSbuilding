package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintWindowLayoutTest {
    @Test
    void placementGeometryMatchesProductionWindowRows() {
        BlueprintWindowLayout.Geometry layout = BlueprintWindowLayout.geometry(false, 10, 20, 224, 292);
        assertEquals(22, layout.x);
        assertEquals(28, layout.y);
        assertEquals(200, layout.width);
        assertEquals(284, layout.footerY);
        assertEquals(260, layout.actionY);
        assertEquals(218, layout.statusY);
    }

    @Test
    void captureUsesSingleFooterRowAndSameStatusGap() {
        BlueprintWindowLayout.Geometry layout = BlueprintWindowLayout.geometry(true, 10, 20, 300, 140);
        assertEquals(132, layout.footerY);
        assertEquals(90, layout.statusY);
    }
}
