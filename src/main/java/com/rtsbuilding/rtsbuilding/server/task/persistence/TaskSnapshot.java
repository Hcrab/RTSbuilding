package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

/**
 * 可写盘的任务完整快照。
 *
 * <p>这里不允许出现 ServerPlayer、ServerLevel、Session、Capability 或 mutable Job。
 * 类型专属数据被深复制到 payload NBT；cursor、状态、等待原因和结果计数只存在于本快照。
 * Executor 在服务器主线程中从这些普通值重新绑定世界资源。</p>
 */
public record TaskSnapshot(
        TaskId id,
        SubmissionId submissionId,
        UUID ownerId,
        String dimensionId,
        TaskType type,
        TaskLifecycleState state,
        int workflowEntryId,
        TaskWaitKey waitKey,
        long revision,
        long createdGameTime,
        long updatedGameTime,
        int totalUnits,
        int cursorUnits,
        int succeededUnits,
        int failedUnits,
        CompoundTag payload,
        RtsOperationReason reason,
        String reasonDetail) {

    /** 旧调用方/旧任务的兼容构造；没有结构化原因时明确为 UNKNOWN。 */
    public TaskSnapshot(TaskId id, SubmissionId submissionId, UUID ownerId, String dimensionId,
            TaskType type, TaskLifecycleState state, int workflowEntryId, TaskWaitKey waitKey,
            long revision, long createdGameTime, long updatedGameTime, int totalUnits,
            int cursorUnits, int succeededUnits, int failedUnits, CompoundTag payload) {
        this(id, submissionId, ownerId, dimensionId, type, state, workflowEntryId, waitKey, revision,
                createdGameTime, updatedGameTime, totalUnits, cursorUnits, succeededUnits, failedUnits,
                payload, RtsOperationReason.UNKNOWN, "");
    }

    public TaskSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(submissionId, "submissionId");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(payload, "payload");
        reason = reason == null ? RtsOperationReason.UNKNOWN : reason;
        reasonDetail = reasonDetail == null ? "" : reasonDetail;
        if (reasonDetail.length() > 1024) throw new IllegalArgumentException("reasonDetail 不能超过 1024 个字符");
        if (dimensionId.isBlank()) throw new IllegalArgumentException("dimensionId 不能为空");
        if (dimensionId.length() > 256) throw new IllegalArgumentException("dimensionId 不能超过 256 个字符");
        NbtStringLimits.requireWritable(dimensionId, "dimensionId");
        ResourceLocation dimension = ResourceLocation.tryParse(dimensionId);
        if (dimension == null || !dimension.toString().equals(dimensionId)) {
            throw new IllegalArgumentException("dimensionId 必须是规范 ResourceLocation");
        }
        if (workflowEntryId < -1) throw new IllegalArgumentException("workflowEntryId 不能小于 -1");
        if (revision < 1L) throw new IllegalArgumentException("revision 必须从 1 开始");
        if (createdGameTime < 0L || updatedGameTime < createdGameTime) {
            throw new IllegalArgumentException("游戏时间无效");
        }
        if (totalUnits < 0 || cursorUnits < 0 || succeededUnits < 0 || failedUnits < 0) {
            throw new IllegalArgumentException("任务计数不能为负数");
        }
        if (totalUnits > 0 && cursorUnits > totalUnits) {
            throw new IllegalArgumentException("cursorUnits 不能超过 totalUnits");
        }
        if ((long) succeededUnits + failedUnits > cursorUnits) {
            throw new IllegalArgumentException("结果计数不能超过已消费游标");
        }
        if (state.waiting() != (waitKey != null)) {
            throw new IllegalArgumentException("等待状态与 waitKey 必须同时出现或同时缺失");
        }
        payload = payload.copy();
    }

    /** 防止调用方通过 NBT 引用绕过 revision 与脏标记。 */
    @Override
    public CompoundTag payload() {
        return payload.copy();
    }

    /** persistence 包内部只读测量入口；调用者严禁修改返回标签。 */
    CompoundTag payloadView() {
        return payload;
    }

    public TaskSnapshot nextRevision(TaskLifecycleState nextState, TaskWaitKey nextWaitKey,
            long gameTime, int nextCursor, int nextSucceeded, int nextFailed, CompoundTag nextPayload) {
        RtsOperationReason nextReason = switch (nextState) {
            case QUEUED, RUNNING -> RtsOperationReason.UNKNOWN;
            case PAUSED -> RtsOperationReason.MANUAL_PAUSED;
            case COMPLETED -> RtsOperationReason.SUCCESS;
            case FAILED -> RtsOperationReason.EXECUTION_ERROR;
            case CANCELLED -> RtsOperationReason.CANCELLED;
            case WAITING_RESOURCE, WAITING_CHUNK, WAITING_PERSISTENCE -> reason;
        };
        String nextDetail = switch (nextState) {
            case QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED -> "";
            case WAITING_RESOURCE, WAITING_CHUNK, WAITING_PERSISTENCE -> reasonDetail;
        };
        return nextRevision(nextState, nextWaitKey, gameTime, nextCursor, nextSucceeded,
                nextFailed, nextPayload, nextReason, nextDetail);
    }

    public TaskSnapshot nextRevision(TaskLifecycleState nextState, TaskWaitKey nextWaitKey,
            long gameTime, int nextCursor, int nextSucceeded, int nextFailed,
            CompoundTag nextPayload, RtsOperationReason nextReason, String nextReasonDetail) {
        return new TaskSnapshot(id, submissionId, ownerId, dimensionId, type, nextState,
                workflowEntryId, nextWaitKey, revision + 1L, createdGameTime, gameTime,
                totalUnits, nextCursor, nextSucceeded, nextFailed, nextPayload, nextReason, nextReasonDetail);
    }
}
