package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CraftTerminalScrollStateTest {
    @Test
    void sixVisibleRowsStayInsideFirstPageAtRowFourteen() {
        assertArrayEquals(new int[]{0},
                CraftTerminalScrollState.requiredPagesForWindow(14, 6, 500));
    }

    @Test
    void sixVisibleRowsRequestBothPagesAtBoundary() {
        assertArrayEquals(new int[]{0, 1},
                CraftTerminalScrollState.requiredPagesForWindow(15, 6, 500),
                "第 15 行开始的六行视窗会跨过 180 项边界，不能只渲染当前页");
    }

    @Test
    void lastPartialPageDoesNotRequestPastTotalEntries() {
        assertArrayEquals(new int[]{1},
                CraftTerminalScrollState.requiredPagesForWindow(20, 6, 181));
    }
}
