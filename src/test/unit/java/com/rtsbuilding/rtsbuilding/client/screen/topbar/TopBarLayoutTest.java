package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopBarLayoutTest {
    @Test
    void fullLayoutKeepsExistingPixelPositions() {
        var layout = TopBarLayout.buttons(854, 32, 32, 5,
                true, true, true, true);
        assertEquals(8, layout.x(TopBarTypes.TopBarButtonId.INTERACT));
        assertEquals(119, layout.x(TopBarTypes.TopBarButtonId.ROTATE));
        assertEquals(164, layout.x(TopBarTypes.TopBarButtonId.QUICK_BUILD));
        assertEquals(349, layout.x(TopBarTypes.TopBarButtonId.DEVELOPER));
        assertEquals(814, layout.x(TopBarTypes.TopBarButtonId.GEAR));
        assertEquals(4, TopBarLayout.BUTTON_Y);

        var status = TopBarLayout.status(854);
        assertEquals(8, status.x());
        assertEquals(838, status.width());
        assertEquals(33, status.row1Y());
        assertEquals(44, status.row2Y());
    }

    @Test
    void highScaleWidthKeepsGearSeparatedAndInsideWhenSpaceAllows() {
        int width = 480;
        int iconWidth = 32;
        var layout = TopBarLayout.buttons(width, 32, iconWidth, 5,
                true, true, true, true);
        int developerRight = layout.x(TopBarTypes.TopBarButtonId.DEVELOPER) + iconWidth;
        assertTrue(layout.x(TopBarTypes.TopBarButtonId.GEAR) >= developerRight + 5);
        assertTrue(layout.x(TopBarTypes.TopBarButtonId.GEAR) + iconWidth <= width);
    }

    @Test
    void contextualHintUsesRightEdgeWhenThereIsEnoughSpace() {
        var status = TopBarLayout.status(854);
        assertEquals(666, TopBarLayout.contextualHintX(status, 420, 180, 12));
    }

    @Test
    void contextualHintIsHiddenInsteadOfOverlappingStatusText() {
        var status = TopBarLayout.status(480);
        assertEquals(-1, TopBarLayout.contextualHintX(status, 330, 180, 12));
    }
}
