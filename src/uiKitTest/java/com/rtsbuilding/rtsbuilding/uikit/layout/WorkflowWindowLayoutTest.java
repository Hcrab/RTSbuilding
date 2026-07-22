package com.rtsbuilding.rtsbuilding.uikit.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorkflowWindowLayoutTest {
    @Test
    void columnsAndHeightMatchProduction() {
        assertEquals(154, WorkflowWindowLayout.rowWidth());
        assertEquals(156, WorkflowWindowLayout.protectX(0));
        assertEquals(192, WorkflowWindowLayout.deleteX(0));
        assertEquals(77, WorkflowWindowLayout.totalHeight(20, 2));
    }
}
