package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 详情窗口的实际正文边界在小逻辑视窗和旧大窗口下都保持可访问。 */
final class RtsWorkflowDetailLayoutTest {
    @Test
    void defaultWindowFitsA320By240LogicalViewport() {
        int width = 320 - 8;
        int height = 240 - 8;
        RtsWorkflowDetailLayout.Geometry layout =
                RtsWorkflowDetailLayout.content(5, 25, width - 2, height - 21, 8, 22);
        assertTrue(layout.viewport.getX() >= 5);
        assertTrue(layout.viewport.getY() >= 25);
        assertTrue(layout.viewport.right() <= 5 + width - 2);
        assertTrue(layout.viewport.bottom() <= 25 + height - 21);
        assertTrue(layout.textWidth > 0);
    }

    @Test
    void savedLargeWindowAndSmallerViewportUseAdaptiveMinimums() {
        assertTrue(RtsWorkflowDetailLayout.adaptiveMinimum(280, 320) <= 312);
        assertTrue(RtsWorkflowDetailLayout.adaptiveMinimum(170, 240) <= 232);
        assertTrue(RtsWorkflowDetailLayout.adaptiveMinimum(280, 200) <= 192);
        assertTrue(RtsWorkflowDetailLayout.adaptiveMinimum(170, 130) <= 122);

        RtsWorkflowDetailLayout.Geometry layout =
                RtsWorkflowDetailLayout.content(4, 20, 192 - 2, 122 - 21, 8, 22);
        assertTrue(layout.viewport.right() <= 4 + 190);
        assertTrue(layout.viewport.bottom() <= 20 + 101);
        assertTrue(layout.viewport.getHeight() > 0);
    }
}
