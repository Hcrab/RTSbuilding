package com.rtsbuilding.rtsbuilding.uicore.funnel;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class FunnelUiReducerTest {
    @Test
    void toggleAndHoverStayInsideVisibleSnapshot() {
        FunnelUiState state = new FunnelUiState(true, true, 2000, 1, -1,
                Collections.singletonList(new FunnelUiEntry(0, "minecraft:chest", "Chest", 64)));
        assertFalse(FunnelUiReducer.apply(state, FunnelUiAction.toggle()).panelVisible);
        assertEquals(0, FunnelUiReducer.apply(state, FunnelUiAction.hover(0)).hoveredSourceIndex);
        assertEquals(-1, FunnelUiReducer.apply(state, FunnelUiAction.hover(20)).hoveredSourceIndex);
    }
}
