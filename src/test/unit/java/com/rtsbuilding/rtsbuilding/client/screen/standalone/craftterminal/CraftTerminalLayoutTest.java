package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CraftTerminalLayoutTest {
    @Test
    void rowCountClampsToSupportedRange() {
        assertEquals(2, CraftTerminalLayout.geometry(1).rows);
        assertEquals(6, CraftTerminalLayout.geometry(9).rows);
    }

    @Test
    void reducingRowsShrinksOnlyFromTop() {
        CraftTerminalLayout.Geometry full = CraftTerminalLayout.geometry(6);
        assertEquals(0, full.visualTop);
        assertEquals(127, full.storageGrid.bottom());

        CraftTerminalLayout.Geometry compact = CraftTerminalLayout.geometry(3);
        assertEquals(54, compact.visualTop);
        assertEquals(249, compact.visibleHeight());
        assertEquals(127, compact.storageGrid.bottom(),
                "固定合成槽不能随储存显示行数漂移");
    }
}
