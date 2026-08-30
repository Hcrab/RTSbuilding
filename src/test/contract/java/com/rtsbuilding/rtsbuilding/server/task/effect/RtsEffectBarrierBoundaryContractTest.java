package com.rtsbuilding.rtsbuilding.server.task.effect;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 防止提交屏障退化为隐藏的世界或物品事务执行器。 */
class RtsEffectBarrierBoundaryContractTest {
    private static final Path EFFECT_PACKAGE = Path.of(
            "src/main/java/com/rtsbuilding/rtsbuilding/server/task/effect");

    @Test
    void barrierPackageContainsNoGameplayTransactionTypes() throws IOException {
        String sources = readFoundationSources();
        assertFalse(sources.contains("net.minecraft.world.item.ItemStack"));
        assertFalse(sources.contains("net.minecraft.server.level.ServerLevel"));
        assertFalse(sources.contains("net.minecraft.server.level.ServerPlayer"));
        assertFalse(sources.contains("net.neoforged.neoforge.items.IItemHandler"));
        assertFalse(sources.contains("net.neoforged.neoforge.capabilities"));
        assertFalse(sources.contains("java.lang.Runnable"));
    }

    @Test
    void statePacketsAreExplicitlyRestrictedToIdempotentProjection() throws IOException {
        String kinds = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectKind.java"));
        assertTrue(kinds.contains("PLUGIN_STATE_SNAPSHOT"));
        assertFalse(kinds.contains("CLIENT_STATE_PACKET"));
        assertTrue(kinds.contains("不适用于有顺序语义、增量语义或游戏事务语义的数据包"));
        assertTrue(kinds.contains("其他包必须先建立自己的"));
        assertTrue(kinds.contains("明确投影类型"));
        assertTrue(kinds.contains("完整快照成功进入 DataCluster 后确认"));
        assertTrue(kinds.contains("物理刷盘失败也不会清除 DataCluster 的 dirty 状态"));
    }

    @Test
    void failurePathRestoresUnacknowledgedEffectsInsteadOfClearingThem() throws IOException {
        String barrier = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectCommitBarrier.java"));
        String ledger = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectLedger.java"));
        assertTrue(barrier.contains("entry.effects().minus(acknowledged)"));
        assertTrue(barrier.contains("retry.put(entry.key(), unresolved)"));
        assertTrue(ledger.contains("mergePending(retry)"));
        assertTrue(ledger.contains("pending.merge(entry.getKey(), effects, RtsEffectReducer::reduce)"));
    }

    @Test
    void commitBarrierHasBothTargetAndNanosecondBudgets() throws IOException {
        String budget = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectCommitBudget.java"));
        String barrier = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectCommitBarrier.java"));
        String ledger = Files.readString(EFFECT_PACKAGE.resolve("RtsEffectLedger.java"));

        assertTrue(budget.contains("int maxTargets"));
        assertTrue(budget.contains("long maxNanos"));
        assertTrue(barrier.contains("nanoTime.getAsLong() - startNanos >= budget.maxNanos()"));
        assertTrue(barrier.contains("attemptedTargets < eligibleTargets"));
        assertTrue(ledger.contains("new ArrayList<>(1)"));
        assertFalse(ledger.substring(ledger.indexOf("beginCommit"), ledger.indexOf("complete("))
                .contains("pending.clear()"));
    }

    @Test
    void developerEffectMetricsUseIncrementalCountersInsteadOfObjectGraphScans() throws IOException {
        String metrics = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsDeveloperMetrics.java"));
        assertTrue(metrics.contains("recordEffectCommit"));
        assertTrue(metrics.contains("effectCommittedKinds"));
        assertTrue(metrics.contains("effectDeferredTargets"));
        assertFalse(metrics.contains("CompoundTag"));
    }

    private static String readFoundationSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (var paths = Files.list(EFFECT_PACKAGE)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().equals("RtsProductionEffectCommitter.java"))
                    .toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }
}
