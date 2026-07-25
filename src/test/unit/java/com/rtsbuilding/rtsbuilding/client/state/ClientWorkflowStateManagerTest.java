package com.rtsbuilding.rtsbuilding.client.state;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientWorkflowStateManagerTest {
    @Test
    void batchReplacesOldSlotsAndKeepsDestroyLookup() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        manager.apply(progress(0, 2, RtsWorkflowType.PLACE_BATCH));
        manager.apply(progress(1, 2, RtsWorkflowType.AREA_DESTROY));
        assertTrue(manager.hasActiveWorkflow());
        assertEquals(RtsWorkflowType.AREA_DESTROY,
                manager.activeDestroyWorkflow().type());

        manager.applyBatch(new S2CRtsWorkflowProgressBatchPayload(List.of(
                progress(0, 1, RtsWorkflowType.PLACE_BATCH))));

        assertEquals(1, manager.activeCount());
        assertEquals(1, manager.activeWorkflows().size());
        assertNull(manager.activeDestroyWorkflow());
        assertNull(manager.rawStatuses()[1]);
    }

    @Test
    void idlePayloadAndDisconnectClearTransientState() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        manager.apply(progress(0, 1, RtsWorkflowType.ULTIMINE));
        manager.setPendingJobs(true);

        manager.apply(S2CRtsWorkflowProgressPayload.idle());
        assertFalse(manager.hasActiveWorkflow());
        assertTrue(manager.hasPendingJobs());

        manager.clear();
        assertFalse(manager.hasPendingJobs());
    }

    private static S2CRtsWorkflowProgressPayload progress(
            int index,
            int count,
            RtsWorkflowType type) {
        return new S2CRtsWorkflowProgressPayload(
                (byte) index,
                (byte) count,
                (byte) type.ordinal(),
                (byte) RtsWorkflowPriority.NORMAL.ordinal(),
                10,
                3,
                0,
                List.of(),
                "",
                (byte) 0,
                (byte) 0,
                (byte) 0,
                42 + index);
    }
}
