package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState;

import java.util.Objects;
import java.util.UUID;

/** 只含稳定 ID、维度键和纯值 snapshot 的挖掘任务载荷。 */
public final class MiningTaskPayload implements TaskPayload {
    private final UUID ownerId;
    private final int dimension;
    private final int workflowEntryId;
    private final MiningTaskState state;

    public MiningTaskPayload(UUID ownerId, int dimension, int workflowEntryId,
            MiningTaskState state) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.dimension = dimension;
        this.state = Objects.requireNonNull(state, "state");
        if (workflowEntryId < -1 || workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("mining workflow 身份无效或漂移");
        }
        this.workflowEntryId = workflowEntryId;
    }

    public UUID ownerId() { return ownerId; }
    public int dimension() { return dimension; }
    public int workflowEntryId() { return workflowEntryId; }
    public MiningTaskState state() { return state; }

    public MiningTaskPayload withState(MiningTaskState nextState) {
        return new MiningTaskPayload(ownerId, dimension, workflowEntryId, nextState);
    }
}
