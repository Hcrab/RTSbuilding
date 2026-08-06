package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftTerminalSortControlsLayoutTest {
    @Test
    void 两个二十四像素按钮纵向排列且命中不重叠() {
        CraftTerminalSortControlsLayout.Geometry layout =
                CraftTerminalSortControlsLayout.resolve(0);

        assertEquals(CraftTerminalSortControlsLayout.BUTTON_WIDTH,
                layout.field.getWidth());
        assertEquals(CraftTerminalSortControlsLayout.BUTTON_HEIGHT,
                layout.field.getHeight());
        assertEquals(layout.field.bottom()
                        + CraftTerminalSortControlsLayout.BUTTON_GAP,
                layout.direction.getY());
        assertTrue(layout.field.bottom() <= layout.direction.getY());
        assertEquals(CraftTerminalUiAction.SORT, layout.actionAt(200, 22));
        assertNull(layout.actionAt(200, 45));
        assertEquals(CraftTerminalUiAction.SORT_DIRECTION,
                layout.actionAt(200, 48));
    }

    @Test
    void 紧凑终端只整体下移排序控件() {
        CraftTerminalSortControlsLayout.Geometry full =
                CraftTerminalSortControlsLayout.resolve(0);
        CraftTerminalSortControlsLayout.Geometry compact =
                CraftTerminalSortControlsLayout.resolve(72);

        assertEquals(full.field.getX(), compact.field.getX());
        assertEquals(full.field.getY() + 72, compact.field.getY());
        assertEquals(full.direction.getY() + 72, compact.direction.getY());
    }
}
