package com.rtsbuilding.rtsbuilding.common.diagnostics;

import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 稳定原因/工作流类型 ID 的纯值回归；未知值绝不能被猜成缺材料。 */
class RtsOperationReasonTest {
    @Test
    void publishedReasonIdsAndWorkflowTypeIdsRemainStable() {
        assertEquals(0, RtsOperationReason.UNKNOWN.wireId());
        assertEquals(1, RtsOperationReason.SUCCESS.wireId());
        assertEquals(3, RtsOperationReason.RESOURCE_MISSING.wireId());
        assertEquals(7, RtsOperationReason.PERMISSION_DENIED.wireId());
        assertEquals(9, RtsOperationReason.MANUAL_PAUSED.wireId());
        assertEquals(11, RtsOperationReason.CANCELLED.wireId());
        assertEquals(12, RtsOperationReason.REPLACED.wireId());

        Set<Integer> reasonIds = Arrays.stream(RtsOperationReason.values())
                .map(RtsOperationReason::wireId).collect(Collectors.toSet());
        assertEquals(RtsOperationReason.values().length, reasonIds.size());
        assertEquals(RtsWorkflowType.PLACE_BATCH, RtsWorkflowType.fromWireId(6));
        assertEquals(RtsWorkflowType.BLUEPRINT_BUILD, RtsWorkflowType.fromWireId(8));
    }

    @Test
    void unknownWireIdsFailClosedWithoutResourceGuess() {
        assertEquals(RtsOperationReason.UNKNOWN, RtsOperationReason.fromWireId(-1));
        assertEquals(RtsOperationReason.UNKNOWN, RtsOperationReason.fromWireId(9999));
        assertNotEquals(RtsOperationReason.RESOURCE_MISSING,
                RtsOperationReason.fromWireId(Integer.MAX_VALUE));
        assertTrue(RtsOperationReason.REPLACED.diagnosticId().equals("replaced"));
    }
}
