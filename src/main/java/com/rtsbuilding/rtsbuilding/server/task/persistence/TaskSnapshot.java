package com.rtsbuilding.rtsbuilding.server.task.persistence;

import com.rtsbuilding.rtsbuilding.server.task.TaskType;
import com.rtsbuilding.rtsbuilding.server.task.identity.SubmissionId;
import com.rtsbuilding.rtsbuilding.server.task.identity.TaskId;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;
import java.util.UUID;

/**
 * 可写盘的任务完整快照。
 *
 * <p>这里不允许出现 EntityPlayerMP、WorldServer、Session、Capability 或 mutable Job。
 * 类型专属数据被深复制到 payload NBT；cursor、状态、等待原因和结果计数只存在于本快照。
 * Executor 在服务器主线程中从这些普通值重新绑定世界资源。</p>
 */
public final class TaskSnapshot {
    private final TaskId id;
    private final SubmissionId submissionId;
    private final UUID ownerId;
    private final String dimensionId;
    private final TaskType type;
    private final TaskLifecycleState state;
    private final int workflowEntryId;
    private final TaskWaitKey waitKey;
    private final long revision;
    private final long createdGameTime;
    private final long updatedGameTime;
    private final int totalUnits;
    private final int cursorUnits;
    private final int succeededUnits;
    private final int failedUnits;
    private final NBTTagCompound payload;

    public TaskSnapshot(TaskId id, SubmissionId submissionId, UUID ownerId,
            String dimensionId, TaskType type, TaskLifecycleState state,
            int workflowEntryId, TaskWaitKey waitKey, long revision,
            long createdGameTime, long updatedGameTime, int totalUnits,
            int cursorUnits, int succeededUnits, int failedUnits, NBTTagCompound payload) {
        this.id = Objects.requireNonNull(id, "id");
        this.submissionId = Objects.requireNonNull(submissionId, "submissionId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.type = Objects.requireNonNull(type, "type");
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(payload, "payload");
        if (dimensionId.trim().isEmpty()) throw new IllegalArgumentException("dimensionId 不能为空");
        if (dimensionId.length() > 256) throw new IllegalArgumentException("dimensionId 不能超过 256 个字符");
        NbtStringLimits.requireWritable(dimensionId, "dimensionId");
        if (!DimensionIdCodec.isCanonical(dimensionId)) {
            throw new IllegalArgumentException("dimensionId 必须是规范整数或 ResourceLocation");
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
        this.workflowEntryId = workflowEntryId;
        this.waitKey = waitKey;
        this.revision = revision;
        this.createdGameTime = createdGameTime;
        this.updatedGameTime = updatedGameTime;
        this.totalUnits = totalUnits;
        this.cursorUnits = cursorUnits;
        this.succeededUnits = succeededUnits;
        this.failedUnits = failedUnits;
        this.payload = com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(payload);
    }

    public TaskId id() { return id; }
    public SubmissionId submissionId() { return submissionId; }
    public UUID ownerId() { return ownerId; }
    public String dimensionId() { return dimensionId; }
    public TaskType type() { return type; }
    public TaskLifecycleState state() { return state; }
    public int workflowEntryId() { return workflowEntryId; }
    public TaskWaitKey waitKey() { return waitKey; }
    public long revision() { return revision; }
    public long createdGameTime() { return createdGameTime; }
    public long updatedGameTime() { return updatedGameTime; }
    public int totalUnits() { return totalUnits; }
    public int cursorUnits() { return cursorUnits; }
    public int succeededUnits() { return succeededUnits; }
    public int failedUnits() { return failedUnits; }

    /** 防止调用方通过 NBT 引用绕过 revision 与脏标记。 */
    public NBTTagCompound payload() {
        return com.rtsbuilding.rtsbuilding.platform.nbt.NbtCompat.copyCompound(payload);
    }

    /** persistence 包内部只读测量入口；调用者严禁修改返回标签。 */
    NBTTagCompound payloadView() {
        return payload;
    }

    public TaskSnapshot nextRevision(TaskLifecycleState nextState, TaskWaitKey nextWaitKey,
            long gameTime, int nextCursor, int nextSucceeded, int nextFailed, NBTTagCompound nextPayload) {
        return new TaskSnapshot(id, submissionId, ownerId, dimensionId, type, nextState,
                workflowEntryId, nextWaitKey, revision + 1L, createdGameTime, gameTime,
                totalUnits, nextCursor, nextSucceeded, nextFailed, nextPayload);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TaskSnapshot)) return false;
        TaskSnapshot that = (TaskSnapshot) other;
        return workflowEntryId == that.workflowEntryId
                && revision == that.revision
                && createdGameTime == that.createdGameTime
                && updatedGameTime == that.updatedGameTime
                && totalUnits == that.totalUnits
                && cursorUnits == that.cursorUnits
                && succeededUnits == that.succeededUnits
                && failedUnits == that.failedUnits
                && id.equals(that.id)
                && submissionId.equals(that.submissionId)
                && ownerId.equals(that.ownerId)
                && dimensionId.equals(that.dimensionId)
                && type == that.type
                && state == that.state
                && Objects.equals(waitKey, that.waitKey)
                && payload.equals(that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, submissionId, ownerId, dimensionId, type, state,
                workflowEntryId, waitKey, revision, createdGameTime, updatedGameTime,
                totalUnits, cursorUnits, succeededUnits, failedUnits, payload);
    }
}
