package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsOperationReason;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanBlueprintResumePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetWorkflowProtectedPayload;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowType;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiAction;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiReducer;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiTransition;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 把 1.21.1 客户端控制器和网络包收束在生产边界。
 *
 * <p>Core 只决定按钮是否有效及其命令；这里负责读取真实同步状态和发送现有网络包，
 * 不复制工作流业务规则，也不会把 preview 夹具带入游戏。</p>
 */
final class WorkflowUiAdapter {
    private WorkflowUiAdapter() {
    }

    static WorkflowUiState snapshot(ClientRtsController controller) {
        List<WorkflowUiRow> rows = new ArrayList<>();
        for (RtsWorkflowStatus status : controller.getActiveWorkflows()) {
            if (status == null || !status.isActive()) {
                continue;
            }
            rows.add(toRow(status));
        }
        return new WorkflowUiState(true, controller.hasPendingJobs(), rows);
    }

    /** 只构造短摘要；缺料物品名称在详情窗口打开时才通过注册表整理。 */
    static WorkflowUiRow findRow(ClientRtsController controller, int entryId) {
        if (controller == null || entryId < 0) {
            return null;
        }
        for (RtsWorkflowStatus status : controller.getActiveWorkflows()) {
            if (status != null && status.isActive() && status.entryId() == entryId) {
                return toRow(status);
            }
        }
        return null;
    }

    private static WorkflowUiRow toRow(RtsWorkflowStatus status) {
        String statusText = translated(statusKey(status));
        String detailText = localizedDetail(status);
        String nextStepText = translated(nextStepKey(status));
        List<String> missingItems = visibleMissingItems(status);
        return new WorkflowUiRow(
                status.entryId(),
                status.type().name().toLowerCase(java.util.Locale.ROOT),
                translated(status.typeTranslationKey()),
                status.progressText(),
                status.completedBlocks(), status.totalBlocks(), status.failedBlocks(),
                status.remainingBlocks(), status.suspended(), status.paused(),
                status.protectedWorkflow(), status.type() == RtsWorkflowType.BLUEPRINT_BUILD,
                statusText, summaryLines(status, statusText, detailText, nextStepText),
                missingItems, detailText, nextStepText);
    }

    /** 只依据结构化 reason/lifecycle 映射短状态，不从 detail 文案猜原因。 */
    static String statusKey(RtsWorkflowStatus status) {
        if (status.paused() || status.reason() == RtsOperationReason.MANUAL_PAUSED) {
            return "screen.rtsbuilding.workflow.status.paused";
        }
        if (!status.suspended()) {
            switch (status.reason()) {
                case CONFIG_DISABLED:
                    return "screen.rtsbuilding.workflow.status.disabled";
                case PERMISSION_DENIED:
                    return "screen.rtsbuilding.workflow.status.no_access";
                case EXECUTION_ERROR:
                    return "screen.rtsbuilding.workflow.status.error";
                case QUEUE_FULL:
                    return "screen.rtsbuilding.workflow.status.queue_full";
                default:
                    return status.isComplete()
                            ? "screen.rtsbuilding.workflow.status.complete"
                            : "screen.rtsbuilding.workflow.status.running";
            }
        }
        switch (status.reason()) {
            case RESOURCE_MISSING:
                return "screen.rtsbuilding.workflow.status.need_items";
            case TOOL_MISSING:
                return "screen.rtsbuilding.workflow.status.need_tool";
            case CHUNK_UNLOADED:
                return "screen.rtsbuilding.workflow.status.loading";
            case CONFIG_DISABLED:
                return "screen.rtsbuilding.workflow.status.disabled";
            case PERMISSION_DENIED:
                return "screen.rtsbuilding.workflow.status.no_access";
            case EXECUTION_ERROR:
                return "screen.rtsbuilding.workflow.status.error";
            case QUEUE_FULL:
                return "screen.rtsbuilding.workflow.status.queue_full";
            default:
                return "screen.rtsbuilding.workflow.status.waiting";
        }
    }

