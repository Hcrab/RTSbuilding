package com.rtsbuilding.rtsbuilding.client.state;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.network.builder.RtsWorkflowWireLimits;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowPriority;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端工作流进度与“恢复放置”扫描结果的单一状态所有者。
 *
 * <p>本类负责把服务端 payload 转换成稳定的客户端快照，并在离开世界时清空。
 * 它不发送网络包、不绘制进度面板，也不决定暂停、恢复或取消动作；
 * {@code ClientRtsController} 继续作为对 UI 的公开门面。</p>
 */
public final class ClientWorkflowStateManager {
    /**
     * 客户端接收缓存沿用工作流 payload 的解码预算。
     *
     * <p>窗口只绘制当前可见行，但状态不能因为旧的 8 槽位 UI 限制而丢掉第 9
     * 条及其后的任务；真正的玩家容量仍由服务端配置决定。</p>
     */
    private static final int MAX_WORKFLOWS = RtsWorkflowWireLimits.MAX_WORKFLOW_COUNT;

    private final RtsWorkflowStatus[] statuses =
            new RtsWorkflowStatus[MAX_WORKFLOWS];
    private int activeCount;
    private boolean pendingJobs;
    private S2CRtsResumePlacementScanPayload resumeScanData;

    public void apply(S2CRtsWorkflowProgressPayload payload) {
        if (payload.isIdle()) {
            clearStatuses();
            return;
        }
        this.activeCount = Math.min(MAX_WORKFLOWS, Math.max(0, payload.workflowCount()));
        int index = payload.workflowIndex();
        if (index < 0 || index >= MAX_WORKFLOWS) {
            return;
        }
        RtsWorkflowType type = RtsWorkflowType.fromWireId(payload.workflowType());
        if (type == null) {
            this.statuses[index] = RtsWorkflowStatus.idle();
            return;
        }
        RtsWorkflowPriority priority = enumValue(
                RtsWorkflowPriority.values(), payload.priority());
        if (priority == null) {
            priority = RtsWorkflowPriority.NORMAL;
        }
        this.statuses[index] = RtsWorkflowStatus.fromRaw(
                type,
                priority,
                payload.totalBlocks(),
                payload.completedBlocks(),
                payload.failedBlocks(),
                payload.missingItems(),
                payload.detailMessage(),
                payload.suspended() != 0,
                payload.paused() != 0,
                payload.protectedWorkflow() != 0,
                RtsOperationReason.fromWireId(payload.reasonId()),
                payload.workflowEntryId());
    }

    public void applyBatch(S2CRtsWorkflowProgressBatchPayload payload) {
        clearStatuses();
        for (S2CRtsWorkflowProgressPayload entry : payload.entries()) {
            apply(entry);
        }
    }

    public RtsWorkflowStatus status(int slot) {
        if (slot < 0 || slot >= MAX_WORKFLOWS
                || this.statuses[slot] == null) {
            return RtsWorkflowStatus.idle();
        }
        return this.statuses[slot];
    }

    public List<RtsWorkflowStatus> activeWorkflows() {
        List<RtsWorkflowStatus> result = new ArrayList<>();
        int count = Math.min(this.activeCount, MAX_WORKFLOWS);
        for (int i = 0; i < count; i++) {
            RtsWorkflowStatus status = this.statuses[i];
            if (status != null && status.type() != null) {
                result.add(status);
            }
        }
        return result;
    }

    public int activeCount() {
        return this.activeCount;
    }

    /**
     * 保留旧门面的原始数组语义，供兼容调用方使用。
     *
     * <p>工作流面板应优先使用 {@link #activeWorkflows()}，避免把协议预算误当成
     * 需要一次绘制的行数。</p>
     */
    public RtsWorkflowStatus[] rawStatuses() {
        return this.statuses;
    }

    @Nullable
    public RtsWorkflowStatus activeDestroyWorkflow() {
        for (RtsWorkflowStatus status : this.statuses) {
            if (status == null || status.type() == null) {
                continue;
            }
            switch (status.type()) {
                case AREA_DESTROY:
                case ULTIMINE:
                case AREA_MINE:
                    return status;
                default:
                    break;
            }
        }
        return null;
    }

    public boolean hasActiveWorkflow() {
        for (RtsWorkflowStatus status : this.statuses) {
            if (status != null && status.type() != null) {
                return true;
            }
        }
        return false;
    }

    public boolean hasPendingJobs() {
        return this.pendingJobs;
    }

    public void setPendingJobs(boolean pendingJobs) {
        this.pendingJobs = pendingJobs;
    }

    public void applyResumeScan(S2CRtsResumePlacementScanPayload payload) {
        this.resumeScanData = payload;
    }

    public S2CRtsResumePlacementScanPayload resumeScanData() {
        return this.resumeScanData;
    }

    public void clearResumeScanData() {
        this.resumeScanData = null;
    }

    public void clear() {
        clearStatuses();
        this.pendingJobs = false;
        this.resumeScanData = null;
    }

    private void clearStatuses() {
        for (int i = 0; i < MAX_WORKFLOWS; i++) {
            this.statuses[i] = null;
        }
        this.activeCount = 0;
    }

    private static <T> T enumValue(T[] values, byte raw) {
        int index = raw;
        return index >= 0 && index < values.length ? values[index] : null;
    }
}
