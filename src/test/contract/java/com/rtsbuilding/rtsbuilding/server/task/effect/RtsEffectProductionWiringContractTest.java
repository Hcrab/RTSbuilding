package com.rtsbuilding.rtsbuilding.server.task.effect;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁住候选 C 的生产接线，避免普通热路径重新绕开副作用屏障。 */
class RtsEffectProductionWiringContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/rtsbuilding/rtsbuilding");

    @Test
    void accumulatorOwnsTypedLedgerAndBarrierWithoutLegacyDrainQueue() throws IOException {
        String accumulator = read("server/task/RtsEffectAccumulator.java");
        assertTrue(accumulator.contains("RtsEffectLedger<RtsPlayerEffectTarget>"));
        assertTrue(accumulator.contains("RtsEffectCommitBarrier<RtsPlayerEffectTarget>"));
        assertTrue(accumulator.contains("new RtsProductionEffectCommitter(server)"));
        assertFalse(accumulator.contains("CoalescingEffectQueue"));
        assertFalse(accumulator.contains(".drain("));
        assertFalse(Files.exists(MAIN.resolve("server/task/CoalescingEffectQueue.java")));
    }

    @Test
    void tickOrderBuildsLatestPageThenCommitsEffectsThenStagesDiskWrites() throws IOException {
        String orchestrator = read("server/service/ServerTickOrchestrator.java");
        assertOrdered(orchestrator,
                "RtsStoragePageRequestCoalescer.flushPending()",
                "RtsEffectAccumulator.INSTANCE.flush(server)");

        String mod = read("RtsbuildingMod.java");
        assertOrdered(mod,
                "ServerTickOrchestrator.getInstance().tickMining(event.getServer())",
                "SaveScheduler.INSTANCE.onTick(event.getServer())",
                "TaskPersistenceRuntime.INSTANCE.tick()");
    }

    @Test
    void ordinaryServiceTemplateOnlyMarksStorageDirty() throws IOException {
        String template = read("server/service/ServiceOperationTemplate.java");
        assertTrue(template.contains("RtsStorageTickService.INSTANCE.alert(player.getUUID())"));
        assertFalse(template.contains("RtsStorageTickService.INSTANCE.forceRefresh("));
    }

    @Test
    void stateSyncEntrypointsMarkEffectsAndOnlyNowMethodsSend() throws IOException {
        String history = read("server/history/ServerHistoryManager.java");
        String plugin = read("server/plugin/RtsPluginService.java");
        String progression = read("server/progression/RtsProgressionManager.java");

        assertTrue(history.contains("markHistory(player.getUUID())"));
        assertTrue(history.contains("sendSyncNow(ServerPlayer player)"));
        assertTrue(plugin.contains("markPluginState(player.getUUID())"));
        assertTrue(plugin.contains("syncToPlayerNow(ServerPlayer player)"));
        assertTrue(progression.contains("markProgressionState(player.getUUID())"));
        assertTrue(progression.contains("syncToPlayerNow(ServerPlayer player)"));
    }

    @Test
    void directSessionWritesRemainOnlyAtLifecycleRecoveryAndCommitBoundary() throws IOException {
        String allServerSources = readTree(MAIN.resolve("server"));
        assertFalse(read("server/service/ServiceOperationTemplate.java")
                .contains("saveToPlayerNbt("));
        assertTrue(allServerSources.contains("class RtsProductionEffectCommitter"));
        assertTrue(allServerSources.contains("class RtsPlacedRecoveryService"));

        long directCalls;
        try (var paths = Files.walk(MAIN.resolve("server"))) {
            directCalls = paths.filter(path -> path.toString().endsWith(".java"))
                    .mapToLong(path -> count(readUnchecked(path), "saveToPlayerNbt(player, session)"))
                    .sum();
        }
        assertTrue(directCalls <= 4,
                "新增同步保存调用会绕过统一屏障；当前允许生命周期、崩溃恢复和生产提交器边界");
    }

    @Test
    void developerScenarioExportsEffectBarrierHealthCounters() throws IOException {
        String command = read("server/service/RtsDeveloperScenarioCommand.java");
        assertTrue(command.contains("effectCommittedKinds"));
        assertTrue(command.contains("effectRetryTargets"));
        assertTrue(command.contains("effectDeferredTargets"));
        assertTrue(command.contains("effectFailedTargets"));
        assertTrue(command.contains("sessionSnapshots"));
        assertTrue(command.contains("workflowSnapshots"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static String readTree(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static long count(String source, String needle) {
        long count = 0;
        int offset = 0;
        while ((offset = source.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static void assertOrdered(String source, String... needles) {
        int previous = -1;
        for (String needle : needles) {
            int current = source.indexOf(needle);
            assertTrue(current > previous, "缺少或顺序错误：" + needle);
            previous = current;
        }
    }
}
