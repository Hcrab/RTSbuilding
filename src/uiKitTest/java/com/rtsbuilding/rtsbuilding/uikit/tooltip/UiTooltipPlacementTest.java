package com.rtsbuilding.rtsbuilding.uikit.tooltip;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiTooltipPlacementTest {
    private static final UiRect SCREEN = new UiRect(0, 0, 200, 120);

    @Test
    void 优先方向有空间时保持方向() {
        UiTooltipPlacement.Result result = UiTooltipPlacement.place(
                SCREEN, new UiRect(20, 20, 10, 10), 60, 30, 4, UiTooltipPlacement.Direction.RIGHT);
        assertEquals(UiTooltipPlacement.Direction.RIGHT, result.getDirection());
        assertFalse(result.isClamped());
    }

    @Test
    void 右侧无空间时翻到左侧() {
        UiTooltipPlacement.Result result = UiTooltipPlacement.place(
                SCREEN, new UiRect(180, 20, 10, 10), 60, 30, 4, UiTooltipPlacement.Direction.RIGHT);
        assertEquals(UiTooltipPlacement.Direction.LEFT, result.getDirection());
        assertFalse(result.isClamped());
    }

    @Test
    void 所有方向都不够时钳制屏内() {
        UiTooltipPlacement.Result result = UiTooltipPlacement.place(
                SCREEN, new UiRect(90, 55, 20, 10), 300, 200, 4, UiTooltipPlacement.Direction.BELOW);
        assertTrue(result.isClamped());
        assertTrue(SCREEN.contains(result.getBounds()));
    }

    @Test
    void 下方无空间时优先翻到上方() {
        UiTooltipPlacement.Result result = UiTooltipPlacement.place(
                SCREEN, new UiRect(40, 100, 10, 10), 60, 30, 4, UiTooltipPlacement.Direction.BELOW);
        assertEquals(UiTooltipPlacement.Direction.ABOVE, result.getDirection());
    }

    @Test
    void 零尺寸Tooltip立即失败() {
        assertThrows(IllegalArgumentException.class,
                () -> UiTooltipPlacement.place(SCREEN, new UiRect(0, 0, 1, 1),
                        0, 10, 0, UiTooltipPlacement.Direction.RIGHT));
    }
}
