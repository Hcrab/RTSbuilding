package com.rtsbuilding.rtsbuilding.server.workflow.model;

import net.minecraft.network.chat.Component;

/**
 * 工作流面板渲染用的轻量格式化工具。
 */
public final class RtsWorkflowProgressProcessor {
    private RtsWorkflowProgressProcessor() {
    }

    public static int computeFillWidth(RtsWorkflowStatus status, int barWidth) {
        if (status == null || !status.isActive() || status.totalBlocks() <= 0 || barWidth <= 0) {
            return 0;
        }
        return Math.min(barWidth, Math.round(barWidth * Math.min(1.0F, status.progress())));
    }

    public static String formatProgressText(RtsWorkflowStatus status) {
        return status == null || !status.isActive() ? "" : status.progressText();
    }

    public static String formatLabel(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) {
            return "";
        }
        String label = Component.translatable(status.typeTranslationKey()).getString();
        if (status.suspended()) {
            label = Component.translatable("screen.rtsbuilding.workflow.suspended", label).getString();
        } else if (status.paused()) {
            label = Component.translatable("screen.rtsbuilding.workflow.paused", label).getString();
        }
        return label;
    }
}
