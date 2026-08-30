package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.UUID;

/**
 * 可持久放置任务的纯值载荷。
 *
 * <p>这里只保存稳定 owner、维度 ID、workflow ID 和防御性复制的放置状态。
 * 它明确不持有 ServerPlayer、Level 实例、Session、Capability 或 mutable PlaceBatchJob；
 * executor 必须在服务端主线程的单个 slice 中重新解析这些运行时资源。</p>
 */
public record PlacementTaskPayload(
        UUID ownerId,
        ResourceKey<Level> dimension,
        int workflowEntryId,
        PlacementTaskState state) implements TaskPayload {

    public PlacementTaskPayload {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(state, "state");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("payload 与 placement state 的 workflowEntryId 不一致");
        }
    }

    /** 用 slice 返回的新 snapshot 创建下一版 payload；稳定身份保持不变。 */
    public PlacementTaskPayload withState(PlacementTaskState nextState) {
        return new PlacementTaskPayload(ownerId, dimension, workflowEntryId, nextState);
    }
}
