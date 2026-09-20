package com.rtsbuilding.rtsbuilding.server.workflow.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 旧持久任务恢复投影时不得反向淘汰已经显示的新工作流。 */
class DurableProjectionDoesNotEvictNewWorkflowContractTest {

    @Test
    void restoringAcceptedProjectionDoesNotEvictOrApplyNewAdmissionLimit() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/core/RtsWorkflowEngine.java"));
        String method = source.substring(source.indexOf("public Optional<RtsWorkflowToken> restoreDurableProjection("));
        method = method.substring(0, method.indexOf("// ──────────────────────────────────────────────────────────────────"));

        assertFalse(method.contains("slots.isFull()"));
        assertTrue(method.contains("slots.addRestoredEntry(restored)"));
        assertFalse(method.contains("removeOldestReplaceableEntry()"));
        assertFalse(method.contains("cancelWorkflowTask(player"));
    }
}
