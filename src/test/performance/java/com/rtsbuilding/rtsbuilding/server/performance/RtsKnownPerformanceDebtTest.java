package com.rtsbuilding.rtsbuilding.server.performance;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 已知性能债的可执行规格。
 *
 * <p>这些测试由 {@code performanceDebt} 单独运行并允许失败，目的是让每次构建都明确显示当前还欠几项，
 * 而不是用 {@code @Disabled} 把问题藏起来。修复对应生产代码后，测试会自然转绿。
 */
@Tag("known-performance-debt")
class RtsKnownPerformanceDebtTest {

    @Test
    void batchProgressMustCoalesceStorageRefreshInsteadOfForcingFullRefreshPerJob() throws Exception {
        String batchOps = readIfPresent("src/main/java/com/rtsbuilding/rtsbuilding/server/service/RtsBatchJobTickOps.java");
        String placement = readIfPresent("src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementBatch.java");
        String destruction = readIfPresent("src/main/java/com/rtsbuilding/rtsbuilding/server/service/destruction/RtsDestructionBatch.java");
        String combined = batchOps + placement + destruction;

        boolean usesDeferredRefresh = combined.contains("markDirtyDeferred")
                || combined.contains("markStorageViewDirty");
        boolean forcesRefreshInProgressPath = batchOps.contains("serviceOp().markDirty(player, session)")
                || placement.contains("RtsStorageTickService.INSTANCE.forceRefresh(player)");
        System.out.printf("[已知性能债][存储] deferred=%s, synchronousRefresh=%s%n",
                usesDeferredRefresh, forcesRefreshInProgressPath);

        assertTrue(usesDeferredRefresh, "批量作业中途进度应使用可合并的延迟刷新");
        assertFalse(forcesRefreshInProgressPath, "批量作业中途进度不能按 job 同步全量刷新存储");
    }

    @Test
    void placementTickMustNotRescanEveryTargetForProgress() throws Exception {
        String placement = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/service/placement/RtsPlacementBatch.java"));
        boolean rescansEveryTick = placement.contains("RtsProgressRefresher.refreshWorkflowProgress(player, session)")
                || placement.contains("RtsPendingPlacementService.refreshWorkflowProgress(player, session)");
        System.out.printf("[已知性能债][放置] 每 tick 全量世界进度重扫=%s%n", rescansEveryTick);
        assertFalse(rescansEveryTick, "中途进度应来自执行计数；世界全量校验必须低频或分片");
    }

    @Test
    void everySkippedDestroyTargetMustConsumeTickBudget() throws Exception {
        Path file = Path.of("src/main/java/com/rtsbuilding/rtsbuilding/server/service/destruction/RtsDestructionBatch.java");
        Assumptions.assumeTrue(Files.isRegularFile(file), "当前版本没有独立 DestructionBatch");
        String source = Files.readString(file);
        int detached = source.indexOf("tickDetachedDestructionSlice(");
        int target = source.indexOf("BlockPos target = job.next();", detached);
        int budget = source.indexOf("processed++;", target);
        int firstContinue = source.indexOf("continue;", target);
        boolean consumesBeforeAnySkip = detached >= 0 && target > detached
                && budget > target && firstContinue > budget;
        System.out.printf("[已知性能债][破坏] detached预算先于所有skip=%s%n",
                consumesBeforeAnySkip);
        assertTrue(consumesBeforeAnySkip,
                "成功、失败、无效和受保护目标都必须消耗同一 tick 检查预算");
    }

    private static String readIfPresent(String path) throws Exception {
        Path file = Path.of(path);
        return Files.isRegularFile(file) ? Files.readString(file) : "";
    }

}
