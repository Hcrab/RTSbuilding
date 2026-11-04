package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskCodec;
import com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskState;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskLifecycleState;
import com.rtsbuilding.rtsbuilding.server.task.persistence.TaskSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RtsDurableTaskExecutionRuntimeTimeRollbackTest {
    @Test
    void destructionCanPauseResumeAndCancelAfterWorldTimeRollback() {
        RtsDurableTaskExecutionRuntime runtime = new RtsDurableTaskExecutionRuntime();
        UUID owner = UUID.randomUUID();
        List<BlockPos> targets = List.of(BlockPos.ZERO, new BlockPos(1, 0, 0), new BlockPos(2, 0, 0));
        DestructionTaskState state = new DestructionTaskState(
                targets, (byte) 2, true, true, 17, 2, 1, 1, List.of(targets.get(0)), List.of());
        var payload = DestructionTaskCodec.encode(new DestructionTaskPayload(owner, Level.OVERWORLD, 17, state));
        TaskSnapshot original = new TaskSnapshot(TaskId.create(), SubmissionId.create(), owner,
                "minecraft:overworld", TaskType.DESTRUCTION, TaskLifecycleState.RUNNING, 17, null,
                4L, 100L, 120L, targets.size(), 2, 1, 1, payload);

        TaskSnapshot paused = runtime.transitionDestructionSnapshot(original, TaskLifecycleState.PAUSED, 10L);
        TaskSnapshot resumed = runtime.transitionDestructionSnapshot(paused, TaskLifecycleState.QUEUED, 11L);
        TaskSnapshot cancelled = runtime.transitionDestructionSnapshot(
                resumed, TaskLifecycleState.CANCELLED, 12L, RtsOperationReason.CANCELLED, "requested");

        assertEquals(expected(original, TaskLifecycleState.PAUSED, 5L, RtsOperationReason.MANUAL_PAUSED, ""), paused);
        assertEquals(expected(original, TaskLifecycleState.QUEUED, 6L, RtsOperationReason.UNKNOWN, ""), resumed);
        assertEquals(expected(original, TaskLifecycleState.CANCELLED, 7L, RtsOperationReason.CANCELLED, "requested"),
                cancelled);
    }

    private static TaskSnapshot expected(TaskSnapshot original, TaskLifecycleState lifecycle, long revision,
            RtsOperationReason reason, String detail) {
        return new TaskSnapshot(original.id(), original.submissionId(), original.ownerId(),
                original.dimensionId(), original.type(), lifecycle, original.workflowEntryId(), null, revision,
                original.createdGameTime(), original.updatedGameTime(), original.totalUnits(), original.cursorUnits(),
                original.succeededUnits(), original.failedUnits(), original.payload(), reason, detail);
    }
}
