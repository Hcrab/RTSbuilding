package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁住蓝图新 root 的 ACK 边界、薄投影、旧数据迁移和停服 barrier。 */
class DurableBlueprintProductionWiringContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void newCommandCannotCreateWorkflowOrExecutorBeforeDurableRootAck() throws IOException {
        String registration = read("server/pipeline/core/RtsPipelineRegistration.java");
        String execute = read("server/pipeline/blueprint/BlueprintExecutePipe.java");
        String bridge = read("server/task/DurableBlueprintTaskBridge.java");

        String blueprintPipeline = registration.substring(registration.indexOf("private static void registerBlueprintBuild()"));
        blueprintPipeline = blueprintPipeline.substring(0, blueprintPipeline.indexOf("// ─────────────────"));
        assertFalse(blueprintPipeline.contains("WorkflowStartPipe"));
        assertFalse(blueprintPipeline.contains("UiRefreshPipe"));
        assertTrue(execute.contains("queueDurableBlueprint(bctx)"));
        assertFalse(execute.contains("submitBlueprint(bctx"));
        assertFalse(execute.contains("BlueprintPersistence.saveToEntry"));
        assertTrue(bridge.contains("BlueprintAdmissionOutcome.ROOT_DURABLE"));
        assertTrue(bridge.indexOf("BlueprintAdmissionOutcome.ROOT_DURABLE")
                < bridge.indexOf("activate(server, taskId)"));
    }

    @Test
    void identityProgressTerminalAndStopUseOneDurableAuthority() throws IOException {
        String bridge = read("server/task/DurableBlueprintTaskBridge.java");
        String engine = read("server/task/RtsTaskEngine.java");
        String mod = read("RtsbuildingMod.java");
        String scheduler = read("server/task/TaskScheduler.java");
        String stopping = between(mod, "static void onServerStopping(", "static void onServerStopped(");
        String stopped = mod.substring(mod.indexOf("static void onServerStopped("));

        assertTrue(bridge.contains("TaskId.fromSubmission(player.getUUID(), submissionId)"));
        assertTrue(engine.contains("new TaskRecord(taskId.value()"));
        assertTrue(bridge.contains("persistence.coordinator().replace(next)"));
        assertTrue(bridge.contains("requestTombstone(entry.getKey(), gameTime)"));
        assertTrue(stopping.contains("checkpointAllDurableExecutions(event.getServer())"));
        assertFalse(stopping.contains("TaskPersistenceRuntime.INSTANCE.stop()"),
                "PlayerLoggedOutEvent 发生前不能关闭 durable writer");
        assertTrue(stopped.indexOf("TaskPersistenceRuntime.INSTANCE.isStarted()")
                < stopped.indexOf("TaskPersistenceRuntime.INSTANCE.stop()"),
                "启动失败后的 ServerStopped 必须跳过不存在的 writer");
        assertTrue(stopped.indexOf("TaskPersistenceRuntime.INSTANCE.stop()")
                < stopped.indexOf("resetDurableRuntimeAfterServerStop()"));
        assertTrue(scheduler.contains("public synchronized void clear()"));
        assertTrue(engine.contains("scheduler.clear()"));
    }

    @Test
    void blueprintPreparationDrainsTheOrderedQueueInsteadOfClearingItAtReadyBoundary() throws IOException {
        String payload = read("server/task/BlueprintTaskPayload.java");
        String queueStage = between(payload, "case QUEUE ->", "case READY ->");

        assertTrue(queueStage.contains("context.getRemainingQueue().addLast(orderedIndices.remove())"));
        assertFalse(queueStage.contains("setRemainingQueue("),
                "QUEUE 阶段结束时重新分配空队列会静默丢失全部放置计划");
    }

    @Test
    void legacyHeavyDataMigratesDeterministicallyAndNeverStartsOldExecutor() throws IOException {
        String persistence = read("server/pipeline/blueprint/BlueprintPersistence.java");
        String bridge = read("server/task/DurableBlueprintTaskBridge.java");

        assertTrue(persistence.contains("SubmissionId.fromLegacy("));
        assertTrue(persistence.contains("sourceDimension.location() + \":\" + entry.id()"));
        assertTrue(persistence.contains("queueLegacyDurableBlueprint(ctx)"));
        assertFalse(persistence.contains("INSTANCE.submitBlueprint(\n                ctx"));
        assertTrue(persistence.contains("if (!preparing) ctx.setRemainingQueue(remaining)"));
        assertTrue(bridge.contains("root ACK 可能先于 legacy heavy→thin 投影"));
        assertTrue(bridge.contains("ProjectionClaimDecision.DEFER_UNCLAIMED_HEAVY) return false"));
        assertTrue(bridge.contains("projection.putUUID(WORKFLOW_TASK_ID, taskId.value())"));
    }

    @Test
    void durableContextsCannotFallBackToHeavyWorkflowCheckpoints() throws IOException {
        String taskEngine = read("server/task/RtsTaskEngine.java");
        String tickPipe = read("server/pipeline/blueprint/BlueprintTickPipe.java");
        String refresher = read("server/service/RtsProgressRefresher.java");

        assertTrue(taskEngine.contains("!durableBlueprintBridge.isDurableContext(payload.context())"));
        assertTrue(tickPipe.contains(".isDurableBlueprintContext(context)) return"));
        assertTrue(refresher.contains(".isDurableBlueprintContext(bctx)"));
    }

    @Test
    void duplicateSubmissionComparesEveryPlacementChangingField() throws IOException {
        String bridge = read("server/task/DurableBlueprintTaskBridge.java");
        assertTrue(bridge.contains("durableBlob.structure().equals(frozen.structure())"));
        assertTrue(bridge.contains("PAYLOAD_ANCHOR) != frozen.anchor().asLong()"));
        assertTrue(bridge.contains("PAYLOAD_CENTER) != frozen.center().asLong()"));
        assertTrue(bridge.contains("PAYLOAD_Y) != frozen.ySteps()"));
        assertTrue(bridge.contains("PAYLOAD_X) != frozen.xSteps()"));
        assertTrue(bridge.contains("PAYLOAD_Z) != frozen.zSteps()"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "无法定位源码片段: " + start + " ... " + end);
        return source.substring(startIndex, endIndex);
    }
}
