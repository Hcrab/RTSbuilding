package com.rtsbuilding.rtsbuilding.network.builder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 中央工作流同步接线的静态契约；不启动客户端、服务端或网络。 */
class WorkflowBackgroundHookupContractTest {
    @Test
    void payloadsAreRegisteredDispatchedAndQueuedOnClientThread() throws IOException {
        String registration = read("src/main/java/com/rtsbuilding/rtsbuilding/network/builder/RtsBuilderPackets.java");
        String dispatcher = read("src/main/java/com/rtsbuilding/rtsbuilding/network/ClientPayloadDispatcher.java");
        String handlers = read("src/main/java/com/rtsbuilding/rtsbuilding/client/network/RtsClientNetworkHandlers.java");
        String facade = read("src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsWorkflowFacade.java");
        String controller = read("src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java");

        assertTrue(registration.contains("S2CRtsWorkflowProgressPayload.TYPE"));
        assertTrue(registration.contains("S2CRtsWorkflowProgressPayload.STREAM_CODEC"));
        assertTrue(registration.contains("S2CRtsWorkflowProgressBatchPayload.TYPE"));
        assertTrue(registration.contains("S2CRtsWorkflowProgressBatchPayload.STREAM_CODEC"));
        assertTrue(registration.contains("ClientPayloadDispatcher::dispatchBuilder"));

        assertTrue(dispatcher.contains("S2CRtsWorkflowProgressPayload p"));
        assertTrue(dispatcher.contains("S2CRtsWorkflowProgressBatchPayload p"));
        assertTrue(dispatcher.contains("handleWorkflowProgress(p, ctx)"));
        assertTrue(dispatcher.contains("handleWorkflowProgressBatch(p, ctx)"));
        assertTrue(handlers.contains("context.enqueueWork"));
        assertTrue(handlers.contains("ClientRtsController.get().applyWorkflowProgress(payload)"));
        assertTrue(handlers.contains("ClientRtsController.get().applyWorkflowProgressBatch(payload)"));
        assertTrue(facade.contains("workflowStateManager.apply(payload)"));
        assertTrue(facade.contains("workflowStateManager.applyBatch(payload)"));
        assertTrue(controller.contains("extends ClientRtsWorkflowFacade"));
    }

    @Test
    void serverSnapshotFieldsReachPayloadAndPlayerSender() throws IOException {
        String sync = read("src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/service/RtsWorkflowSyncService.java");
        String sender = read("src/main/java/com/rtsbuilding/rtsbuilding/server/network/RtsClientboundPackets.java");
        String protocol = read("src/main/java/com/rtsbuilding/rtsbuilding/network/RtsForgePayloadRegistrar.java");
        String workflowEngine = read("src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/core/RtsWorkflowEngine.java");
        String orchestrator = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/ServerTickOrchestrator.java");
        String effects = read("src/main/java/com/rtsbuilding/rtsbuilding/server/task/effect/RtsProductionEffectCommitter.java");

        assertTrue(sync.contains("status.missingItems()"));
        assertTrue(sync.contains("status.detailMessage()"));
        assertTrue(sync.contains("status.reason().wireId()"));
        assertTrue(sync.contains("new S2CRtsWorkflowProgressBatchPayload(entries)"));
        assertTrue(sync.contains("RtsForgePayloadRegistrar.sendToPlayer"));
        assertTrue(sender.contains("sendToPlayer"));
        assertTrue(sender.contains("RtsForgePayloadRegistrar"));
        assertTrue(protocol.contains("RtsNetworkProtocol.VERSION"));
        assertTrue(workflowEngine.contains("RtsEffectAccumulator.INSTANCE.markWorkflow"));
        assertTrue(orchestrator.contains("RtsEffectAccumulator.INSTANCE.flush(server)"));
        assertTrue(effects.contains("case WORKFLOW_SNAPSHOT"));
        assertTrue(effects.contains("flushPlayerNow"));
    }

    @Test
    void replacementAndCancellationClearProjectionBeforeTaskTerminalSideEffects() throws IOException {
        String workflow = read("src/main/java/com/rtsbuilding/rtsbuilding/server/workflow/core/RtsWorkflowEngine.java");
        String tasks = read("src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java");
        String runtime = read("src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsDurableTaskExecutionRuntime.java");

        int replacementMark = workflow.indexOf(
                "markTerminalWithReason(replaced, RtsOperationReason.REPLACED");
        int replacementCancel = workflow.indexOf(
                "cancelWorkflowTask(player, dimension, replaced.id(), RtsOperationReason.REPLACED)");
        assertTrue(replacementMark >= 0 && replacementMark < replacementCancel);

        int cancelMark = workflow.indexOf(
                "markTerminalWithReason(entry, RtsOperationReason.CANCELLED");
        int cancelCall = workflow.indexOf(
                "cancelWorkflowTask(player, dimension, entryId, RtsOperationReason.CANCELLED)");
        assertTrue(cancelMark >= 0 && cancelMark < cancelCall);

        assertTrue(tasks.contains("RtsOperationReason terminalReason"));
        assertTrue(tasks.contains("replaced_by_newer_workflow"));
        assertTrue(tasks.contains("resolvedReason.diagnosticId()"));
        assertTrue(tasks.contains("coordinator.requestTombstone(cancelled.id(), cancelled.updatedGameTime())"));
        assertTrue(runtime.contains("terminalReason != null"));
        assertTrue(runtime.contains("reasonDetail"));
    }

    @Test
    void manualPauseUsesPausedLifecycleAndStorageWakeupRemainsTargeted() throws IOException {
        String tasks = read("src/main/java/com/rtsbuilding/rtsbuilding/server/task/RtsTaskEngine.java");
        String pending = read("src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsPendingPlacementService.java");
        int pause = tasks.indexOf("public boolean setWorkflowPaused");
        int resume = tasks.indexOf("public void resumeWaitingPlacementItems", pause);
        assertTrue(pause >= 0 && resume > pause);
        String pauseBody = tasks.substring(pause, resume);
        assertTrue(pauseBody.contains("TaskLifecycleState.PAUSED"));
        assertTrue(pauseBody.contains("workflowPauseOverrides.put(key, paused)"));
        assertTrue(pending.contains("resumeWaitingPlacementItems(player, changedItemIds)"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }
}
