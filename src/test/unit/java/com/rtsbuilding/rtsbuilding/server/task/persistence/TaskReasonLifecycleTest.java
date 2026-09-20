package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** durable snapshot 的原因字段兼容旧任务，并在生命周期切换时清理过期详情。 */
class TaskReasonLifecycleTest {
    private final TaskCodec codec = new TaskCodec();

    @Test
    void reasonDetailRoundTripAndLegacyMissingFieldsAreSafe() {
        TaskSnapshot waiting = snapshot(
                TaskLifecycleState.WAITING_RESOURCE,
                RtsOperationReason.RESOURCE_MISSING, "missing:stone");

        TaskSnapshot decoded = codec.decodeSnapshot(codec.encodeSnapshot(waiting));
        assertEquals(RtsOperationReason.RESOURCE_MISSING, decoded.reason());
        assertEquals("missing:stone", decoded.reasonDetail());

        CompoundTag legacy = codec.encodeSnapshot(waiting);
        legacy.remove("reason_id");
        legacy.remove("reason_detail");
        TaskSnapshot legacyDecoded = codec.decodeSnapshot(legacy);
        assertEquals(RtsOperationReason.UNKNOWN, legacyDecoded.reason());
        assertEquals("", legacyDecoded.reasonDetail());

        CompoundTag future = codec.encodeSnapshot(waiting);
        future.putInt("reason_id", 9999);
        assertEquals(RtsOperationReason.UNKNOWN, codec.decodeSnapshot(future).reason());
    }

    @Test
    void queuedPausedAndCancelledTransitionsDoNotKeepOldNeedItems() {
        TaskSnapshot waiting = snapshot(
                TaskLifecycleState.WAITING_RESOURCE,
                RtsOperationReason.RESOURCE_MISSING, "missing:stone");

        TaskSnapshot queued = waiting.nextRevision(
                TaskLifecycleState.QUEUED, null, 2L, 2, 1, 0, new CompoundTag());
        assertEquals(RtsOperationReason.UNKNOWN, queued.reason());
        assertEquals("", queued.reasonDetail());

        TaskSnapshot paused = waiting.nextRevision(
                TaskLifecycleState.PAUSED, null, 2L, 2, 1, 0, new CompoundTag());
        assertEquals(RtsOperationReason.MANUAL_PAUSED, paused.reason());
        assertEquals("", paused.reasonDetail());

        TaskSnapshot replaced = waiting.nextRevision(
                TaskLifecycleState.CANCELLED, null, 2L, 2, 1, 0, new CompoundTag(),
                RtsOperationReason.REPLACED, "replaced_by_newer_workflow");
        assertEquals(RtsOperationReason.REPLACED, replaced.reason());
        assertEquals("replaced_by_newer_workflow", replaced.reasonDetail());

        TaskSnapshot chunk = waiting.nextRevision(
                TaskLifecycleState.WAITING_CHUNK,
                new TaskWaitKey("chunk", "minecraft:overworld"), 2L, 2, 1, 0,
                new CompoundTag(), RtsOperationReason.CHUNK_UNLOADED, "chunk_unloaded");
        assertEquals(RtsOperationReason.CHUNK_UNLOADED, chunk.reason());
        assertEquals("chunk_unloaded", chunk.reasonDetail());

        TaskSnapshot failed = waiting.nextRevision(
                TaskLifecycleState.FAILED, null, 2L, 2, 1, 0, new CompoundTag());
        assertEquals(RtsOperationReason.EXECUTION_ERROR, failed.reason());
        assertEquals("", failed.reasonDetail());

        TaskSnapshot completed = waiting.nextRevision(
                TaskLifecycleState.COMPLETED, null, 2L, 2, 1, 0, new CompoundTag());
        assertEquals(RtsOperationReason.SUCCESS, completed.reason());
        assertEquals("", completed.reasonDetail());
    }

    private static TaskSnapshot snapshot(
            TaskLifecycleState state, RtsOperationReason reason, String detail) {
        return new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                TaskType.PLACEMENT, state, 17,
                state.waiting() ? new TaskWaitKey("item", "minecraft:stone") : null,
                1L, 0L, 1L, 10, 2, 1, 0, new CompoundTag(), reason, detail);
    }
}
