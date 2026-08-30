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

    @Test
    void nameDialogDrawAndHitRectanglesShareOneGeometry() {
        BlueprintWindowLayout.NameDialogGeometry layout =
                BlueprintWindowLayout.nameDialog(101, 55, 398, 125);
        assertEquals(111, layout.inputX);
        assertEquals(378, layout.inputW);
        assertEquals(431, layout.cancelX);
        assertEquals(355, layout.confirmX);
        assertEquals(156, layout.buttonY);
        assertEquals(128, layout.inputY);
    }

    @Test
    void materialViewportAndColumnsStayBounded() {
        BlueprintWindowLayout.MaterialDialogGeometry narrow =
                BlueprintWindowLayout.materialDialog(1, 20, 280, 120);
        assertEquals(300, narrow.width);
        assertEquals(150, narrow.height);
        assertEquals(280, narrow.listW);
        assertEquals(104, narrow.listH);
        assertEquals(1, narrow.columns());

        BlueprintWindowLayout.MaterialDialogGeometry wide =
                BlueprintWindowLayout.materialDialog(1, 20, 540, 319);
        assertEquals(520, wide.listW);
        assertEquals(273, wide.listH);
        assertEquals(2, wide.columns());
    }
}
