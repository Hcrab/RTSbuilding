package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsMainlineLayoutTest {
    @Test
    void baselineBottomPanelMatchesProductionGeometry() {
        RtsMainlineLayout.BottomPanel panel = RtsMainlineLayout.bottomPanel(1280, 720, 110);
        assertEquals(607, panel.panelY);
        assertEquals(113, panel.panelH);
        assertEquals(66, panel.categoryX);
        assertEquals(200, panel.storageX);
        assertEquals(1146, panel.craftPanelX);
        assertEquals(940, panel.mainStorageW);
        assertEquals(2, panel.storageRows);
    }

    @Test
    void topBarKeepsGearRightAligned() {
        RtsMainlineLayout.TopButtons buttons = RtsMainlineLayout.topButtons(
                1280, true, false, true, false);
        assertEquals(8, buttons.x(0));
        assertEquals(1240, buttons.x(10));
        assertTrue(buttons.isPresent(4));
        assertTrue(buttons.isPresent(7));
    }

    @Test
    void topStatusOnlyShowsContextHintWhenBothTextsFit() {
        RtsMainlineLayout.TopStatus status = RtsMainlineLayout.topStatus(800);
        assertEquals(8, status.x);
        assertEquals(784, status.width);
        assertEquals(692, RtsMainlineLayout.contextualHintX(status, 300, 100, 12));
        assertEquals(-1, RtsMainlineLayout.contextualHintX(status, 690, 100, 12));
    }

    @Test
    void capturedLegacyHeightKeepsSubregionsAlignedWithoutProductionClamp() {
        RtsMainlineLayout.BottomPanel panel = RtsMainlineLayout.bottomPanelAtHeight(960, 540, 88);
        assertEquals(452, panel.panelY);
        assertEquals(66, panel.categoryX);
        assertEquals(200, panel.storageX);
        assertEquals(826, panel.craftPanelX);
        assertEquals(742, panel.pagerX);
    }
}
