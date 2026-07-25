package com.rtsbuilding.rtsbuilding.client.screen.developer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsDeveloperTaskLayoutTest {
    @Test
    void normalScreenKeepsOriginalSingleColumnPixels() {
        var layout = RtsDeveloperTaskLayout.resolve(854, 480, 4);
        assertEquals(297, layout.taskButtons().get(0).x());
        assertEquals(48, layout.taskButtons().get(0).y());
        assertEquals(260, layout.taskButtons().get(0).width());
        assertEquals(126, layout.taskButtons().get(layout.taskButtons().size() - 1).y());
        assertEquals(448, layout.backButton().y());
    }

    @Test
    void narrowHighScaleScreenKeepsEveryButtonInsideBounds() {
        int width = 200;
        int height = 160;
        var layout = RtsDeveloperTaskLayout.resolve(width, height, 4);
        for (var button : layout.taskButtons()) {
            assertTrue(button.x() >= 0 && button.right() <= width);
            assertTrue(button.y() >= 0 && button.bottom() <= height);
            assertTrue(button.bottom() <= layout.backButton().y(),
                    "任务按钮不能覆盖返回按钮");
        }
        assertTrue(layout.backButton().x() >= 0 && layout.backButton().right() <= width);
        assertTrue(layout.backButton().bottom() <= height);
    }
}
