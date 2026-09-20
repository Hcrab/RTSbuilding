package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 工作流短状态必须只依赖结构化 reason/lifecycle，不从英文详情猜缺料。 */
@org.junit.jupiter.api.extension.ExtendWith(com.rtsbuilding.rtsbuilding.test.ModTranslationsFixture.class)
class WorkflowUiAdapterTest {
    @Test
    void unknownReasonDoesNotPretendToBeMissingMaterials() {
        RtsWorkflowStatus status = status(
                RtsOperationReason.UNKNOWN, true, false, 10, 1, List.of("minecraft:stone"));

        assertEquals("screen.rtsbuilding.workflow.status.waiting",
                WorkflowUiAdapter.statusKey(status));
        assertEquals("screen.rtsbuilding.workflow.next.waiting",
                WorkflowUiAdapter.nextStepKey(status));
    }

    @Test
    void manualPauseWinsOverSuspensionAndRecoveryDropsStaleReason() {
        RtsWorkflowStatus paused = status(
                RtsOperationReason.MANUAL_PAUSED, true, true, 10, 1, List.of("minecraft:stone"));
        RtsWorkflowStatus recovered = status(
                RtsOperationReason.RESOURCE_MISSING, false, false, 10, 4, List.of());
        RtsWorkflowStatus complete = status(
                RtsOperationReason.SUCCESS, false, false, 4, 4, List.of());
        RtsWorkflowStatus error = status(
                RtsOperationReason.EXECUTION_ERROR, false, false, 4, 1, List.of());

        assertEquals("screen.rtsbuilding.workflow.status.paused",
                WorkflowUiAdapter.statusKey(paused));
        assertEquals("screen.rtsbuilding.workflow.next.resume",
                WorkflowUiAdapter.nextStepKey(paused));
        assertEquals("screen.rtsbuilding.workflow.status.running",
                WorkflowUiAdapter.statusKey(recovered));
        assertEquals("screen.rtsbuilding.workflow.next.running",
                WorkflowUiAdapter.nextStepKey(recovered));
        assertEquals("screen.rtsbuilding.workflow.status.complete",
                WorkflowUiAdapter.statusKey(complete));
        assertEquals("screen.rtsbuilding.workflow.status.error",
                WorkflowUiAdapter.statusKey(error));
    }

    @Test
    void knownAndUnsafeServerDetailsUseTheProductionLocalizationHelper() {
        RtsWorkflowStatus waiting = statusWithDetail(
                RtsOperationReason.UNKNOWN, true, "workflow_waiting");
        RtsWorkflowStatus missing = statusWithDetail(
                RtsOperationReason.RESOURCE_MISSING, true, "missing:minecraft:stone needed=1");
        RtsWorkflowStatus unsafe = statusWithDetail(
                RtsOperationReason.EXECUTION_ERROR, false,
                "java.lang.IllegalStateException at C:\\server\\rtsbuilding");

        assertFalse(WorkflowUiAdapter.localizedDetail(waiting).equals("workflow_waiting"));
        assertFalse(WorkflowUiAdapter.localizedDetail(missing).contains("missing:minecraft"));
        assertFalse(WorkflowUiAdapter.localizedDetail(unsafe).contains("IllegalStateException"));
    }

    @Test
    void fullDetailsRetainZeroTo1024MissingIdsOnlyWhenOpened() {
        for (int count : new int[] {0, 1, 7, 1024}) {
            List<String> missing = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                missing.add(index == 0
                        ? "rtsbuilding:missing_" + "x".repeat(180)
                        : "rtsbuilding:missing_" + index);
            }
            WorkflowUiRow row = new WorkflowUiRow(
                    77, "place_batch", "Batch", "3/10",
                    3, 10, 0, 7, true, false, false, false,
                    "Waiting", List.of("Waiting"), missing,
                    "The task is waiting for materials.", "Refill and resume");

            List<String> lines = WorkflowUiAdapter.fullDetailLines(row);

            assertEquals(count + 4, lines.size());
            if (count > 0) {
                assertTrue(lines.get(2).contains(missing.get(0)));
                assertTrue(lines.get(count + 1).contains(missing.get(count - 1)));
            }
        }
    }

    @Test
    void recoveredPausedAndCompleteRowsDropStaleMissingSuggestions() {
        assertEquals(List.of("minecraft:stone"), WorkflowUiAdapter.visibleMissingItems(
                status(RtsOperationReason.RESOURCE_MISSING, true, false,
                        10, 1, List.of("minecraft:stone"))));
        assertEquals(List.of(), WorkflowUiAdapter.visibleMissingItems(
                status(RtsOperationReason.RESOURCE_MISSING, false, false,
                        10, 4, List.of("minecraft:stone"))));
        assertEquals(List.of(), WorkflowUiAdapter.visibleMissingItems(
                status(RtsOperationReason.MANUAL_PAUSED, true, true,
                        10, 1, List.of("minecraft:stone"))));
        assertEquals(List.of(), WorkflowUiAdapter.visibleMissingItems(
                status(RtsOperationReason.SUCCESS, false, false,
                        4, 4, List.of("minecraft:stone"))));
    }

    private static RtsWorkflowStatus statusWithDetail(
            RtsOperationReason reason,
            boolean suspended,
            String detail) {
        return RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.PLACE_BATCH,
                RtsWorkflowPriority.NORMAL,
                10,
                1,
                0,
                List.of("minecraft:stone"),
                detail,
                suspended,
                false,
                false,
                reason,
                77);
    }

    private static RtsWorkflowStatus status(
            RtsOperationReason reason,
            boolean suspended,
            boolean paused,
            int total,
            int completed,
            List<String> missing) {
        return RtsWorkflowStatus.fromRaw(
                RtsWorkflowType.PLACE_BATCH,
                RtsWorkflowPriority.NORMAL,
                total,
                completed,
                0,
                missing,
                "waiting_for_items",
                suspended,
                paused,
                false,
                reason,
                77);
    }
}
