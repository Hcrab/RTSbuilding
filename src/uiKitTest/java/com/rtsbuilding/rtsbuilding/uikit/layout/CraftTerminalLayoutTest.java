package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftTerminalLayoutTest {
    @Test
    void 可变储存行只移动顶部且不推挤合成区() {
        CraftTerminalLayout.Geometry full = CraftTerminalLayout.geometry(6);
        CraftTerminalLayout.Geometry compact = CraftTerminalLayout.geometry(2);

        assertEquals(0, full.visualTop);
        assertEquals(72, compact.visualTop);
        assertEquals(127, full.storageGrid.bottom());
        assertEquals(127, compact.storageGrid.bottom());
        assertEquals(129, full.craftingPanel.getY());
        assertEquals(full.craftingPanel, compact.craftingPanel);
        assertEquals(full.inventoryPanel, compact.inventoryPanel);
    }

    @Test
    void 储存格使用半开边界且精确保持九列() {
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(6);
        assertEquals(0, layout.storageCellAt(7, 19));
        assertEquals(8, layout.storageCellAt(168.99D, 19));
        assertEquals(9, layout.storageCellAt(7, 37));
        assertEquals(-1, layout.storageCellAt(169, 19));
        assertEquals(-1, layout.storageCellAt(7, 127));
    }

    @Test
    void 所有右侧图标动作由同一布局解析() {
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(6);
        assertEquals(CraftTerminalUiAction.CYCLE_ROWS, layout.actionAt(200, 5));
        assertEquals(CraftTerminalUiAction.SORT, layout.actionAt(200, 22));
        assertNull(layout.actionAt(200, 45));
        assertEquals(CraftTerminalUiAction.SORT_DIRECTION, layout.actionAt(200, 48));
        assertEquals(CraftTerminalUiAction.CLEAR_TO_STORAGE, layout.actionAt(200, 194));
        assertNull(layout.actionAt(200, 176));
        assertNull(layout.actionAt(200, 240));
        assertNull(layout.actionAt(194, 176));
    }

    @Test
    void 菜单槽位位于共享槽框的内沿() {
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(6);
        assertEquals(24, layout.craftingGridFrame.getX());
        assertEquals(143, layout.craftingGridFrame.getY());
        assertEquals(221, CraftTerminalLayout.INVENTORY_Y - 1);
        assertEquals(279, CraftTerminalLayout.HOTBAR_Y - 1);
    }

    @Test
    void 贡献者纹理切片保持原像素尺寸() {
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(4);
        assertEquals(195, CraftTerminalLayout.WIDTH);
        assertEquals(221, CraftTerminalLayout.VISIBLE_WIDTH);
        assertEquals(CraftTerminalLayout.VISIBLE_WIDTH,
                layout.sortControls.direction.right());
        assertEquals(304, CraftTerminalLayout.IMAGE_HEIGHT);
        for (CraftTerminalLayout.TextureSlice slice : layout.skinSlices()) {
            assertEquals(slice.source.getWidth(), slice.target.getWidth());
            assertEquals(slice.source.getHeight(), slice.target.getHeight());
        }
    }

    @Test
    void 图二滑块保持十乘十五并在轨道内移动() {
        CraftTerminalLayout.Geometry full = CraftTerminalLayout.geometry(6);
        CraftTerminalLayout.Geometry compact = CraftTerminalLayout.geometry(2);

        assertEquals(new UiRect(176, 19, 10, 15), full.scrollbarHandle(0.0D));
        assertEquals(new UiRect(176, 66, 10, 15), full.scrollbarHandle(0.5D));
        assertEquals(new UiRect(176, 112, 10, 15), full.scrollbarHandle(1.0D));
        assertEquals(new UiRect(176, 91, 10, 15), compact.scrollbarHandle(-1.0D));
        assertEquals(new UiRect(176, 112, 10, 15), compact.scrollbarHandle(2.0D));

        CraftTerminalLayout.TextureSlice slice = full.scrollbarHandleSlice(0.25D);
        assertEquals(new UiRect(197, 20, 10, 15), slice.source);
        assertEquals(new UiRect(176, 42, 10, 15), slice.target);
        assertTrue(full.scrollbar.contains(slice.target));

        double grabOffset = 6.0D;
        assertEquals(0.0D, full.scrollbarFractionForPointer(19 + grabOffset, grabOffset));
        assertEquals(1.0D, full.scrollbarFractionForPointer(112 + grabOffset, grabOffset));
        assertEquals(0.0D, compact.scrollbarFractionForPointer(-100, grabOffset));
        assertEquals(1.0D, compact.scrollbarFractionForPointer(1000, grabOffset));
    }
}
