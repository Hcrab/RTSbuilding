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
        assertTrue(source.contains("EntityTypeTest.forClass(ItemEntity.class)"));
        assertTrue(source.contains("drops, queryLimit"));
        assertFalse(source.contains("getEntitiesOfClass("));
    }

    @Test
    void recoveryQueueStoresEntityIdentityInsteadOfCopiedStacks() throws IOException {
        String state = read("server/storage/state/RtsPlacementState.java");
        String service = read("server/service/RtsPlacedRecoveryService.java");
        assertTrue(state.contains("Deque<UUID> entityIds"));
        assertTrue(service.contains("droppedEntity.getUUID()"));
        assertFalse(service.contains("stacks.addLast(droppedStack.copy())"));
    }

    @Test
    void blueprintExecutionIsDimensionBoundAndReleasesTerminalPayload() throws IOException {
        String payload = read("server/task/BlueprintTaskPayload.java");
        String engine = read("server/task/RtsTaskEngine.java");
        String persistence = read("server/pipeline/blueprint/BlueprintPersistence.java");
        String executor = read("server/pipeline/blueprint/BlueprintTickPipe.java");

        assertTrue(payload.contains("ResourceKey<Level> dimension"));
        assertTrue(engine.contains("equals(payload.dimension())"));
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

        assertTrue(placement.contains("ResourceKey<Level> dimension"));
        assertTrue(destruction.contains("ResourceKey<Level> dimension"));
        assertTrue(mining.contains("ResourceKey<Level> dimension"));
        assertTrue(count(engine, "equals(payload.dimension())") >= 4,
                "placement/destruction/mining/blueprint 均须在切维时让出执行");
        assertTrue(engine.contains("return payload.dimension()"));
    }

    @Test
    void recoveryClaimsAreBoundedAndUnloadedChunksArePreserved() throws IOException {
        String service = read("server/service/RtsPlacedRecoveryService.java");
        String serializer = read("server/data/SessionSerializer.java");

        assertTrue(service.contains("setUnlimitedLifetime()"));
        assertTrue(service.contains("hasChunkAt(candidate.targetPos())"));
        assertTrue(service.contains("PLACED_RECOVERY_MAX_QUEUED_JOBS"));
        assertTrue(service.contains("PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS"));
        assertTrue(serializer.contains("PLACED_RECOVERY_MAX_ENTITIES_PER_JOB"));
        assertTrue(serializer.contains("PLACED_RECOVERY_MAX_TOTAL_ENTITY_CLAIMS"));
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
