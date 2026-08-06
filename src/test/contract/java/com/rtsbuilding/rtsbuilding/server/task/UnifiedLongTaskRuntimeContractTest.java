package com.rtsbuilding.rtsbuilding.server.task;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止后续改动悄悄恢复第二套 Tick runtime 或预算外实体全扫描。 */
class UnifiedLongTaskRuntimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void legacyTickRuntimeClassesStayDeleted() {
        Path core = MAIN.resolve("server/pipeline/core");
        assertFalse(Files.exists(core.resolve("TickablePipelineRegistry.java")));
        assertFalse(Files.exists(core.resolve("ActivePipeline.java")));
        assertFalse(Files.exists(core.resolve("TickablePipe.java")));
        assertFalse(Files.exists(MAIN.resolve("server/pipeline/mining/UltimineTickPipe.java")));
    }

    @Test
    void orchestratorHasOnlyOneLongTaskRuntime() throws IOException {
        String source = read("server/service/ServerTickOrchestrator.java");
        assertTrue(source.contains("RtsTaskEngine.INSTANCE.tick(server)"));
        assertFalse(source.contains("TickablePipelineRegistry"));
        assertFalse(source.contains("funnel().tick"));
        assertFalse(source.contains("RtsPlacedRecoveryService.tick"));
    }

    @Test
    void funnelEntityQueryHasHardResultLimit() throws IOException {
        String source = read("server/service/impl/RtsFunnelServiceImpl.java");
        assertTrue(source.contains("RtsBoundedCollectibleEntityQuery.query("));
        assertTrue(source.contains("instanceof EntityXPOrb"));
        assertTrue(source.contains("experienceOrb.onCollideWithPlayer(player)"));
        assertFalse(source.contains("getEntitiesWithinAABB("));
    }

    @Test
    void recoveryQueueStoresDurableEntityClaimsInsteadOfAnonymousCopiedStacks() throws IOException {
        String state = read("server/storage/state/RtsPlacementState.java");
        String service = read("server/service/RtsPlacedRecoveryService.java");
        String serializer = read("server/data/SessionSerializer.java");
        assertTrue(state.contains("UUID operationId"));
        assertTrue(state.contains("Deque<PlacedRecoveryClaim> claims"));
        assertTrue(state.contains("int ordinal"));
        assertTrue(state.contains("ItemStack expectedStack"));
        assertTrue(state.contains("ItemStack.areItemStacksEqual(actual, expectedStack)"));
        assertTrue(service.contains("droppedEntity.getUniqueID()"));
        assertTrue(service.contains("claim.matches(droppedStack)"));
        assertTrue(serializer.contains("setUniqueId(\"operation_id\""));
        assertTrue(serializer.contains("setInteger(\"ordinal\""));
        assertTrue(serializer.contains("setTag(\"stack\""));
        assertFalse(service.contains("stacks.addLast(droppedStack.copy())"));
    }

    @Test
    void blueprintExecutionIsDimensionBoundAndReleasesTerminalPayload() throws IOException {
        String payload = read("server/task/BlueprintTaskPayload.java");
        String engine = read("server/task/RtsTaskEngine.java");
        String persistence = read("server/pipeline/blueprint/BlueprintPersistence.java");
        String executor = read("server/pipeline/blueprint/BlueprintTickPipe.java");

        assertTrue(payload.contains("private final int dimension"));
        assertTrue(engine.contains("payload.player().dimension != payload.dimension()"));
        assertTrue(engine.contains("blueprintRecords.entrySet().removeIf"));
        assertTrue(persistence.contains("KEY_SOURCE_DIMENSION"));
        assertTrue(persistence.contains("bctx.getData(BlueprintContext.KEY_SOURCE_DIMENSION)"));
        assertTrue(persistence.contains("sourceDimension"));
        assertTrue(executor.contains("token.recordFailures(failures)"));
        assertFalse(executor.contains("for (int i = 0; i < failures"));
    }

    @Test
    void everyWorldMutatingTaskFreezesItsCreationDimension() throws IOException {
        String placement = read("server/task/PlacementTaskPayload.java");
        String destruction = read("server/task/DestructionTaskPayload.java");
        String mining = read("server/task/MiningTaskPayload.java");
        String engine = read("server/task/RtsTaskEngine.java");
        String durableRuntime = read("server/task/RtsDurableTaskExecutionRuntime.java");

        assertTrue(placement.contains("private final int dimension"));
        assertTrue(destruction.contains("private final int dimension"));
        assertTrue(mining.contains("private final int dimension"));
        assertTrue(count(engine, "dimension != payload.dimension()")
                        + count(durableRuntime, "dimension != payload.dimension()") >= 4,
                "placement/destruction/mining/blueprint 均须在切维时让出执行");
        assertTrue(engine.contains("return ((PlacementTaskPayload) record.payload()).dimension()"));
        assertTrue(engine.contains("return ((DestructionTaskPayload) record.payload()).dimension()"));
        assertTrue(engine.contains("return ((MiningTaskPayload) record.payload()).dimension()"));
        assertTrue(engine.contains("return ((BlueprintTaskPayload) record.payload()).dimension()"));
    }

    @Test
    void recoveryClaimsAreBoundedAndUnloadedChunksArePreserved() throws IOException {
        String service = read("server/service/RtsPlacedRecoveryService.java");
        String serializer = read("server/data/SessionSerializer.java");

        assertTrue(service.contains("setNoDespawn()"));
        assertTrue(service.contains("isBlockLoaded(candidate.targetPos())"));
        assertTrue(count(service, "RtsBoundedItemEntityQuery.query(") >= 2,
                "恢复前后两次实体查询都必须使用 1.12 专用硬上限 helper");
        assertFalse(service.contains("getEntitiesWithinAABB("));
        assertTrue(service.contains("PLACED_RECOVERY_MAX_QUEUED_JOBS"));
        assertTrue(service.contains("PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS"));
        assertTrue(serializer.contains("PLACED_RECOVERY_MAX_ENTITIES_PER_JOB"));
        assertTrue(serializer.contains("PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS"));
    }

    @Test
    void recoveryWaitsForDurabilityAckBeforeConsumingWorldEntity() throws IOException {
        String service = read("server/service/RtsPlacedRecoveryService.java");
        String state = read("server/storage/state/RtsPlacementState.java");
        String cluster = read("server/data/DataCluster.java");

        assertTrue(state.contains("requiredPersistedRevision"));
        assertTrue(service.contains("persistedPlacementRevision(player)"));
        assertTrue(service.contains("candidate.requiredPersistedRevision() <= persistedPlacementRevision"));
        assertTrue(service.contains("savePlacementToPlayerNbt(player, session)"));
        assertTrue(cluster.contains("persistedRevision(DataComponent<?> component)"));
    }

    @Test
    void funnelTargetIsDimensionBoundAndNewOverflowStaysInWorld() throws IOException {
        String state = read("server/storage/state/RtsFunnelState.java");
        String service = read("server/service/impl/RtsFunnelServiceImpl.java");
        String serializer = read("server/data/SessionSerializer.java");
        String tickSource = service.substring(service.indexOf("public FunnelTickResult tickBudgeted"));

        assertTrue(state.contains("Integer funnelTargetDimension"));
        assertTrue(serializer.contains("funnel_target_dimension"));
        assertTrue(tickSource.contains("player.dimension != session.funnel.funnelTargetDimension.intValue()"));
        assertTrue(tickSource.indexOf("funnelTargetDimension == null")
                < tickSource.indexOf("sanitizeSessionDimension(player, session)"));
        assertTrue(service.contains("saveFunnelToPlayerNbt(player, session)"));
        assertFalse(service.contains("addToBuffer("));
        assertFalse(service.contains("saveToPlayerNbt(player, session)"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static int count(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }
}
