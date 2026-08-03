package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止持久化放置上限再次绕过“新工作流淘汰未钉住旧工作流”的产品规则。 */
class TaskEngineWorkflowAdmissionContractTest {

    @Test
    void taskEngineAdmissionEvictsReplaceableTasksAndRejectsHiddenWorkflows() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java"));

        assertTrue(source.contains("makeRoomForDurableTaskFamily("));
        assertTrue(source.contains("snapshot.dimensionId().equals(dimensionId)"));
        assertTrue(source.contains("occupiesQuickBuildSlot"));
        assertTrue(source.contains("entry == null || !entry.protectedWorkflow()"));
        assertTrue(source.contains("cancelWorkflowTask(player, player.dimension"));
        assertTrue(source.contains("TaskLifecycleState.CANCELLED"));
        assertTrue(source.contains("reconcileHiddenDurableWorkflows(player, coordinator)"));
        assertTrue(source.contains("TaskType.DESTRUCTION,"));
        assertTrue(source.contains("已终止无法恢复可见投影的隐藏任务"));
        assertTrue(source.contains("已终止没有合法工作流 ID 的隐藏任务"));
        assertTrue(source.contains("已终止缺失可见工作流投影的蓝图任务"));
        assertTrue(source.contains("终态旧任务只收口已有投影"));
    }
}
