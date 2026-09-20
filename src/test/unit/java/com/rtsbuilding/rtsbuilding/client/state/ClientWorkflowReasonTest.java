package com.rtsbuilding.rtsbuilding.client.state;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端线程安全状态容器必须保留原因、缺料和详情，而不是只保留进度数字。 */
class ClientWorkflowReasonTest {
    @Test
    void unknownReasonAndStructuredDetailSurviveStateProjection() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        manager.apply(progress(0, 256, 9999, List.of("minecraft:stone"), "missing:stone"));

        assertEquals(256, manager.activeCount());
        assertEquals(RtsOperationReason.UNKNOWN, manager.status(0).reason());
        assertEquals(List.of("minecraft:stone"), manager.status(0).missingItems());
        assertEquals("missing:stone", manager.status(0).detailMessage());
    }

    @Test
    void pausedReasonAndProtocolIndexAboveUiWindowRemainDistinguishable() {
        ClientWorkflowStateManager manager = new ClientWorkflowStateManager();
        manager.apply(progress(0, 256, RtsOperationReason.MANUAL_PAUSED.wireId(),
                List.of(), "paused_by_player"));
        assertEquals(RtsOperationReason.MANUAL_PAUSED, manager.status(0).reason());
        assertTrue(manager.status(0).paused());

        manager.apply(progress(255, 256, RtsOperationReason.REPLACED.wireId(),
                List.of(), "replaced_by_newer_workflow"));
        assertEquals(256, manager.activeCount());
        assertEquals(RtsWorkflowType.PLACE_BATCH, manager.status(0).type());
        assertEquals(RtsWorkflowType.PLACE_BATCH, manager.status(255).type(),
                "协议索引必须保留，UI 只负责滚动显示可见行");
    }

    private static S2CRtsWorkflowProgressPayload progress(
            int index, int count, int reason, List<String> missing, String detail) {
        return new S2CRtsWorkflowProgressPayload(
                index, count, (byte) RtsWorkflowType.PLACE_BATCH.wireId(),
                (byte) RtsWorkflowPriority.NORMAL.ordinal(), 100, 10, 0,
                missing, detail, (byte) 0,
                (byte) (reason == RtsOperationReason.MANUAL_PAUSED.wireId() ? 1 : 0),
                (byte) 0, 17, reason);
    }
}
