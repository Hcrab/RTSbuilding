package com.rtsbuilding.rtsbuilding.server.task.persistence.asset;

import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TaskAssetIdTest {

    @Test
    void idIsStablePerTaskAndKindWithoutCrossTaskSharing() {
        TaskId first = TaskId.create();
        TaskId second = TaskId.create();

        assertEquals(TaskAssetId.forTask(first, "blueprint"),
                TaskAssetId.forTask(first, "blueprint"));
        assertNotEquals(TaskAssetId.forTask(first, "blueprint"),
                TaskAssetId.forTask(second, "blueprint"));
        assertNotEquals(TaskAssetId.forTask(first, "blueprint"),
                TaskAssetId.forTask(first, "other"));
    }
}
