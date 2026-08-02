package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiState;

import java.util.ArrayList;
import java.util.List;

/** 工作流浮窗的 preview-only 确定性快照，不模拟服务端执行。 */
final class WorkflowPreviewFixtures {
    private WorkflowPreviewFixtures() {
    }

    static boolean supports(UiPreviewScenario.Variant variant) {
        return variant == UiPreviewScenario.Variant.WORKFLOW_ACTIVE
                || variant == UiPreviewScenario.Variant.WORKFLOW_PAUSED
                || variant == UiPreviewScenario.Variant.WORKFLOW_SUSPENDED
                || variant == UiPreviewScenario.Variant.WORKFLOW_MIXED;
    }

    static WorkflowUiState forScenario(UiPreviewScenario scenario, UiLanguageBundle language) {
        List<WorkflowUiRow> rows = new ArrayList<WorkflowUiRow>();
        UiPreviewScenario.Variant variant = scenario.variant();
        if (variant == UiPreviewScenario.Variant.WORKFLOW_ACTIVE
                || variant == UiPreviewScenario.Variant.WORKFLOW_MIXED) {
            rows.add(row(101, "quick_build", language, 36, 120,
                    false, false, false, false));
        }
        if (variant == UiPreviewScenario.Variant.WORKFLOW_PAUSED
                || variant == UiPreviewScenario.Variant.WORKFLOW_MIXED) {
            rows.add(row(102, "area_destroy", language, 80, 160,
                    false, true, true, false));
        }
        if (variant == UiPreviewScenario.Variant.WORKFLOW_SUSPENDED
                || variant == UiPreviewScenario.Variant.WORKFLOW_MIXED) {
            rows.add(row(103, "blueprint_build", language, 54, 240,
                    true, false, variant == UiPreviewScenario.Variant.WORKFLOW_SUSPENDED, true));
        }
        return new WorkflowUiState(true, variant == UiPreviewScenario.Variant.WORKFLOW_MIXED, rows);
    }

    private static WorkflowUiRow row(int id, String type, UiLanguageBundle language,
                                     int completed, int total, boolean suspended,
                                     boolean paused, boolean protectedWorkflow, boolean blueprint) {
        String label = language == null ? type
                : language.text("screen.rtsbuilding.workflow.type." + type);
        if (suspended && language != null) {
            label = language.format("screen.rtsbuilding.workflow.suspended", label);
        }
        return new WorkflowUiRow(id, type, label, completed + "/" + total,
                completed, total, 0, total - completed, suspended, paused,
                protectedWorkflow, blueprint);
    }
}
