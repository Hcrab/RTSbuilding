package com.rtsbuilding.rtsbuilding.uicore.workflow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorkflowUiReducerTest {
    @Test
    void activeRowsPauseProtectAndDelete() {
        WorkflowUiState state = state();
        assertEquals(WorkflowUiTransition.Command.TOGGLE_PAUSED,
                WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                        WorkflowUiAction.Type.TOGGLE_PAUSED, 1)).command);
        assertEquals(1, WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                WorkflowUiAction.Type.DELETE, 1)).state.rows.size());
    }

    @Test
    void suspendedResumeUsesTypeSpecificScan() {
        WorkflowUiState state = state();
        assertEquals(WorkflowUiTransition.Command.SCAN_RESUME_BLUEPRINT,
                WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                        WorkflowUiAction.Type.RESUME_SUSPENDED, 2)).command);
        assertEquals(WorkflowUiTransition.Command.NONE,
                WorkflowUiReducer.apply(state, WorkflowUiAction.of(
                        WorkflowUiAction.Type.TOGGLE_PAUSED, 2)).command);
    }

    @Test
    void progressMatchesProductionCompletedOnlyFormula() {
        WorkflowUiRow row = new WorkflowUiRow(3, "quick_build", "Quick Build", "30/100",
                20, 100, 10, 70, false, false, false, false);
        assertEquals(0.2D, row.progress());
    }

    private static WorkflowUiState state() {
        return new WorkflowUiState(true, false, Arrays.asList(
                new WorkflowUiRow(1, "quick_build", "Quick Build", "24/80",
                        24, 80, 0, 56, false, false, false, false),
                new WorkflowUiRow(2, "blueprint_build", "Blueprint", "8/120",
                        8, 120, 0, 112, true, false, true, true)));
    }
}
