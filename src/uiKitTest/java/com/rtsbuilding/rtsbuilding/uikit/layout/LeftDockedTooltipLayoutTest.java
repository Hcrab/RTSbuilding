package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class LeftDockedTooltipLayoutTest {
    @Test
    void 常规底栏从左边缘和顶部向上偏移() {
        LeftDockedTooltipLayout.Geometry geometry =
                LeftDockedTooltipLayout.resolve(20, 900, 52);

        assertEquals(28, geometry.anchorX());
        assertEquals(876, geometry.anchorY());
        assertEquals(38, geometry.detailX());
        assertEquals(894, geometry.detailY());
    }

    @Test
    void 矮屏幕不会侵入顶部保留区() {
        LeftDockedTooltipLayout.Geometry geometry =
                LeftDockedTooltipLayout.resolve(0, 70, 52);

        assertEquals(60, geometry.anchorY());
        assertThrows(IllegalArgumentException.class,
                () -> LeftDockedTooltipLayout.resolve(0, 70, -1));
    }
}
