package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskAssetManifestTest {

    @Test
    void totalsAreAuthoritativeAndRejectAdmissionBeyondCompressedOrLogicalQuota() {
        TaskAssetMetadata compressedA = metadata(TaskId.create(),
                TaskAssetManifest.MAX_COMPRESSED_BYTES / 2L + 1L, 1L);
        TaskAssetMetadata compressedB = metadata(TaskId.create(),
                TaskAssetManifest.MAX_COMPRESSED_BYTES / 2L + 1L, 1L);
        assertThrows(IllegalArgumentException.class, () -> new TaskAssetManifest(Map.of(
                compressedA.assetId(), compressedA, compressedB.assetId(), compressedB)));

        TaskAssetMetadata logicalA = metadata(TaskId.create(),
                1L, TaskAssetManifest.MAX_LOGICAL_BYTES / 2L + 1L);
        TaskAssetMetadata logicalB = metadata(TaskId.create(),
                1L, TaskAssetManifest.MAX_LOGICAL_BYTES / 2L + 1L);
        assertThrows(IllegalArgumentException.class, () -> new TaskAssetManifest(Map.of(
                logicalA.assetId(), logicalA, logicalB.assetId(), logicalB)));
    }

    @Test
    void writeOnceMetadataCannotMutateAndRemovePlusUpsertCannotShareId() {
        TaskAssetMetadata first = metadata(TaskId.create(), 10L, 20L);
        TaskAssetManifest manifest = new TaskAssetManifest(Map.of(first.assetId(), first));
        TaskAssetMetadata conflicting = new TaskAssetMetadata(
                first.assetId(), first.taskId(), first.kind(), "b".repeat(64), 10L, 20L);

        assertThrows(IllegalArgumentException.class,
                () -> manifest.apply(List.of(conflicting), Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> manifest.apply(List.of(first), Set.of(first.assetId())));
        assertEquals(10L, manifest.compressedBytes());
        assertEquals(20L, manifest.logicalBytes());
    }

    private static TaskAssetMetadata metadata(TaskId taskId, long compressed, long logical) {
        return new TaskAssetMetadata(TaskAssetId.forTask(taskId, "blueprint"), taskId,
                "blueprint", "a".repeat(64), compressed, logical);
    }
}
