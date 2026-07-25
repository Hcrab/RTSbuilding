package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStoreTest {

    @Test
    void duplicateSubmissionReturnsOriginalTaskInsteadOfCreatingSecondRuntime() {
        TaskStore store = new TaskStore();
        UUID owner = UUID.randomUUID();
        SubmissionId submission = SubmissionId.create();
        TaskSnapshot original = snapshot(TaskId.create(), submission, owner, 9,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        TaskSnapshot retriedPacket = snapshot(TaskId.create(), submission, owner, 9,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");

        assertTrue(store.submit(original).inserted());
        TaskAdmissionResult retry = store.submit(retriedPacket);

        assertFalse(retry.inserted());
        assertEquals(original.id(), retry.snapshot().id());
        assertEquals(1, store.size());
        assertEquals(original.id(), store.findBySubmission(owner, submission).orElseThrow().id());
        assertEquals(original.id(),
                store.findByWorkflow(owner, "minecraft:overworld", 9).orElseThrow().id());
    }

    @Test
    void inspectAdmissionUsesIndexesWithoutMutatingStore() {
        TaskStore store = new TaskStore();
        UUID owner = UUID.randomUUID();
        TaskSnapshot candidate = snapshot(TaskId.create(), SubmissionId.create(), owner, 12,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");

        TaskAdmissionResult inspection = store.inspectAdmission(candidate);

        assertTrue(inspection.inserted());
        assertEquals(0, store.size());
        assertTrue(store.findBySubmission(owner, candidate.submissionId()).isEmpty());
        assertTrue(store.findByWorkflow(owner, candidate.dimensionId(), 12).isEmpty());
    }

    @Test
    void replacingOneTaskMovesOnlyItsIncrementalWaitIndex() {
        TaskStore store = new TaskStore();
        UUID owner = UUID.randomUUID();
        TaskWaitKey itemWait = new TaskWaitKey("item", "minecraft:oak_log");
        TaskSnapshot waiting = snapshot(TaskId.create(), SubmissionId.create(), owner, 3,
                TaskLifecycleState.WAITING_RESOURCE, itemWait, 1L, "minecraft:overworld");
        TaskSnapshot sibling = snapshot(TaskId.create(), SubmissionId.create(), owner, 4,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        store.submit(waiting);
        store.submit(sibling);

        TaskSnapshot resumed = snapshot(waiting.id(), waiting.submissionId(), owner, 3,
                TaskLifecycleState.RUNNING, null, 2L, "minecraft:overworld");
        store.replace(resumed);

        assertTrue(store.waitingFor(itemWait).isEmpty());
        assertEquals(2, store.inDimension("minecraft:overworld").size());
        assertEquals(2, store.runnableFor(owner, "minecraft:overworld").size());
        assertEquals(2, store.ownedBy(owner).size());

        TaskSnapshot illegalDimensionMove = snapshot(resumed.id(), resumed.submissionId(), owner, 3,
                TaskLifecycleState.RUNNING, null, 3L, "minecraft:the_nether");
        assertThrows(IllegalArgumentException.class, () -> store.replace(illegalDimensionMove));
    }

    @Test
    void workflowCollisionAndStaleRevisionFailClosed() {
        TaskStore store = new TaskStore();
        UUID owner = UUID.randomUUID();
        TaskSnapshot first = snapshot(TaskId.create(), SubmissionId.create(), owner, 21,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld");
        store.submit(first);

        assertThrows(IllegalStateException.class, () -> store.submit(snapshot(
                TaskId.create(), SubmissionId.create(), owner, 21,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:overworld")));
        assertThrows(IllegalArgumentException.class, () -> store.replace(first));

        TaskSnapshot sameWorkflowOtherDimension = snapshot(
                TaskId.create(), SubmissionId.create(), owner, 21,
                TaskLifecycleState.QUEUED, null, 1L, "minecraft:the_nether");
        assertTrue(store.submit(sameWorkflowOtherDimension).inserted(),
                "workflow key 必须包含 dimension");
    }

    static TaskSnapshot snapshot(TaskId id, SubmissionId submission, UUID owner, int workflow,
            TaskLifecycleState state, TaskWaitKey waitKey, long revision, String dimension) {
        CompoundTag payload = new CompoundTag();
        payload.putString("plan_ref", "fixture-plan");
        return new TaskSnapshot(id, submission, owner, dimension, TaskType.PLACEMENT,
                state, workflow, waitKey, revision, 10L, 10L + revision,
                20, 2, 2, 0, payload);
    }
}
