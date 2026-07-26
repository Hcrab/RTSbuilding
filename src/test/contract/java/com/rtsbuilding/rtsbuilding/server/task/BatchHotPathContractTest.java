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
        int detached = source.indexOf("tickDetachedDestructionSlice(");
        int next = source.indexOf("BlockPos target = job.next();", detached);
        int budget = source.indexOf("processed++;", next);
        int firstSkip = source.indexOf("continue;", next);
        assertTrue(next >= 0 && budget > next && budget < firstSkip,
                "预算必须在任何权限/区块跳过分支之前消费");
    }

    @Test
    void placementHasARealTaskExecutor() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(runtime.contains("scheduler.register(TaskType.PLACEMENT"));
        assertTrue(runtime.contains("private DurableTaskScheduler.SliceResult executeDurablePlacement"));
        assertFalse(runtime.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void destructionHasARealTaskExecutor() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(runtime.contains("scheduler.register(TaskType.DESTRUCTION"));
        assertTrue(runtime.contains("private DurableTaskScheduler.SliceResult executeDurableDestruction"));
        assertFalse(runtime.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void rangeDestructionUsesWriteBehindCheckpointsInsteadOfWaitingForEverySlice() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        int start = runtime.indexOf("private DurableTaskScheduler.SliceResult executeDurableDestruction");
        int end = runtime.indexOf("private DurableTaskScheduler.SliceResult durableNoProgress", start);
        String destruction = runtime.substring(start, end);

        assertTrue(destruction.contains("DestructionProgressOverlay"));
        assertTrue(destruction.contains("pendingUnits >= 256"));
        assertTrue(destruction.contains(
                "return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits())"));
        assertTrue(runtime.contains("checkpointDestructionExecutions"));
        assertTrue(runtime.contains("transitionDestructionSnapshot"));
    }

    @Test
    void successfulBreakIsNotRetriedWhenToolBecomesProtected() throws IOException {
        String source = readMain("server/service/destruction/RtsDestructionBatch.java");
        int start = source.indexOf("public static DestructionSliceResult tickDetachedDestructionSlice(");
        int end = source.indexOf("public static void recordDetachedHistory(", start);
        String detached = source.substring(start, end);
        int successfulBreak = detached.indexOf("job.destroyedPositions.add(target)");
        int protectionCheck = detached.lastIndexOf(
                "RtsMiningValidator.isToolNearBreak(player, session, job.toolSlot())");
        assertTrue(protectionCheck >= 0 && successfulBreak < protectionCheck);
        assertFalse(detached.contains("unconsumeLast()"));
    }

    @Test
    void taskEngineContainsNoLegacySessionQueues() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        assertFalse(engine.contains("placeBatchJobs"));
        assertFalse(engine.contains("pendingJobs"));
        assertFalse(engine.contains("destroyJobs"));
        assertFalse(engine.contains("pendingDestroyJobs"));
        assertFalse(engine.contains("ultimineJobQueue"));
    }

    @Test
    void miningRunsAsARealTask() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(runtime.contains("scheduler.register(TaskType.MINING"));
        assertTrue(runtime.contains("private DurableTaskScheduler.SliceResult executeDurableMining"));
        assertFalse(runtime.contains("executeLegacyPlayerSlice"));
    }

    @Test
    void progressiveMiningAnimationDoesNotWaitForDiskAckAtEveryStage() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        assertTrue(runtime.contains("MiningProgressOverlay"));
        assertTrue(runtime.contains("result.outcome() == com.rtsbuilding.rtsbuilding.server.task.mining.MiningSliceResult.Outcome.NEXT_TICK"));
        assertTrue(runtime.contains("new MiningProgressOverlay("));
        assertTrue(runtime.contains("return new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits())"));
    }

    @Test
    void batchMiningUsesWriteBehindCheckpointsAndDoesNotReencodeAllHistoryPerSlice() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        String mining = readMain("server/service/mining/RtsMiningStateMachine.java");
        assertTrue(runtime.contains("pendingUnits >= 256"));
        assertTrue(runtime.contains("new DurableTaskScheduler.SliceResult(snapshot, result.processedUnits())"));
        assertTrue(mining.contains("state.appendFrozenHistoryTo(history)"));
        assertFalse(mining.contains("history.stream()\n                .map(MiningTaskCodec::encodeHistory)"));
    }

    @Test
    void miningBufferBackpressureIsAResourceWait() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        String miningState = readMain("server/service/mining/RtsMiningStateMachine.java");
        String waitHint = readMain("server/task/mining/MiningWaitHint.java");
        int miningStart = runtime.indexOf("executeDurableMining(");
        String miningBody = runtime.substring(miningStart);
        assertTrue(miningState.contains("session.miningDropBuffer.isFull()"));
        assertTrue(miningState.contains("MiningSliceResult.Outcome.WAITING, MiningWaitHint.buffer()"));
        assertTrue(waitHint.contains("new MiningWaitHint(\"buffer\", \"mining_drop_buffer\")"));
        assertTrue(miningBody.contains("new com.rtsbuilding.rtsbuilding.server.task.persistence.TaskWaitKey("));
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
    void dropBufferUsesBoundedMemoryInsteadOfADurableTaskProtocol() throws IOException {
        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        String absorber = readMain("server/service/mining/RtsDropAbsorber.java");
        String engine = readMain("server/task/RtsTaskEngine.java");
        String types = readMain("server/task/TaskType.java");
        assertTrue(absorber.contains("private static boolean enqueueDrops"));
        assertTrue(engine.contains("drainDropBuffer(player, session, 16, deadline)"));
        assertFalse(runtime.contains("BufferEscrow"));
        assertFalse(types.contains("BUFFER_DRAIN"));
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
        assertTrue(pending.contains("resumeWaitingPlacementItems(player, changedItemIds)"));
        assertFalse(pending.contains("pendingJobsForItems(changedItemIds)"));
        assertFalse(pending.contains("placeBatchJobs.addLast"));
    }

    @Test
    void workflowIsAOneWayProjectionOfTaskLifecycle() throws IOException {
        String engine = readMain("server/task/RtsTaskEngine.java");
        int projection = engine.indexOf("private void projectDurableWorkflowLifecycles(");
        String projectionBody = engine.substring(projection);
        assertTrue(projectionBody.contains("inspectDurableProjection(player, snapshot)"));
        assertTrue(projectionBody.contains("restoreDurableProjection("),
                "持久任务缺少 UI 条目时必须重建投影，不能让真实任务在后台隐身");
        assertTrue(projectionBody.indexOf("if (token == null) continue;")
                        < projectionBody.lastIndexOf(
                                "projectedDurableStates.put(snapshot.id(), snapshot.revision())"),
                "重建失败时不能把状态误记为已投影");
        assertTrue(projectionBody.contains("case COMPLETED -> token.complete()"));
        assertTrue(projectionBody.contains("case FAILED, CANCELLED -> token.cancel()"));

        String runtime = readMain("server/task/RtsDurableTaskExecutionRuntime.java");
        int placementExecutor = runtime.indexOf("executeDurablePlacement(");
        int destructionExecutor = runtime.indexOf("executeDurableDestruction(");
        int miningExecutor = runtime.indexOf("executeDurableMining(");
        int runtimeHelpers = runtime.indexOf("private static boolean durableRevisionAcknowledged(");
        assertFalse(runtime.substring(placementExecutor, destructionExecutor).contains("token.isPaused()"));
        assertFalse(runtime.substring(destructionExecutor, miningExecutor).contains("token.isPaused()"));
        assertFalse(runtime.substring(miningExecutor, runtimeHelpers).contains("token.isPaused()"));
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
        assertTrue(handlers.contains("RtsTaskEngine.INSTANCE.setWorkflowPaused"));
        String workflows = readMain("server/workflow/core/RtsWorkflowEngine.java");
        int delete = workflows.indexOf("public void deleteWorkflow(");
        int cancelAll = workflows.indexOf("public void cancelAll(", delete);
        String deleteBody = workflows.substring(delete, cancelAll);
        assertTrue(deleteBody.indexOf("cancelWorkflowTask(player, entryId)")
                        < deleteBody.indexOf("slots.removeEntryById(entryId)"),
                "删除 UI 条目前必须先取消真实任务");
        String taskEngine = readMain("server/task/RtsTaskEngine.java");
        int cancel = taskEngine.indexOf("public boolean cancelWorkflowTask(");
        int resume = taskEngine.indexOf("public void resumeWaitingPlacementItems(", cancel);
        String cancelBody = taskEngine.substring(cancel, resume);
        assertTrue(cancelBody.indexOf("coordinator.replace(cancelled)")
                        < cancelBody.indexOf("coordinator.requestTombstone("),
                "控制面取消 durable task 后必须申请 tombstone，不能让终态永久留在 active store");
        String session = readMain("server/service/impl/RtsSessionServiceImpl.java");
        assertTrue(session.indexOf("RtsTaskEngine.INSTANCE.pauseAllWorkflowTasks(player)")
                < session.indexOf("RtsWorkflowEngine.getInstance().pauseAllActive"));
    }

    @Test
    void missingWorkflowProjectionStillClearsOrReconcilesClient() throws IOException {
        String workflows = readMain("server/workflow/core/RtsWorkflowEngine.java");
        int flush = workflows.indexOf("public void flushPlayerNow(");
        int fireEvent = workflows.indexOf("void fireEvent(", flush);
        String flushBody = workflows.substring(flush, fireEvent);
        assertTrue(flushBody.contains("syncService.sendIdle(player)"),
                "最后一个服务端条目被清理后仍必须清空客户端旧投影");

        int delete = workflows.indexOf("public void deleteWorkflow(");
        int cancelAll = workflows.indexOf("public void cancelAll(", delete);
        String deleteBody = workflows.substring(delete, cancelAll);
        assertTrue(deleteBody.contains("if (slots == null)"));
        assertTrue(deleteBody.contains("syncService.sendIdle(player)"),
                "点击叉号时，即使服务端条目已经消失也必须让客户端自愈");
        assertTrue(deleteBody.contains("syncService.notifyPlayer(player, slots)"),
                "只有目标条目缺失时也要回传其余权威条目");
    }

    private static String readMain(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java/com/rtsbuilding/rtsbuilding").resolve(relative));
    }
}
