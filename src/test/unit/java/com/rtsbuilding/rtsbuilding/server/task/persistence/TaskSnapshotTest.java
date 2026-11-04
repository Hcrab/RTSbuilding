package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskSnapshotTest {
    @Test
    void revisionPreservesTaskAcrossWorldTimeRollback() {
        TaskSnapshot original = snapshot(100L, 120L);

        TaskSnapshot progressed = original.nextRevision(
                TaskLifecycleState.RUNNING, null, 99L, 4, 3, 1, original.payload());
        assertEquals(120L, progressed.updatedGameTime());
        assertEquals(4, progressed.cursorUnits());
        assertEquals(3, progressed.succeededUnits());
        assertEquals(1, progressed.failedUnits());

        TaskSnapshot completed = progressed.nextRevision(
                TaskLifecycleState.COMPLETED, null, -1L, 5, 4, 1, progressed.payload(),
                RtsOperationReason.SUCCESS, "last block");
        assertEquals(new TaskSnapshot(
                original.id(), original.submissionId(), original.ownerId(), original.dimensionId(),
                original.type(), TaskLifecycleState.COMPLETED, original.workflowEntryId(), null,
                original.revision() + 2L, 100L, 120L, 5, 5, 4, 1, original.payload(),
                RtsOperationReason.SUCCESS, "last block"), completed);
    }

    @Test
    void revisionTracksForwardTimeAndRetainsSameTime() {
        TaskSnapshot original = snapshot(100L, 120L);

        TaskSnapshot advanced = original.nextRevision(
                TaskLifecycleState.RUNNING, null, 130L, 3, 2, 1, original.payload());
        TaskSnapshot unchangedTime = advanced.nextRevision(
                TaskLifecycleState.RUNNING, null, 130L, 3, 2, 1, advanced.payload());
        TaskSnapshot earlierUpdate = unchangedTime.nextRevision(
                TaskLifecycleState.RUNNING, null, 125L, 3, 2, 1, unchangedTime.payload());

        assertEquals(130L, advanced.updatedGameTime());
        assertEquals(130L, unchangedTime.updatedGameTime());
        assertEquals(130L, earlierUpdate.updatedGameTime());
        assertEquals(original.revision() + 3L, earlierUpdate.revision());
    }

    @Test
    void constructorRejectsInvalidPersistedTimes() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(-1L, 100L));
        assertThrows(IllegalArgumentException.class, () -> snapshot(100L, 99L));
    }

    private static TaskSnapshot snapshot(long createdGameTime, long updatedGameTime) {
        CompoundTag payload = new CompoundTag();
        payload.putString("plan_ref", "fixture-plan");
        return new TaskSnapshot(TaskId.create(), SubmissionId.create(), UUID.randomUUID(),
                "minecraft:overworld", TaskType.PLACEMENT, TaskLifecycleState.RUNNING, 17, null,
                4L, createdGameTime, updatedGameTime, 5, 3, 2, 1, payload);
    }
}
