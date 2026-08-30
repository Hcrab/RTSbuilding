package com.rtsbuilding.rtsbuilding.uikit.theme;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class UiControlVisualStyleTest {
    @Test
    void 顶栏状态只插值边框且按下立即覆盖动画() {
        UiColor idle = UiControlVisualStyle.animatedBorder(
                UiControlRole.MODE, 0.0D, 0.0D, false);
        UiColor hoverHalf = UiControlVisualStyle.animatedBorder(
                UiControlRole.MODE, 0.5D, 0.0D, false);
        UiColor selected = UiControlVisualStyle.animatedBorder(
                UiControlRole.MODE, 0.0D, 1.0D, false);
        UiColor pressed = UiControlVisualStyle.animatedBorder(
                UiControlRole.MODE, 0.0D, 0.0D, true);

        assertEquals(RtsMainlineTheme.BUTTON_BORDER_LIGHT, idle);
        assertNotEquals(idle, hoverHalf);
        assertEquals(RtsMainlineTheme.CONTROL_SELECTED_BORDER_LIGHT, selected);
        assertEquals(RtsMainlineTheme.CONTROL_PRESSED_BORDER_LIGHT, pressed);
    }
}
