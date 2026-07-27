package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * Unified API processor for workflow progress data.
 *
 * <p>This is the single entry point for UI rendering helper methods that directly consume {@link RtsWorkflowStatus}.
 * With {@code RtsWorkflowProgressData} merged into {@code RtsWorkflowStatus},
 * the {@code process()} converter is no longer needed — consumers read precomputed fields directly from the status record.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Client: start from a received status
 * RtsWorkflowStatus status = ...;
 * String label = RtsWorkflowProgressProcessor.formatLabel(status);
 * String progress = RtsWorkflowProgressProcessor.formatProgressText(status);
 * int fillW = RtsWorkflowProgressProcessor.computeFillWidth(status, barWidth);
 * }</pre>
 */
public final class RtsWorkflowProgressProcessor {

    private RtsWorkflowProgressProcessor() {
    }

    // ======================================================================
    //  Panel Rendering Helpers
    // ======================================================================

    /**
     * Compute the fill width (in pixels) for a progress bar of the given width.
     *
     * @param status   Workflow status
     * @param barWidth Total progress bar width in pixels
     * @return Fill width in pixels, range [0, barWidth]
     */
    public static int computeFillWidth(RtsWorkflowStatus status, int barWidth) {
        if (status == null || !status.isActive() || status.totalBlocks() <= 0 || barWidth <= 0) {
            return 0;
        }
        float fraction = (float) status.completedBlocks() / (float) status.totalBlocks();
        return Math.min(barWidth, Math.round(barWidth * Math.min(1.0F, fraction)));
    }

    /**
     * Return the display string in the format "completed/total", e.g. "45/100".
     */
    public static String formatProgressText(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) return "";
        return status.progressText();
    }

    /**
     * Return the display label for this workflow entry, optionally appending a "(shelved)" suffix.
     */
    public static String formatLabel(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) return "";
        String label = status.typeLabel();
        if (status.suspended()) {
            label += " (搁置)";
        }
        return label;
    }
}
