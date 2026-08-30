package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CraftFeedbackStyleTest {
    @Test
    void 淡出透明度保持旧上下限() {
        assertEquals(255, CraftFeedbackStyle.alpha(1.5D));
        assertEquals(127, CraftFeedbackStyle.alpha(0.5D));
        assertEquals(84, CraftFeedbackStyle.alpha(0.0D));
        assertEquals(0x7F18222C,
                CraftFeedbackStyle.faded(CraftFeedbackStyle.PANEL, 127).toArgb());
        assertThrows(IllegalArgumentException.class,
                () -> CraftFeedbackStyle.alpha(Double.NaN));
    }
}
