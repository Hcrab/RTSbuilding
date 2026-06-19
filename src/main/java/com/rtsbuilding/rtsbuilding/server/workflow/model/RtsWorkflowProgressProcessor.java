package com.rtsbuilding.rtsbuilding.server.workflow.model;

/**
 * 工作流进度数据的统一 API 处理器。
 *
 * <p>这是直接消费 {@link RtsWorkflowStatus} 的 UI 渲染辅助方法的统一入口。
 * 随着 {@code RtsWorkflowProgressData} 合并到 {@code RtsWorkflowStatus}，
 * {@code process()} 转换器已不再需要——消费者直接从状态记录中读取预计算字段。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 客户端：从已收到的状态开始
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
    //  面板渲染辅助方法
    // ======================================================================

    /**
     * 计算指定宽度进度条的填充宽度（像素）。
     *
     * @param status   工作流状态
     * @param barWidth 进度条总宽度（像素）
     * @return 填充宽度（像素），取值范围 [0, barWidth]
     */
    public static int computeFillWidth(RtsWorkflowStatus status, int barWidth) {
        if (status == null || !status.isActive() || status.totalBlocks() <= 0 || barWidth <= 0) {
            return 0;
        }
        float fraction = (float) status.completedBlocks() / (float) status.totalBlocks();
        return Math.min(barWidth, Math.round(barWidth * Math.min(1.0F, fraction)));
    }

    /**
     * 返回显示字符串，格式为「已完成/总数」，例如 "45/100"。
     */
    public static String formatProgressText(RtsWorkflowStatus status) {
        if (status == null || !status.isActive()) return "";
        return status.progressText();
    }

    /**
     * 返回此工作流条目的显示标签，可选择附加「(搁置)」后缀。
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
