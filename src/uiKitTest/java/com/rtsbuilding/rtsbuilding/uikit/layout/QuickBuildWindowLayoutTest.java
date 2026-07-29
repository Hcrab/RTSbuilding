package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals(332, g.windowH);
        assertEquals(new UiRect(108, 75, 79, 18), g.buildMode);
        assertEquals(new UiRect(108, 314, 162, 4), g.progress);
        assertEquals(118, g.chainLabelY);
        assertEquals(132, g.chainSliderY);
        assertEquals(322, g.statusTextY);
        assertEquals(318, g.statusItemY);
        assertEquals(244, g.chainValueX(50));
        assertEquals(208, g.missingTextX(200));
        assertEquals(224, g.missingIconX(208, 12));
    }

    @Test
    void buildAndDestroyReserveTheSameFiveRowBody() {
        assertEquals(0, QuickBuildWindowLayout.windowHeight(true)
                - QuickBuildWindowLayout.windowHeight(false));
    }

    @Test
    void sliderAndDefaultDockGeometryUseNamedKitRules() {
        assertEquals(50, QuickBuildWindowLayout.chainSliderWidth(178));
        assertEquals(18, QuickBuildWindowLayout.CHAIN_SLIDER_H);
        assertEquals(2, QuickBuildWindowLayout.CHAIN_VALUE_Y_OFFSET);
        assertEquals(2, QuickBuildWindowLayout.CONTROL_ICON_INSET);
        assertEquals(16, QuickBuildWindowLayout.CONTROL_ICON_SIZE);
        assertEquals(2, QuickBuildWindowLayout.SHAPE_SELECTED_INSET);
        assertEquals(1738, QuickBuildWindowLayout.defaultX(1920));
        assertEquals(92, QuickBuildWindowLayout.defaultY(52));
    }

    @Test
    void 模式命中使用半开边界且按钮间隙不误触() {
        QuickBuildWindowLayout.Geometry g =
                QuickBuildWindowLayout.geometry(100, 50, false);

        assertEquals(QuickBuildUiMode.BUILD, g.modeAt(108, 75));
        assertEquals(QuickBuildUiMode.BUILD, g.modeAt(186.999D, 92.999D));
        assertNull(g.modeAt(187, 80));
        assertNull(g.modeAt(190.999D, 80));
        assertEquals(QuickBuildUiMode.DESTROY, g.modeAt(191, 75));
        assertNull(g.modeAt(270, 80));
        assertNull(g.modeAt(200, 93));
    }
}
