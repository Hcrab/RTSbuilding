package com.rtsbuilding.rtsbuilding.uicore.craftterminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CraftTerminalSortFieldTest {
    @Test
    void 终端只在名称和数量之间确定性切换() {
        assertEquals(CraftTerminalSortField.QUANTITY,
                CraftTerminalSortField.NAME.next());
        assertEquals(CraftTerminalSortField.NAME,
                CraftTerminalSortField.QUANTITY.next());
    }
}
