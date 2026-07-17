package com.rtsbuilding.rtsbuilding.server.task;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurableBlueprintMigrationProtocolTest {
    @Test
    void readyLegacyProgressKeepsRemainingCursorInsteadOfPretendingComplete() {
        DurableBlueprintTaskBridge.InitialProgress progress =
                DurableBlueprintTaskBridge.initialProgress(false, 100, 37, 51, 4);

        assertEquals(63, progress.cursor());
        assertEquals(51, progress.succeeded());
        assertEquals(4, progress.failed());
    }

    @Test
    void preparingLegacyTaskRestartsPreparationWithoutInventingCursor() {
        DurableBlueprintTaskBridge.InitialProgress progress =
                DurableBlueprintTaskBridge.initialProgress(true, 100, 0, 51, 4);

        assertEquals(0, progress.cursor());
        assertEquals(0, progress.succeeded());
        assertEquals(0, progress.failed());
    }

    @Test
    void recoveryCompletionBeforeLegacyRestoreDefersInsteadOfFailingOrStartingSecondSlot() {
        assertEquals(DurableBlueprintTaskBridge.ProjectionClaimDecision.DEFER_UNCLAIMED_HEAVY,
                DurableBlueprintTaskBridge.decideProjectionClaim(
                        new CompoundTag(), UUID.randomUUID(), false));
    }

    @Test
    void legacyRestoreAfterRootAckClaimsHeavySlotThenMatchingThinRetryReusesIt() {
        UUID taskId = UUID.randomUUID();
        assertEquals(DurableBlueprintTaskBridge.ProjectionClaimDecision.CLAIM_HEAVY,
                DurableBlueprintTaskBridge.decideProjectionClaim(
                        new CompoundTag(), taskId, true));

        CompoundTag thin = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(
                thin, "durable_task_id", taskId);
        assertEquals(DurableBlueprintTaskBridge.ProjectionClaimDecision.REUSE_MATCHING_THIN,
                DurableBlueprintTaskBridge.decideProjectionClaim(thin, taskId, false));
    }

    @Test
    void conflictingThinProjectionFailsClosed() {
        CompoundTag thin = new CompoundTag();
        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(
                thin, "durable_task_id", UUID.randomUUID());

        assertEquals(DurableBlueprintTaskBridge.ProjectionClaimDecision.FAIL_CONFLICT,
                DurableBlueprintTaskBridge.decideProjectionClaim(
                        thin, UUID.randomUUID(), true));
    }
}
