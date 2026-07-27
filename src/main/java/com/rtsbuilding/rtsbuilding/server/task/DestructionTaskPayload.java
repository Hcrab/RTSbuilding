package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.destruction.DestructionTaskState;

import java.util.Objects;
import java.util.UUID;

/**
 * 可持久拆除任务的纯值载荷。
 *
 * <p>这里只保存稳定 owner、创建维度、workflow ID 和防御性复制的拆除状态。
 * 它明确不持有 EntityPlayerMP、WorldServer、Session、Capability 或 mutable DestructionJob；
 * executor 必须在服务端主线程的单个 slice 中临时解析运行时资源。</p>
 */
public final class DestructionTaskPayload implements TaskPayload {
    private final UUID ownerId;
    private final int dimension;
    private final int workflowEntryId;
    private final DestructionTaskState state;

    public DestructionTaskPayload(UUID ownerId, int dimension, int workflowEntryId,
            DestructionTaskState state) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.dimension = dimension;
        this.state = Objects.requireNonNull(state, "state");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("payload 与 destruction state 的 workflowEntryId 不一致");
        }
        this.workflowEntryId = workflowEntryId;
    }

    public UUID ownerId() { return ownerId; }
    public int dimension() { return dimension; }
    public int workflowEntryId() { return workflowEntryId; }
    public DestructionTaskState state() { return state; }
}
