package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.mining.MiningTaskState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/** 只含稳定 ID、维度键和纯值 snapshot 的挖掘任务载荷。 */
public record MiningTaskPayload(
        UUID ownerId,
        ResourceKey<Level> dimension,
        int workflowEntryId,
        MiningTaskState state) implements TaskPayload {

    public MiningTaskPayload {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(state, "state");
        if (workflowEntryId < -1 || workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("mining workflow 身份无效或漂移");
        }
    }

    public MiningTaskPayload withState(MiningTaskState nextState) {
        return new MiningTaskPayload(ownerId, dimension, workflowEntryId, nextState);
    }
}
