package com.rtsbuilding.rtsbuilding.server.task.effect;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsEffectLedgerTest {
    private static TestTarget dimensionTarget(int id) {
        return new TestTarget(id, RtsEffectScope.PLAYER_DIMENSION);
    }

    private static TestTarget globalTarget(int id) {
        return new TestTarget(id, RtsEffectScope.PLAYER_GLOBAL);
    }

    @Test
    void reducerCoalescesRepeatedTypedEffectsWithoutAllocatingMoreTargets() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        TestTarget player = dimensionTarget(1);

        for (int i = 0; i < 50_000; i++) {
            ledger.mark(player, RtsEffectKind.STORAGE_VIEW_DIRTY);
        }
        ledger.mark(player, RtsEffectKind.WORKFLOW_SNAPSHOT);

        assertEquals(1, ledger.pendingTargetCount());
        RtsEffectLedgerMetrics metrics = ledger.snapshotMetrics();
        assertEquals(50_001, metrics.markedKinds());
        assertEquals(49_999, metrics.coalescedKinds());
        assertEquals(1, metrics.peakPendingTargets());
    }

    @Test
    void oneTargetIsCommittedAtMostOncePerTickAndNewMarksWaitForNextTick() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget player = dimensionTarget(1);
        List<RtsEffectSet> seen = new ArrayList<>();
        ledger.mark(player, RtsEffectKind.STORAGE_VIEW_DIRTY);

        var first = barrier.commit(100L, (key, effects) -> {
            seen.add(effects);
            // 即使新标记与租约中的 kind 相同，也不能被旧租约的 ACK 一并清掉。
            ledger.mark(key, RtsEffectKind.STORAGE_VIEW_DIRTY);
            ledger.mark(key, RtsEffectKind.WORKFLOW_SNAPSHOT);
            return RtsEffectCommitResult.all(effects);
        });
        var duplicate = barrier.commit(100L, (key, effects) -> {
            throw new AssertionError("同一 Tick 不得第二次进入提交器");
        });

        assertEquals(1, first.attemptedTargets());
        assertTrue(duplicate.skipped());
        assertEquals(1, ledger.pendingTargetCount());

        barrier.commit(101L, (key, effects) -> {
            seen.add(effects);
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(2, seen.size());
        assertTrue(seen.get(0).contains(RtsEffectKind.STORAGE_VIEW_DIRTY));
        assertFalse(seen.get(0).contains(RtsEffectKind.WORKFLOW_SNAPSHOT));
        assertTrue(seen.get(1).contains(RtsEffectKind.STORAGE_VIEW_DIRTY));
        assertTrue(seen.get(1).contains(RtsEffectKind.WORKFLOW_SNAPSHOT));
        assertEquals(0, ledger.pendingTargetCount());
    }

    @Test
    void thrownCommitRetainsAllEffectsForTheNextTick() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget player = globalTarget(1);
        RtsEffectSet requested = RtsEffectSet.of(
                RtsEffectKind.SESSION_PERSISTENCE,
                RtsEffectKind.HISTORY_SNAPSHOT);
        ledger.mark(player, requested);

        var failed = barrier.commit(7L, (key, effects) -> {
            throw new IllegalStateException("故障注入");
        });

        assertEquals(1, failed.failedTargets());
        assertEquals(1, failed.retryTargets());
        assertEquals(1, ledger.pendingTargetCount());

        List<RtsEffectSet> retried = new ArrayList<>();
        var recovered = barrier.commit(8L, (key, effects) -> {
            retried.add(effects);
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(List.of(requested), retried);
        assertEquals(2, recovered.committedKinds());
        assertEquals(0, ledger.pendingTargetCount());
    }

    @Test
    void nullCommitResultIsAFullFailureAndNeverAnImplicitAck() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget player = globalTarget(1);
        ledger.mark(player, RtsEffectKind.SESSION_PERSISTENCE);

        var failed = barrier.commit(10L, (key, effects) -> null);

        assertEquals(1, failed.failedTargets());
        assertEquals(1, failed.retryTargets());
        assertEquals(1, ledger.pendingTargetCount());
        barrier.commit(11L, (key, effects) -> RtsEffectCommitResult.all(effects));
        assertEquals(0, ledger.pendingTargetCount());
    }

    @Test
    void partialAckRetriesOnlyTheUncommittedKind() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget player = globalTarget(1);
        ledger.mark(player, RtsEffectSet.of(
                RtsEffectKind.SESSION_PERSISTENCE,
                RtsEffectKind.HISTORY_SNAPSHOT,
                RtsEffectKind.PROGRESSION_STATE_SNAPSHOT));

        var partial = barrier.commit(20L, (key, effects) ->
                new RtsEffectCommitResult(RtsEffectSet.of(
                        RtsEffectKind.HISTORY_SNAPSHOT,
                        RtsEffectKind.PROGRESSION_STATE_SNAPSHOT)));

        assertEquals(2, partial.committedKinds());
        assertEquals(1, partial.retryTargets());

        List<RtsEffectSet> retried = new ArrayList<>();
        barrier.commit(21L, (key, effects) -> {
            retried.add(effects);
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(1, retried.size());
        assertEquals(RtsEffectSet.of(RtsEffectKind.SESSION_PERSISTENCE), retried.getFirst());
    }

    @Test
    void multiplePlayersEachProduceOneReducedCommit() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget first = dimensionTarget(1);
        TestTarget second = dimensionTarget(2);
        ledger.mark(first, RtsEffectKind.STORAGE_VIEW_DIRTY);
        ledger.mark(first, RtsEffectKind.STORAGE_VIEW_DIRTY);
        ledger.mark(second, RtsEffectKind.STORAGE_VIEW_DIRTY);
        AtomicInteger calls = new AtomicInteger();

        var report = barrier.commit(30L, (key, effects) -> {
            calls.incrementAndGet();
            return RtsEffectCommitResult.all(effects);
        });

        assertEquals(2, calls.get());
        assertEquals(2, report.attemptedTargets());
        assertEquals(2, report.committedTargets());
    }

    @Test
    void metricsAreUpdatedWithoutScanningCommittedEntries() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger);
        TestTarget player = dimensionTarget(1);
        ledger.mark(player, RtsEffectKind.STORAGE_VIEW_DIRTY);
        ledger.mark(player, RtsEffectKind.STORAGE_VIEW_DIRTY);
        ledger.mark(player, RtsEffectKind.WORKFLOW_SNAPSHOT);

        barrier.commit(40L, (key, effects) ->
                new RtsEffectCommitResult(RtsEffectSet.of(RtsEffectKind.STORAGE_VIEW_DIRTY)));
        RtsEffectLedgerMetrics afterPartial = ledger.snapshotMetrics();
        assertEquals(3, afterPartial.markedKinds());
        assertEquals(1, afterPartial.coalescedKinds());
        assertEquals(1, afterPartial.leasedTargets());
        assertEquals(1, afterPartial.committedKinds());
        assertEquals(1, afterPartial.retriedTargets());
        assertEquals(1, afterPartial.retriedKinds());
        assertEquals(1, afterPartial.pendingTargets());

        barrier.commit(41L, (key, effects) -> RtsEffectCommitResult.all(effects));
        RtsEffectLedgerMetrics completed = ledger.snapshotMetrics();
        assertEquals(2, completed.leasedTargets());
        assertEquals(2, completed.committedKinds());
        assertEquals(0, completed.pendingTargets());
    }

    @Test
    void commitLimitLeavesUnleasedTargetsPendingInInsertionOrder() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger, 2);
        for (int key = 0; key < 5; key++) {
            ledger.mark(dimensionTarget(key), RtsEffectKind.STORAGE_VIEW_DIRTY);
        }
        List<Integer> committed = new ArrayList<>();

        var first = barrier.commit(50L, (key, effects) -> {
            committed.add(key.id());
            return RtsEffectCommitResult.all(effects);
        });

        assertEquals(List.of(0, 1), committed);
        assertEquals(2, first.attemptedTargets());
        assertEquals(3, first.pendingTargetsAfterCommit());

        barrier.commit(51L, (key, effects) -> {
            committed.add(key.id());
            return RtsEffectCommitResult.all(effects);
        });
        barrier.commit(52L, (key, effects) -> {
            committed.add(key.id());
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(List.of(0, 1, 2, 3, 4), committed);
        assertEquals(0, ledger.pendingTargetCount());
    }

    @Test
    void persistentFailuresDoNotStarveTargetsThatWereAlreadyWaiting() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(ledger, 2);
        for (int key = 0; key < 4; key++) {
            ledger.mark(globalTarget(key), RtsEffectKind.SESSION_PERSISTENCE);
        }
        List<Integer> attempts = new ArrayList<>();

        barrier.commit(60L, (key, effects) -> {
            attempts.add(key.id());
            throw new IllegalStateException("持续故障");
        });
        barrier.commit(61L, (key, effects) -> {
            attempts.add(key.id());
            return RtsEffectCommitResult.all(effects);
        });

        // 失败的 0、1 会回到队尾；已经等待的 2、3 必须先获得提交机会。
        assertEquals(List.of(0, 1, 2, 3), attempts);
        assertEquals(2, ledger.pendingTargetCount());
    }

    @Test
    void nanoBudgetStopsBetweenTargetsAndDefersTheRestWithoutCallingCommitter() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        AtomicLong clock = new AtomicLong();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(
                ledger, new RtsEffectCommitBudget(8, 100L), clock::get);
        for (int key = 0; key < 3; key++) {
            ledger.mark(dimensionTarget(key), RtsEffectKind.STORAGE_VIEW_DIRTY);
        }
        List<Integer> attempts = new ArrayList<>();

        var first = barrier.commit(70L, (key, effects) -> {
            attempts.add(key.id());
            clock.set(100L);
            return RtsEffectCommitResult.all(effects);
        });

        assertEquals(List.of(0), attempts);
        assertEquals(1, first.attemptedTargets());
        assertEquals(2, first.deferredTargets());
        assertEquals(2, first.pendingTargetsAfterCommit());
        assertEquals(2, ledger.snapshotMetrics().deferredTargets());

        barrier.commit(71L, (key, effects) -> {
            attempts.add(key.id());
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(List.of(0, 1, 2), attempts);
        assertEquals(0, ledger.pendingTargetCount());
    }

    @Test
    void timeConsumingFailureMovesBehindTargetsDeferredByTheNanoBudget() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        AtomicLong clock = new AtomicLong();
        RtsEffectCommitBarrier<TestTarget> barrier = new RtsEffectCommitBarrier<>(
                ledger, new RtsEffectCommitBudget(8, 100L), clock::get);
        for (int key = 0; key < 3; key++) {
            ledger.mark(globalTarget(key), RtsEffectKind.SESSION_PERSISTENCE);
        }
        List<Integer> attempts = new ArrayList<>();

        barrier.commit(80L, (key, effects) -> {
            attempts.add(key.id());
            clock.addAndGet(100L);
            throw new IllegalStateException("持续故障");
        });
        barrier.commit(81L, (key, effects) -> {
            attempts.add(key.id());
            clock.addAndGet(100L);
            return RtsEffectCommitResult.all(effects);
        });
        barrier.commit(82L, (key, effects) -> {
            attempts.add(key.id());
            clock.addAndGet(100L);
            return RtsEffectCommitResult.all(effects);
        });

        assertEquals(List.of(0, 1, 2), attempts);
        assertEquals(1, ledger.pendingTargetCount());
    }

    @Test
    void commitLimitMustBePositive() {
        RtsEffectLedger<TestTarget> ledger = new RtsEffectLedger<>();
        assertThrows(IllegalArgumentException.class, () -> new RtsEffectCommitBarrier<>(ledger, 0));
    }

    @Test
    void playerEffectTargetSeparatesGlobalAndDimensionScopedEffects() {
        UUID player = UUID.randomUUID();
        RtsPlayerEffectTarget global = RtsPlayerEffectTarget.global(player);
        RtsPlayerEffectTarget overworld = RtsPlayerEffectTarget.inDimension(player, "minecraft:overworld");

        assertTrue(global.isGlobal());
        assertFalse(overworld.isGlobal());
        assertFalse(global.equals(overworld));
        assertThrows(IllegalArgumentException.class,
                () -> RtsPlayerEffectTarget.inDimension(player, " "));
    }

    @Test
    void ledgerRejectsEffectsFromTheWrongScope() {
        RtsEffectLedger<RtsPlayerEffectTarget> ledger = new RtsEffectLedger<>();
        UUID player = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> ledger.mark(
                RtsPlayerEffectTarget.global(player), RtsEffectKind.STORAGE_VIEW_DIRTY));
        assertThrows(IllegalArgumentException.class, () -> ledger.mark(
                RtsPlayerEffectTarget.inDimension(player, "minecraft:overworld"),
                RtsEffectKind.SESSION_PERSISTENCE));
    }

    @Test
    void playerLifecycleCleanupDoesNotDiscardAnotherPlayersEffects() {
        RtsEffectLedger<RtsPlayerEffectTarget> ledger = new RtsEffectLedger<>();
        UUID leavingPlayer = UUID.randomUUID();
        UUID remainingPlayer = UUID.randomUUID();
        ledger.mark(RtsPlayerEffectTarget.global(leavingPlayer),
                RtsEffectKind.SESSION_PERSISTENCE);
        ledger.mark(RtsPlayerEffectTarget.inDimension(leavingPlayer, "minecraft:overworld"),
                RtsEffectKind.STORAGE_VIEW_DIRTY);
        ledger.mark(RtsPlayerEffectTarget.global(remainingPlayer),
                RtsEffectKind.HISTORY_SNAPSHOT);

        int discarded = ledger.discardMatching(target ->
                target.playerId().equals(leavingPlayer));

        assertEquals(2, discarded);
        assertEquals(1, ledger.pendingTargetCount());
        List<RtsPlayerEffectTarget> committed = new ArrayList<>();
        new RtsEffectCommitBarrier<>(ledger).commit(90L, (target, effects) -> {
            committed.add(target);
            return RtsEffectCommitResult.all(effects);
        });
        assertEquals(List.of(RtsPlayerEffectTarget.global(remainingPlayer)), committed);
    }

    @Test
    void dimensionCleanupPreservesGlobalAndNewDimensionEffects() {
        RtsEffectLedger<RtsPlayerEffectTarget> ledger = new RtsEffectLedger<>();
        UUID player = UUID.randomUUID();
        ledger.mark(RtsPlayerEffectTarget.global(player),
                RtsEffectKind.SESSION_PERSISTENCE);
        ledger.mark(RtsPlayerEffectTarget.inDimension(player, "minecraft:overworld"),
                RtsEffectKind.WORKFLOW_SNAPSHOT);
        ledger.mark(RtsPlayerEffectTarget.inDimension(player, "minecraft:the_nether"),
                RtsEffectKind.STORAGE_VIEW_DIRTY);

        int discarded = ledger.discardMatching(target -> target.playerId().equals(player)
                && !target.isGlobal()
                && target.dimensionId().equals("minecraft:overworld"));

        assertEquals(1, discarded);
        assertEquals(2, ledger.pendingTargetCount());
        assertEquals(2, ledger.pendingKindCount());
    }

    private record TestTarget(int id, RtsEffectScope scope) implements RtsEffectTarget {
    }
}