    private static List<String> summaryLines(
            RtsWorkflowStatus status,
            String statusText,
            String detailText,
            String nextStepText) {
        List<String> details = new ArrayList<>();
        details.add(Component.translatable(
                "screen.rtsbuilding.workflow.detail.status", statusText).getString());
        if (!visibleMissingItems(status).isEmpty()) {
            details.add(Component.translatable(
                    "screen.rtsbuilding.workflow.detail.missing_count",
                    visibleMissingItems(status).size()).getString());
        }
        if (!detailText.isEmpty()) {
            details.add(Component.translatable(
                    "screen.rtsbuilding.workflow.detail.server", detailText).getString());
        }
        if (!nextStepText.isEmpty()) {
            details.add(nextStepText);
        }
        return details;
    }

    /** 清除恢复/完成/手动暂停沿用的旧缺料快照，只让当前缺料原因进入详情。 */
    static List<String> visibleMissingItems(RtsWorkflowStatus status) {
        if (status == null || status.isComplete()
                || !status.suspended()
                || status.reason() != RtsOperationReason.RESOURCE_MISSING) {
            return List.of();
        }
        return status.missingItems();
    }

    /** 在玩家主动打开详情时整理全部缺料名称，保持正常列表帧不创建不可见 ItemStack。 */
    static List<String> fullDetailLines(WorkflowUiRow row) {
        List<String> details = new ArrayList<>();
        if (row == null) {
            return details;
        }
        details.add(Component.translatable(
                "screen.rtsbuilding.workflow.detail.status", row.statusText).getString());
        details.add(Component.translatable(
                "screen.rtsbuilding.workflow.detail.progress", row.progressText).getString());
        for (String itemId : row.missingItemIds) {
            details.add(Component.translatable(
                    "screen.rtsbuilding.workflow.detail.missing_item",
                    displayItemName(itemId)).getString());
        }
        if (!row.detailText.isEmpty()) {
            details.add(Component.translatable(
                    "screen.rtsbuilding.workflow.detail.server", row.detailText).getString());
        }
        if (!row.nextStepText.isEmpty()) {
            details.add(row.nextStepText);
        }
        return details;
    }

    static String nextStepKey(RtsWorkflowStatus status) {
        if (status.paused() || status.reason() == RtsOperationReason.MANUAL_PAUSED) {
            return "screen.rtsbuilding.workflow.next.resume";
        }
        if (!status.suspended()) {
            switch (status.reason()) {
                case CONFIG_DISABLED:
                    return "screen.rtsbuilding.workflow.next.disabled";
                case PERMISSION_DENIED:
                    return "screen.rtsbuilding.workflow.next.access";
                case EXECUTION_ERROR:
                    return "screen.rtsbuilding.workflow.next.error";
                case QUEUE_FULL:
                    return "screen.rtsbuilding.workflow.next.waiting";
                default:
                    return status.isComplete()
                            ? "screen.rtsbuilding.workflow.next.complete"
                            : "screen.rtsbuilding.workflow.next.running";
            }
        }
        switch (status.reason()) {
            case RESOURCE_MISSING:
                return "screen.rtsbuilding.workflow.next.items";
            case TOOL_MISSING:
                return "screen.rtsbuilding.workflow.next.tool";
            case CHUNK_UNLOADED:
                return "screen.rtsbuilding.workflow.next.loading";
            case CONFIG_DISABLED:
                return "screen.rtsbuilding.workflow.next.disabled";
            case PERMISSION_DENIED:
                return "screen.rtsbuilding.workflow.next.access";
            case EXECUTION_ERROR:
                return "screen.rtsbuilding.workflow.next.error";
            default:
                return "screen.rtsbuilding.workflow.next.waiting";
        }
    }

