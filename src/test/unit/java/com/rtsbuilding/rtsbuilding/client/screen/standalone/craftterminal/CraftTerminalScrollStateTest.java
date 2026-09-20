package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CraftTerminalScrollStateTest {
    @Test
    void defaultWindowKeepsExistingBoundarySemantics() {
        assertArrayEquals(new int[]{0, 1},
                CraftTerminalScrollState.requiredPagesForWindow(15, 6, 500));
    }

    @Test
    void smallEffectivePageKeepsEveryPageNeededByOneWindow() {
        assertArrayEquals(new int[]{0, 1, 2, 3},
                CraftTerminalScrollState.requiredPagesForWindow(0, 6, 60, 17));
    }

    @Test
    void smallEffectivePageStopsAtLastPartialPage() {
        assertArrayEquals(new int[]{0, 1},
                CraftTerminalScrollState.requiredPagesForWindow(0, 6, 18, 17));
    }
}
