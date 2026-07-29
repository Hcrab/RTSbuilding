package com.rtsbuilding.rtsbuilding.server.diagnostic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsDiagnosticReasonTest {

    @Test
    void classifiesStableReasonsFromUnifiedValidationStages() {
        assertEquals(RtsDiagnosticReason.FEATURE_LOCKED,
                RtsDiagnosticReason.classify(
                        "ProgressionGatePipe", "Feature not unlocked: RANGE_DESTROY", false, false));
        assertEquals(RtsDiagnosticReason.STORAGE_SESSION_MISSING,
                RtsDiagnosticReason.classify(
                        "SessionValidatePipe", "No storage session found for player", false, false));
        assertEquals(RtsDiagnosticReason.STORAGE_SESSION_MISSING,
                RtsDiagnosticReason.classify(
                        "ToolBorrowPipe", "No session in context", false, false));
        assertEquals(RtsDiagnosticReason.WORKFLOW_QUEUE_FULL,
                RtsDiagnosticReason.classify(
                        "WorkflowStartPipe", "Workflow queue full (8/8)", false, false));
        assertEquals(RtsDiagnosticReason.CLAIM_DENIED,
                RtsDiagnosticReason.classify(
                        "MiningExecutePipe", "Claim protection denied block break at 1, 2, 3", false, false));
    }

    @Test
    void distinguishesIntentionalSkipFromFailureAndException() {
        assertEquals(RtsDiagnosticReason.PLACED_BLOCK_RECOVERED,
                RtsDiagnosticReason.classify(
                        "MiningExecutePipe", "Placed block recovered, no mining needed", true, false));
        assertEquals(RtsDiagnosticReason.PIPELINE_EARLY_EXIT,
                RtsDiagnosticReason.classify("AnyPipe", "Already handled", true, false));
        assertEquals(RtsDiagnosticReason.PIPE_EXCEPTION,
                RtsDiagnosticReason.classify("AnyPipe", "boom", false, true));
        assertEquals(RtsDiagnosticReason.PIPELINE_REJECTED,
                RtsDiagnosticReason.classify("AnyPipe", "Unknown validation", false, false));
    }
}
