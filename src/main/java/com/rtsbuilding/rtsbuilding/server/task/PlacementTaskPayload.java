package com.rtsbuilding.rtsbuilding.server.task;

import com.rtsbuilding.rtsbuilding.server.task.placement.PlacementTaskState;

import java.util.Objects;
import java.util.UUID;

/**
 * 可持久放置任务的纯值载荷。
 *
 * <p>这里只保存稳定 owner、维度 ID、workflow ID 和防御性复制的放置状态。
 * 它明确不持有 EntityPlayerMP、Level 实例、Session、Capability 或 mutable PlaceBatchJob；
 * executor 必须在服务端主线程的单个 slice 中重新解析这些运行时资源。</p>
 */
public final class PlacementTaskPayload implements TaskPayload {
    private final UUID ownerId;
    private final int dimension;
    private final int workflowEntryId;
    private final PlacementTaskState state;

    public PlacementTaskPayload(UUID ownerId, int dimension, int workflowEntryId,
            PlacementTaskState state) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.dimension = dimension;
        this.state = Objects.requireNonNull(state, "state");
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (workflowEntryId != state.workflowEntryId()) {
            throw new IllegalArgumentException("payload 与 placement state 的 workflowEntryId 不一致");
        }
        this.workflowEntryId = workflowEntryId;
    }

    public UUID ownerId() { return ownerId; }
    public int dimension() { return dimension; }
    public int workflowEntryId() { return workflowEntryId; }
    public PlacementTaskState state() { return state; }

    /** 用 slice 返回的新 snapshot 创建下一版 payload；稳定身份保持不变。 */
    public PlacementTaskPayload withState(PlacementTaskState nextState) {
        return new PlacementTaskPayload(ownerId, dimension, workflowEntryId, nextState);
    }
}
