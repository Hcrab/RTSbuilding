package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 调度器、投影器和调试端可见的只读任务查询面；不暴露 replace/remove 等可变入口。 */
public interface TaskQuery {
    Optional<TaskSnapshot> get(TaskId taskId);

    Optional<TaskSnapshot> findBySubmission(UUID ownerId, SubmissionId submissionId);

    Optional<TaskSnapshot> findByWorkflow(UUID ownerId, String dimensionId, int workflowEntryId);

    Optional<TaskTombstone> receipt(TaskId taskId);

    List<TaskSnapshot> ownedBy(UUID ownerId);

    List<TaskSnapshot> inDimension(String dimensionId);

    List<TaskSnapshot> waitingFor(TaskWaitKey waitKey);

    List<TaskSnapshot> runnableFor(UUID ownerId, String dimensionId);

    List<TaskSnapshot> snapshots();

    int size();
}
