package com.rtsbuilding.rtsbuilding.client.state;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 协议容量与 UI 可见行解耦：高索引不能被客户端缓存静默丢弃。 */
class ClientWorkflowStateManagerCapacityTest {
    @Test
    void keepsBoundaryIndexesAndUsesStableEntryIds() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        int[] indexes = {7, 8, 127, 128, 1023};
        for (int index : indexes) {
            manager.apply(progress(index, 1024, 10_000 + index));
            assertEquals(10_000 + index, manager.status(index).entryId());
        }
        assertEquals(1024, manager.activeCount());
        assertEquals(5, manager.activeWorkflows().size());
    }

    @Test
    void batchReplacementAndIdleClearTheFullProtocolBudget() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        manager.applyBatch(new S2CRtsWorkflowProgressBatchPayload(List.of(
                progress(0, 1024, 1),
                progress(1023, 1024, 2))));
        assertEquals(2, manager.activeWorkflows().size());

        manager.applyBatch(new S2CRtsWorkflowProgressBatchPayload(List.of(
                progress(0, 9, 3),
                progress(8, 9, 4))));
        assertEquals(9, manager.activeCount());
        assertEquals(2, manager.activeWorkflows().size());
        assertNull(manager.rawStatuses()[1023]);

        manager.apply(S2CRtsWorkflowProgressPayload.idle());
        assertEquals(0, manager.activeCount());
        assertEquals(0, manager.activeWorkflows().size());
        assertNull(manager.rawStatuses()[8]);
    }

    private static S2CRtsWorkflowProgressPayload progress(
            int index, int count, int entryId) {
        return new S2CRtsWorkflowProgressPayload(
                index,
                count,
                (byte) RtsWorkflowType.PLACE_BATCH.wireId(),
                (byte) RtsWorkflowPriority.NORMAL.ordinal(),
                10,
                3,
                0,
                List.of(),
                "",
                (byte) 0,
                (byte) 0,
                (byte) 0,
                entryId,
                0);
    }
}
