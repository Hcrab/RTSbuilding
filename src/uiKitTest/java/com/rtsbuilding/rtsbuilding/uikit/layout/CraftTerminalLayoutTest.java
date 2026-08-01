package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        assertEquals(CraftTerminalUiAction.CLEAR_TO_STORAGE, layout.actionAt(200, 176));
        assertEquals(CraftTerminalUiAction.DEPOSIT_HOTBAR, layout.actionAt(200, 240));
        assertNull(layout.actionAt(194, 176));
    }

    @Test
    void 菜单槽位位于共享槽框的内沿() {
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(6);
        assertEquals(CraftTerminalLayout.CRAFT_GRID_X - 1,
                layout.craftingGridFrame.getX());
        assertEquals(CraftTerminalLayout.CRAFT_GRID_Y - 1,
                layout.craftingGridFrame.getY());
        assertEquals(221, CraftTerminalLayout.INVENTORY_Y - 1);
        assertEquals(278, CraftTerminalLayout.HOTBAR_Y - 1);
    }
}