    private static String displayItemName(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return itemId;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    /** 错误详情只允许短的结构化提示，避免把堆栈、NBT 或服务端路径送进 UI。 */
    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "";
        }
        String compact = detail.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        String lower = compact.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("exception") || lower.contains("stacktrace")
                || lower.contains(" at ") || lower.contains("nbt")
                || compact.contains("\\") || compact.contains("/")) {
            return "";
        }
        return compact.length() <= 240 ? compact : compact.substring(0, 237) + "...";
    }

    /** 只映射已知 token/结构化原因；未知内部 token 不直接进入玩家文本。 */
    static String localizedDetail(RtsWorkflowStatus status) {
        String safe = safeDetail(status.detailMessage());
        if ("screen.rtsbuilding.workflow.waiting_items".equals(safe)) {
            return translated(safe);
        }
        switch (status.reason()) {
            case RESOURCE_MISSING:
                return translated("screen.rtsbuilding.workflow.detail.missing_generic");
            case TOOL_MISSING:
                return translated("screen.rtsbuilding.workflow.detail.tool_missing");
            case CHUNK_UNLOADED:
                return translated("screen.rtsbuilding.workflow.detail.chunk_waiting");
            case CONFIG_DISABLED:
                return translated("screen.rtsbuilding.workflow.detail.config_disabled");
            case PERMISSION_DENIED:
                return translated("screen.rtsbuilding.workflow.detail.permission_denied");
            case EXECUTION_ERROR:
                return "workflow_missing".equals(safe)
                        ? translated("screen.rtsbuilding.workflow.detail.workflow_missing")
                        : translated("screen.rtsbuilding.workflow.detail.execution_error");
            case QUEUE_FULL:
                return translated("screen.rtsbuilding.workflow.detail.queue_full");
            case SKIPPED:
                return translated("screen.rtsbuilding.workflow.detail.skipped");
            case CANCELLED:
                return translated("screen.rtsbuilding.workflow.detail.cancelled");
            case REPLACED:
                return translated("screen.rtsbuilding.workflow.detail.replaced");
            case UNKNOWN:
                if ("workflow_waiting".equals(safe)) {
                    return translated("screen.rtsbuilding.workflow.detail.waiting_unknown");
                }
                String human = humanDetailOrEmpty(safe);
                return human.isEmpty()
                        ? translated("screen.rtsbuilding.workflow.detail.waiting_unknown")
                        : human;
            default:
                return humanDetailOrEmpty(safe);
        }
    }

    private static String humanDetailOrEmpty(String safe) {
        if (safe.isEmpty() || safe.indexOf(' ') < 0
                || safe.indexOf(':') >= 0 || safe.indexOf('=') >= 0
                || safe.indexOf('_') >= 0) {
            return "";
        }
        return safe;
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    static WorkflowUiTransition dispatch(ClientRtsController controller,
                                         WorkflowUiState state, WorkflowUiAction action) {
        WorkflowUiTransition transition = WorkflowUiReducer.apply(state, action);
        switch (transition.command) {
            case TOGGLE_PROTECTED:
                WorkflowUiRow row = find(state, action.entryId);
                if (row != null) {
                    PacketDistributor.sendToServer(new C2SRtsSetWorkflowProtectedPayload(
                            row.entryId, !row.protectedWorkflow));
                }
                break;
            case TOGGLE_PAUSED:
                PacketDistributor.sendToServer(new C2SRtsPauseWorkflowPayload(action.entryId));
                break;
            case SCAN_RESUME_PLACEMENT:
                PacketDistributor.sendToServer(new C2SRtsScanResumePlacementPayload(action.entryId));
                break;
            case SCAN_RESUME_BLUEPRINT:
                PacketDistributor.sendToServer(new C2SRtsScanBlueprintResumePayload(action.entryId));
                break;
            case DELETE:
                PacketDistributor.sendToServer(new C2SRtsDeleteWorkflowPayload(action.entryId));
                break;
            default:
                break;
        }
        return transition;
    }

    private static WorkflowUiRow find(WorkflowUiState state, int entryId) {
        for (WorkflowUiRow row : state.rows) if (row.entryId == entryId) return row;
        return null;
    }
}
