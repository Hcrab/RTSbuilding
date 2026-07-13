package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止已移除的高开销模式在后续重构中悄悄回到服务器热路径。 */
class BatchHotPathContractTest {
    @Test
    void placementUsesDualBudgetAndNeverPerTickRescansAllTargets() throws IOException {
        String source = readMain("server/service/placement/RtsPlacementBatch.java");
        assertTrue(source.contains("System.nanoTime() < deadlineNanos"));
        assertFalse(source.contains("Math.max(1, totalBlocks / 10)"));
        assertFalse(source.contains("RtsProgressRefresher.refreshWorkflowProgress(player, session)"));
    }

    @Test
    void destructionConsumesBudgetForSkippedTargets() throws IOException {
        String source = readMain("server/service/destruction/RtsDestructionBatch.java");
        int next = source.indexOf("BlockPos target = job.next();");
        int budget = source.indexOf("remaining--;", next);
        int firstSkip = source.indexOf("continue;", next);
        assertTrue(next >= 0 && budget > next && budget < firstSkip,
                "预算必须在任何权限/区块跳过分支之前消费");
    }

    @Test
    void placementHasARealTaskExecutor() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("registerExecutor(TaskType.PLACEMENT"));
        assertTrue(engine.contains("private TaskStepResult executePlacement"));
        assertFalse(engine.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void destructionHasARealTaskExecutor() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("registerExecutor(TaskType.DESTRUCTION"));
        assertTrue(engine.contains("private TaskStepResult executeDestruction"));
        assertFalse(engine.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void successfulBreakIsNotRetriedWhenToolBecomesProtected() throws IOException {
        String source = readMain("server/service/destruction/RtsDestructionBatch.java");
        int checkStart = source.indexOf("// 破坏后再次检查工具耐久");
        int failureStart = source.indexOf("} else {", checkStart);
        String protectedToolBranch = source.substring(checkStart, failureStart);
        assertFalse(protectedToolBranch.contains("unconsumeLast()"));
    }

    @Test
    void taskEngineSubmitsOnlyTheCurrentDomainHead() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("var job = session.placement.placeBatchJobs.peekFirst()"));
        assertTrue(engine.contains("var job = session.destruction.destroyJobs.peekFirst()"));
        assertFalse(engine.contains("for (var job : session.placement.placeBatchJobs)"));
        assertFalse(engine.contains("for (var job : session.destruction.destroyJobs)"));
    }

    @Test
    void missingWorkflowUsesDomainCleanupInsteadOfRawQueueRemoval() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("RtsPlacementBatch.cancelPlaceTask(player, session, job)"));
        assertTrue(engine.contains("RtsDestructionBatch.cancelDestroyTask(player, session, job)"));
    }

    @Test
    void miningRunsAsARealTask() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertTrue(engine.contains("registerExecutor(TaskType.MINING"));
        assertTrue(engine.contains("private TaskStepResult executeMining"));
        assertFalse(engine.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void miningBufferBackpressureIsAResourceWait() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        int miningStart = engine.indexOf("private TaskStepResult executeMining");
        String miningBody = engine.substring(miningStart);
        assertTrue(miningBody.contains("session.miningDropBuffer.isFull()"));
        assertTrue(miningBody.contains("TaskStepResult.waitForResource()"));
        assertTrue(engine.contains("record.status() == TaskStatus.WAITING_RESOURCE"
                + " && !session.miningDropBuffer.isFull()"));
    }

    @Test
    void ultimineCountsBrokenBlocksEvenWithoutAHistorySnapshot() throws IOException {
        String source = readMain("server/service/mining/RtsUltimineProcessor.java");
        assertTrue(source.contains("if (result.broken()) {"));
        assertTrue(source.contains("if (preRecord != null) {"));
        assertTrue(source.contains("session.mining.ultimineBrokenTargets++;"));
        assertFalse(source.contains("if (result.broken() && preRecord != null)"));
    }

    @Test
    void miningProjectsSuccessAndFailureThroughCoalescedWorkflowEffects() throws IOException {
        String processor = readMain("server/service/mining/RtsUltimineProcessor.java");
        String token = readMain("server/workflow/core/RtsWorkflowToken.java");
        assertTrue(processor.contains("reportWorkflowDelta(player, session"));
        assertTrue(processor.contains("token.recordFailures(failed)"));
        assertTrue(token.contains("public void recordFailures(int count)"));
        assertTrue(token.contains("engine.notifyPlayer(playerId, dimension)"));
    }

    @Test
    void dropBufferHasItsOwnTaskAndNoLegacyAdapterRemains() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        String types = readMain("server/task/TaskType.java");
        assertTrue(engine.contains("registerExecutor(TaskType.BUFFER_DRAIN"));
        assertTrue(engine.contains("private TaskStepResult executeBufferDrain"));
        assertTrue(engine.contains("TaskStepResult.nextTick(processed, completedStacks"));
        assertFalse(engine.contains("LegacyPlayerSlicePayload"));
        assertFalse(types.contains("LEGACY_ADAPTER"));
    }

    @Test
    void storageTickOnlyInvalidatesTheViewInsteadOfBuildingAPage() throws IOException {
        String orchestrator = readMain("server/service/ServerTickOrchestrator.java");
        assertTrue(orchestrator.contains("RtsEffectAccumulator.INSTANCE.markStorageViewDirty"));
        assertFalse(orchestrator.contains("serviceOp.refreshPage(player, session)"));
        assertFalse(orchestrator.contains("page().requestPage"));
    }

    @Test
    void storageTickWakesOnlyPendingJobsForChangedItems() throws IOException {
        String orchestrator = readMain("server/service/ServerTickOrchestrator.java");
        String pending = readMain("server/service/RtsPendingPlacementService.java");
        assertTrue(orchestrator.contains(
                "RtsPendingPlacementService.tryResumeAfterStorageChange(player, entry.getValue())"));
        assertTrue(pending.contains("session.placement.pendingJobsForItems(changedItemIds)"));
    }

    @Test
    void workflowIsAOneWayProjectionOfTaskLifecycle() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        int projection = engine.indexOf("private void projectWorkflowLifecycles()");
        String projectionBody = engine.substring(projection);
        assertTrue(projectionBody.indexOf("if (token == null) continue;")
                        < projectionBody.indexOf("projectedTaskStatuses.put(record.id(), record.status())"),
                "令牌尚未创建时不能把状态误记为已投影");
        assertTrue(projectionBody.contains("if (record.status().terminal())"));
        assertTrue(projectionBody.contains("workflowPauseOverrides.remove"));
        assertTrue(projectionBody.contains("releaseTerminalWorkflow(record)"));

        int placementExecutor = engine.indexOf("private TaskStepResult executePlacement");
        int destructionExecutor = engine.indexOf("private TaskStepResult executeDestruction");
        int miningExecutor = engine.indexOf("private TaskStepResult executeMining");
        int bufferExecutor = engine.indexOf("private TaskStepResult executeBufferDrain");
        assertFalse(engine.substring(placementExecutor, destructionExecutor).contains("token.isPaused()"));
        assertFalse(engine.substring(destructionExecutor, miningExecutor).contains("token.isPaused()"));
        assertFalse(engine.substring(miningExecutor, bufferExecutor).contains("token.isPaused()"));
        String miningState = readMain("server/service/mining/RtsMiningStateMachine.java");
        assertFalse(miningState.contains("tokenOpt.get().isPaused()"));
        assertFalse(miningState.contains("new WorkflowCompletePipe()"));
    }

    @Test
    void pauseBeforeTaskSynchronizationIsRemembered() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        int pause = engine.indexOf("public boolean setWorkflowPaused");
        int pauseAll = engine.indexOf("public void pauseAllWorkflowTasks", pause);
        String body = engine.substring(pause, pauseAll);
        assertTrue(body.indexOf("workflowPauseOverrides.put(key, paused)")
                < body.indexOf("TaskRecord record = findWorkflowTask(key)"));
        assertTrue(body.contains("if (record != null)"));
        assertTrue(body.contains("return true;"));
        assertTrue(body.contains("isTaskEngineWorkflow(workflow.type())"));
    }

    @Test
    void workflowUiCommandsMutateTaskEngineFirst() throws IOException {
        String handlers = readMain("network/builder/handler/RtsInteractionHandlers.java");
        assertTrue(handlers.contains("RtsTaskEngine.INSTANCE.cancelWorkflowTask"));
        assertTrue(handlers.contains("RtsTaskEngine.INSTANCE.setWorkflowPaused"));
        String session = readMain("server/service/impl/RtsSessionServiceImpl.java");
        assertTrue(session.indexOf("RtsTaskEngine.INSTANCE.pauseAllWorkflowTasks(player)")
                < session.indexOf("RtsWorkflowEngine.getInstance().pauseAllActive"));
    }

    @Test
    void taskEngineBatchPathDoesNotConsultWorkflowPauseValve() throws IOException {
        String placement = readMain("server/service/placement/RtsPlacementBatch.java");
        String destruction = readMain("server/service/destruction/RtsDestructionBatch.java");
        assertTrue(placement.contains("if (onlyJob != null)"));
        assertTrue(destruction.contains("if (onlyJob != null)"));
        assertTrue(placement.indexOf("if (onlyJob != null)")
                < placement.indexOf("RtsBatchJobTickOps.checkPausedOrCancelled", placement.indexOf("if (onlyJob != null)")));
        assertTrue(destruction.indexOf("if (onlyJob != null)")
                < destruction.indexOf("RtsBatchJobTickOps.checkPausedOrCancelled", destruction.indexOf("if (onlyJob != null)")));
        String common = readMain("server/service/RtsBatchJobTickOps.java");
        assertTrue(common.contains("if (releaseWorkflow) token.complete()"));
        assertTrue(placement.contains("onlyJob == null); // Task Engine 路径由 TaskRecord 终态释放工作流槽位"));
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
