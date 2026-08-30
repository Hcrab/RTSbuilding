package com.rtsbuilding.rtsbuilding.client.screen.gear;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemeColorPickerTest {
    @Test
    void indicatorUsesTheExactFinalSelectedColor() {
        ThemeColorPicker picker = new ThemeColorPicker();
        UiColor selected = UiColor.argb(0xFF, 0x34, 0xAD, 0x78);

        picker.setColor(selected);

        assertEquals(selected.toArgb(), picker.indicatorColor());
    }
}
