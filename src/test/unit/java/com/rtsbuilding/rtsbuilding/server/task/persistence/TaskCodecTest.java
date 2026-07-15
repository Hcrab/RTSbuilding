package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskCodecTest {
    private final TaskCodec codec = new TaskCodec();

    @Test
    void fullImageRoundTripPreservesTaskPayloadTombstoneAndMigrationLedger() {
        TaskSnapshot task = TaskStoreTest.snapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), 31,
                TaskLifecycleState.WAITING_RESOURCE,
                new TaskWaitKey("item", "minecraft:stone"), 4L, "minecraft:overworld");
        TaskTombstone tombstone = new TaskTombstone(
                TaskId.create(), 7L, TaskLifecycleState.COMPLETED, 200L);
        TaskRepository.Image source = new TaskRepository.Image(
                Map.of(task.id(), task), Map.of(tombstone.taskId(), tombstone), Set.of("session-jobs-v1"));

        TaskRepository.Image decoded = codec.decodeImage(codec.encodeImage(source));

        assertEquals(source, decoded);
    }

    @Test
    void payloadIsDeepCopiedAtConstructionAndReadBoundary() {
        CompoundTag sourcePayload = new CompoundTag();
        sourcePayload.putInt("cursor_blob", 4);
        TaskSnapshot task = new TaskSnapshot(
                TaskId.create(), SubmissionId.create(), UUID.randomUUID(), "minecraft:overworld",
                com.rtsbuilding.rtsbuilding.server.task.TaskType.MINING,
                TaskLifecycleState.RUNNING, -1, null, 1L, 0L, 0L,
                10, 0, 0, 0, sourcePayload);

        sourcePayload.putInt("cursor_blob", 99);
        CompoundTag exposed = task.payload();
        exposed.putInt("cursor_blob", 77);

        assertEquals(4, task.payload().getInt("cursor_blob"));
    }

    @Test
    void unknownOrCorruptSchemaCannotBecomeEmptyRepository() {
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("schema", TaskCodec.CURRENT_SCHEMA + 1);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(unknown));

        CompoundTag corrupt = new CompoundTag();
        corrupt.putInt("schema", TaskCodec.CURRENT_SCHEMA);
        net.minecraft.nbt.ListTag tasks = new net.minecraft.nbt.ListTag();
        CompoundTag missingIdentity = new CompoundTag();
        missingIdentity.putString("type", "PLACEMENT");
        tasks.add(missingIdentity);
        corrupt.put("tasks", tasks);
        assertThrows(TaskCodec.TaskCodecException.class, () -> codec.decodeImage(corrupt));
    }
}
