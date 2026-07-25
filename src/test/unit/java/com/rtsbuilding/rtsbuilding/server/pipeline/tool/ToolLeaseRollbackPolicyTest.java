package com.rtsbuilding.rtsbuilding.server.pipeline.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolLeaseRollbackPolicyTest {

    @Test
    void ultimineAndAreaDestroyCancelTaskWithoutReturningTransferredLeaseTwice() {
        var decision = ToolLeaseRollbackPolicy.decide(true, true, false, true);

        assertTrue(decision.cancelSubmittedTask());
        assertFalse(decision.returnPipelineLease());
    }

    @Test
    void queueModeCancelsSubmittedTaskButNeverReturnsSharedSessionLease() {
        var decision = ToolLeaseRollbackPolicy.decide(true, false, false, false);

        assertTrue(decision.cancelSubmittedTask());
        assertFalse(decision.returnPipelineLease());
    }

    @Test
    void failureBeforeHandoffReturnsPipelineLeaseExactlyOnce() {
        var first = ToolLeaseRollbackPolicy.decide(false, false, false, true);
        var afterReturn = ToolLeaseRollbackPolicy.decide(false, false, true, true);

        assertTrue(first.returnPipelineLease());
        assertFalse(afterReturn.returnPipelineLease());
    }
}
