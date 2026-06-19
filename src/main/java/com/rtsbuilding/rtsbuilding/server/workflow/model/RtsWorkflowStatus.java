package com.rtsbuilding.rtsbuilding.server.workflow.model;

import java.util.List;

/**
 * 当前工作流进度的不可变快照——用于服务端查询、网络传输和客户端 UI 的统一记录。
 *
 * <p>本 record 将旧的 {@code RtsWorkflowStatus}（原始字段 + 计算方法）
 * 和 {@code RtsWorkflowProgressData}（预计算字段 + UI 辅助）合并为一个。
 * 派生值（{@link #remainingBlocks()}、{@link #progress()}、
 * {@link #isComplete()}）在快照创建时预计算，消费者无需重新计算。</p>
 *
 * @param type            当前活动的工作流类型
 * @param priority        活动工作流的优先级
 * @param totalBlocks     待处理的总方块数（未知则为 0）
 * @param completedBlocks 已成功处理的方块数
 * @param failedBlocks    处理失败的方块数
 * @param remainingBlocks 待处理的方块数（预计算）
 * @param progress        进度，浮点数范围 [0.0, 1.0]（预计算）
 * @param suspended       {@code true} 表示此工作流已挂起（等待物品）
 * @param paused          {@code true} 表示此工作流已被用户暂停
 * @param isComplete      {@code true} 表示所有方块均已处理完成（预计算）
 * @param missingItems    当前缺少的物品 ID 列表
 * @param detailMessage   关于当前工作流的可选人类可读详情
 * @param entryId         不可变的工作流条目 ID，用于与待处理作业关联
 */
public record RtsWorkflowStatus(
        RtsWorkflowType type,
        RtsWorkflowPriority priority,
        int totalBlocks,
        int completedBlocks,
        int failedBlocks,
        int remainingBlocks,
        float progress,
        boolean suspended,
        boolean paused,
        boolean isComplete,
        List<String> missingItems,
        String detailMessage,
        int entryId) {

    // ──────────────────────────────────────────────────────────────────
    //  工厂方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 从原始（非派生）值创建状态，预计算
     * {@code remainingBlocks}、{@code progress} 和 {@code isComplete}。
     *
     * <p>在从网络负载或可变条目状态构造时使用此工厂方法。</p>
     */
    public static RtsWorkflowStatus fromRaw(
            RtsWorkflowType type, RtsWorkflowPriority priority,
            int totalBlocks, int completedBlocks, int failedBlocks,
            List<String> missingItems, String detailMessage,
            boolean suspended, boolean paused, int entryId) {
        int remaining = totalBlocks > 0
                ? Math.max(0, totalBlocks - (completedBlocks + failedBlocks))
                : 0;
        float progress = totalBlocks > 0
                ? Math.min(1.0F, (float) (completedBlocks + failedBlocks) / (float) totalBlocks)
                : 0.0F;
        boolean isComplete = totalBlocks > 0
                && (completedBlocks + failedBlocks) >= totalBlocks;
        return new RtsWorkflowStatus(type, priority, totalBlocks, completedBlocks,
                failedBlocks, remaining, progress, suspended, paused, isComplete,
                missingItems == null ? List.of() : List.copyOf(missingItems),
                detailMessage == null ? "" : detailMessage, entryId);
    }

    /**
     * 创建一个空（无活动工作流）状态。
     */
    public static RtsWorkflowStatus idle() {
        return new RtsWorkflowStatus(null, RtsWorkflowPriority.NORMAL,
                0, 0, 0, 0, 0.0F, false, false, false,
                List.of(), "", -1);
    }

    // ──────────────────────────────────────────────────────────────────
    //  便捷查询
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回 {@code true} 表示这是一个活动（非空闲）工作流。
     */
    public boolean isActive() {
        return type != null;
    }

    /**
     * 返回 {@code true} 表示此工作流有需要关注的缺失物品。
     */
    public boolean hasMissingItems() {
        return !missingItems.isEmpty();
    }

    /**
     * 返回 {@code true} 表示此工作流有失败记录。
     */
    public boolean hasFailures() {
        return failedBlocks > 0;
    }

    // ──────────────────────────────────────────────────────────────────
    //  显示辅助方法
    // ──────────────────────────────────────────────────────────────────

    /**
     * 返回人类可读的进度摘要字符串，
     * 例如 {@code "45/100"} 或 {@code "0/0"}。
     */
    public String progressText() {
        return completedBlocks + "/" + (totalBlocks > 0 ? totalBlocks : 0);
    }

    /**
     * 返回工作流类型的显示标签，
     * 例如 {@code "Mine"}、{@code "Ultimine"}。
     */
    public String typeLabel() {
        if (type == null) return "空闲";
        return switch (type) {
            case MINE_SINGLE  -> "挖掘";
            case ULTIMINE     -> "连锁挖掘";
            case AREA_MINE    -> "区域挖掘";
            case AREA_DESTROY -> "摧毁";
            case PLACE_SINGLE -> "放置";
            case PLACE_BATCH  -> "批量放置";
            case QUICK_BUILD  -> "快速建造";
            case BLUEPRINT_BUILD -> "蓝图建造";
            case STOP_MINING  -> "停止挖掘";
        };
    }
}
