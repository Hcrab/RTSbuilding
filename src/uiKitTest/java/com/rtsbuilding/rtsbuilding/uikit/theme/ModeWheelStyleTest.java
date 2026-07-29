package com.rtsbuilding.rtsbuilding.uikit.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModeWheelStyleTest {
    @Test
    void 选项状态由同一语义映射提供() {
        assertSame(ModeWheelStyle.OPTION_BORDER_CURRENT,
                ModeWheelStyle.optionBorder(true, 0.0D));
        assertSame(ModeWheelStyle.OPTION_BACKGROUND_IDLE,
                ModeWheelStyle.optionBackground(false, 0.0D));
        assertEquals(ModeWheelStyle.OPTION_BORDER_HOVER,
                ModeWheelStyle.optionBorder(false, 1.0D));
        assertEquals(ModeWheelStyle.OPTION_BACKGROUND_HOVER,
                ModeWheelStyle.optionBackground(true, 1.0D));
    }

    @Test
    void 透明度缩放确定且钳制边界() {
        assertEquals(0x80FFFFFF,
                ModeWheelStyle.multiplyAlpha(new UiColor(0xFFFFFFFF), 0.5D).toArgb());
        assertEquals(0x00FFFFFF,
                ModeWheelStyle.multiplyAlpha(new UiColor(0x80FFFFFF), -1.0D).toArgb());
        assertEquals(0x80FFFFFF,
                ModeWheelStyle.multiplyAlpha(new UiColor(0x80FFFFFF), 2.0D).toArgb());
        assertThrows(IllegalArgumentException.class,
                () -> ModeWheelStyle.multiplyAlpha(null, 1.0D));
    }
}
