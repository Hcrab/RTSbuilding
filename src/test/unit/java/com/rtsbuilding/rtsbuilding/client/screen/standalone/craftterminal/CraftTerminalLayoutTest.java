package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftTerminalLayoutTest {
    @Test
    void rowCountClampsToSupportedRange() {
        CraftTerminalLayout layout = new CraftTerminalLayout(1);
        assertEquals(2, layout.rows());
        layout.setRows(9);
        assertEquals(6, layout.rows());
    }

    @Test
    void reducingRowsShrinksOnlyFromTop() {
        CraftTerminalLayout layout = new CraftTerminalLayout(6);
        assertEquals(0, layout.visualTop());
        assertEquals(CraftTerminalLayout.STORAGE_BOTTOM,
                layout.storageGridY() + layout.rows() * CraftTerminalLayout.SLOT_SIZE);

        layout.setRows(3);
        assertEquals(54, layout.visualTop());
        assertEquals(250, layout.visibleHeight());
        assertEquals(CraftTerminalLayout.STORAGE_BOTTOM,
                layout.storageGridY() + layout.rows() * CraftTerminalLayout.SLOT_SIZE,
                "合成格的固定槽位坐标不能随储存显示行数漂移");
    }
}
