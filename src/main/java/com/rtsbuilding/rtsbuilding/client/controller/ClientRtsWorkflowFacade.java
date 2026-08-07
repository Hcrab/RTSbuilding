package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.state.ClientWorkflowStateManager;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsWorkflowProgressPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 客户端工作流投影的完整公共门面。
 *
 * <p>它拥有服务端工作流快照、批量替换、恢复扫描和待处理标记；不执行工作流，
 * 不发送控制命令，也不拥有 Screen 绘制。控制器生命周期只负责在切换世界时清空它。</p>
 */
abstract class ClientRtsWorkflowFacade extends ClientRtsPreferenceFacade {
    protected final ClientWorkflowStateManager workflowStateManager =
            new ClientWorkflowStateManager();

    public void applyWorkflowProgress(S2CRtsWorkflowProgressPayload payload) {
        this.workflowStateManager.apply(payload);
    }

    public void applyWorkflowProgressBatch(S2CRtsWorkflowProgressBatchPayload payload) {
        this.workflowStateManager.applyBatch(payload);
    }

    public RtsWorkflowStatus getWorkflowStatus(int slot) {
        return this.workflowStateManager.status(slot);
    }

    public List<RtsWorkflowStatus> getActiveWorkflows() {
        return this.workflowStateManager.activeWorkflows();
    }

    public void clearWorkflowData() {
        this.workflowStateManager.clear();
    }

    public int getWorkflowActiveCount() {
        return this.workflowStateManager.activeCount();
    }

    public RtsWorkflowStatus[] getWorkflowStatuses() {
        return this.workflowStateManager.rawStatuses();
    }

    @Nullable
    public RtsWorkflowStatus findActiveDestroyWorkflow() {
        return this.workflowStateManager.activeDestroyWorkflow();
    }

    public boolean hasPendingJobs() {
        return this.workflowStateManager.hasPendingJobs();
    }

    public void setHasPendingJobs(boolean hasPendingJobs) {
        this.workflowStateManager.setPendingJobs(hasPendingJobs);
    }

    public void applyResumePlacementScan(S2CRtsResumePlacementScanPayload payload) {
        this.workflowStateManager.applyResumeScan(payload);
    }

    public S2CRtsResumePlacementScanPayload getResumeScanData() {
        return this.workflowStateManager.resumeScanData();
    }

    public void clearResumeScanData() {
        this.workflowStateManager.clearResumeScanData();
    }

    public boolean hasActiveWorkflow() {
        return this.workflowStateManager.hasActiveWorkflow();
    }
}


