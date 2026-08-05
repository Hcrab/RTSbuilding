package com.rtsbuilding.rtsbuilding.server.workflow.model;

public final class RtsWorkflowProgressProcessor {

    private RtsWorkflowProgressProcessor() {}

    public static String formatLabel(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) return "";
        String label = status.typeLabel();
        if (status.onHold()) {
            label += " (搁置)";
        }
        return label;
    }

    public static String formatProgressText(RtsWorkflowStatus status) {
        if (status == null) return "";
        return status.progressText();
    }

    public static int computeFillWidth(RtsWorkflowStatus status, int barWidth) {
        if (status == null || !status.isActive()) return 0;
        return Math.round(status.progress() * barWidth);
    }
}
