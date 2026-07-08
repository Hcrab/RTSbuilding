package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowPanelVisibilityGateTest {
    @Test
    void hidesUntilContentPersistsPastDelay() {
        WorkflowPanelVisibilityGate gate = new WorkflowPanelVisibilityGate(1_000L);

        assertFalse(gate.canShow(true, 10_000L));
        assertFalse(gate.canShow(true, 10_999L));
        assertTrue(gate.canShow(true, 11_000L));
    }

    @Test
    void shortContentBurstDoesNotOpenPanel() {
        WorkflowPanelVisibilityGate gate = new WorkflowPanelVisibilityGate(1_000L);

        assertFalse(gate.canShow(true, 20_000L));
        assertFalse(gate.canShow(false, 20_400L));
        assertFalse(gate.canShow(true, 20_500L));
        assertFalse(gate.canShow(true, 21_499L));
        assertTrue(gate.canShow(true, 21_500L));
    }

    @Test
    void hidesImmediatelyWhenContentDisappearsAfterShowing() {
        WorkflowPanelVisibilityGate gate = new WorkflowPanelVisibilityGate(1_000L);

        assertFalse(gate.canShow(true, 30_000L));
        assertTrue(gate.canShow(true, 31_000L));
        assertFalse(gate.canShow(false, 31_001L));
    }
}
