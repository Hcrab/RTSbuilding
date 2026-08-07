package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class QuickBuildWindowLayoutTest {
    @Test
    void geometryMatchesProductionTwoModePanelWithSharedCatalog() {
        QuickBuildWindowLayout.Geometry g = QuickBuildWindowLayout.geometry(100, 50, false);
        assertEquals(106, g.buildModeX);
        assertEquals(173, g.destroyModeX);
        assertEquals(106, g.shapeX(0));
        assertEquals(138, g.shapeX(1));
        assertEquals(106, g.shapeX(2));
        assertEquals(141, g.shapeY(2));
        assertEquals(288, g.windowH);
        assertEquals(new UiRect(106, 70, 64, 14), g.buildMode);
        assertEquals(new UiRect(173, 70, 64, 14), g.destroyMode);
        assertEquals(91, g.catalogY);
        assertEquals(64, g.catalogW);
        assertEquals(new UiRect(106, 283, 132, 3), g.progress);
        assertEquals(111, g.chainLabelY);
        assertEquals(122, g.chainSliderY);
        assertEquals(290, g.statusTextY);
        assertEquals(287, g.statusItemY);
        assertEquals(225, g.chainValueX(50));
        assertEquals(206, g.missingTextX(200));
        assertEquals(221, g.missingIconX(206, 12));
    }

    @Test
    void buildAndDestroyKeepOneFixedWindowFrame() {
        assertEquals(144, QuickBuildWindowLayout.WINDOW_W);
        assertEquals(288, QuickBuildWindowLayout.WINDOW_H);
        assertEquals(QuickBuildWindowLayout.windowHeight(true),
                QuickBuildWindowLayout.windowHeight(false));
        assertEquals(QuickBuildWindowLayout.geometry(100, 50, true).windowH,
                QuickBuildWindowLayout.geometry(100, 50, false).windowH);
    }

    @Test
    void sliderAndDefaultDockGeometryUseNamedKitRules() {
        assertEquals(42, QuickBuildWindowLayout.chainSliderWidth(144));
        assertEquals(14, QuickBuildWindowLayout.CHAIN_SLIDER_H);
        assertEquals(1, QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET);
        assertEquals(2, QuickBuildWindowLayout.CONTROL_ICON_INSET);
        assertEquals(12, QuickBuildWindowLayout.CONTROL_ICON_SIZE);
        assertEquals(2, QuickBuildWindowLayout.SHAPE_SELECTED_INSET);
        assertEquals(1773, QuickBuildWindowLayout.defaultX(1920));
        assertEquals(84, QuickBuildWindowLayout.defaultY(52));
    }

    @Test
    void modeHitTestingUsesHalfOpenBoundsAndIgnoresTheGap() {
        QuickBuildWindowLayout.Geometry g =
                QuickBuildWindowLayout.geometry(100, 50, false);

        assertEquals(QuickBuildUiMode.BUILD, g.modeAt(106, 70));
        assertEquals(QuickBuildUiMode.BUILD, g.modeAt(169.999D, 83.999D));
        assertNull(g.modeAt(170, 75));
        assertNull(g.modeAt(172.999D, 75));
        assertEquals(QuickBuildUiMode.DESTROY, g.modeAt(173, 70));
        assertEquals(QuickBuildUiMode.DESTROY, g.modeAt(236.999D, 83.999D));
        assertNull(g.modeAt(237, 75));
        assertNull(g.modeAt(200, 84));
        assertEquals(g.buildMode, g.modeArea(QuickBuildUiMode.SMART_FILL));
    }

    @Test
    void convenienceToolsUseTheSameTwoColumnGridAsShapes() {
        QuickBuildWindowLayout.Geometry g =
                QuickBuildWindowLayout.geometry(100, 50, true);

        assertEquals(g.shapeX(0), g.convenienceToolX(0));
        assertEquals(g.shapeX(1), g.convenienceToolX(1));
        assertEquals(g.shapeX(2), g.convenienceToolX(2));
        assertEquals(g.shapeY(0), g.convenienceToolY(0));
        assertEquals(g.shapeY(1), g.convenienceToolY(1));
        assertEquals(g.shapeY(2), g.convenienceToolY(2));
    }

    @Test
    void tooltipStaysNearPointerInVirtualViewport() {
        assertEquals(new UiRect(108, 88, 80, 30),
                QuickBuildWindowLayout.tooltipBounds(240, 160, 100, 80, 80, 30));
    }

    @Test
    void tooltipFlipsAndClampsAtVirtualViewportEdges() {
        assertEquals(new UiRect(132, 102, 80, 30),
                QuickBuildWindowLayout.tooltipBounds(240, 160, 220, 140, 80, 30));
        assertEquals(new UiRect(4, 4, 300, 200),
                QuickBuildWindowLayout.tooltipBounds(240, 160, 2, 2, 300, 200));
    }
}
