package com.rtsbuilding.rtsbuilding.server.service;

import java.util.Objects;

/**
 * 搁置（挂起）放置作业的世界扫描结果 record。
 *
 * <p>当玩家点击重启按钮后，服务端通过 {@link RtsPendingPlacementService#scanPendingJob}
 * 对挂起作业的剩余位置进行世界扫描，得到各项统计数据。
 * 此结果被缓存后由客户端消费，用于在面板上展示扫描详情和重启策略决策。
 *
 * @param itemId             正在放置的物品 ID（如 {@code "minecraft:diamond_block"}）
 * @param itemLabel          物品的本地化显示名称（可选，为空时客户端使用 itemId）
 * @param totalRemaining     作业剩余总位置数（含已放置和冲突的格）
 * @param alreadyPlacedCount 范围内已存在同种方块的位置数（用户手动放置的）
 * @param conflictCount      范围内存在不同方块的位置数（冲突格，需跳过或覆盖）
 * @param availableItems     当前存储系统中该物品的可用数量（含玩家背包）
 * @param neededItems        重启实际需要从存储提取的物品数（= totalRemaining - alreadyPlacedCount）
 * @param missingItems       缺少物品数（= neededItems - availableItems，≤0 表示足够）
 * @param workflowEntryId    目标工作流条目 ID，用于定位对应的挂起作业
 *
 * <p><b>派生方法：</b>
 * <ul>
 *   <li>{@link #hasEnoughItems()} — {@code missingItems <= 0} 时返回 {@code true}</li>
 *   <li>{@link #hasConflicts()} — 存在冲突方块时返回 {@code true}</li>
 *   <li>{@link #effectivePlaceCount()} — 实际需要放置的数量（{@code totalRemaining - alreadyPlacedCount}）</li>
 * </ul>
 */
public final class RtsResumeScanResult {
    private final String itemId;
    private final String itemLabel;
    private final int totalRemaining;
    private final int alreadyPlacedCount;
    private final int conflictCount;
    private final long availableItems;
    private final int neededItems;
    private final long missingItems;
    private final int workflowEntryId;

    public RtsResumeScanResult(String itemId, String itemLabel, int totalRemaining,
            int alreadyPlacedCount, int conflictCount, long availableItems,
            int neededItems, long missingItems, int workflowEntryId) {
        this.itemId = itemId;
        this.itemLabel = itemLabel;
        this.totalRemaining = totalRemaining;
        this.alreadyPlacedCount = alreadyPlacedCount;
        this.conflictCount = conflictCount;
        this.availableItems = availableItems;
        this.neededItems = neededItems;
        this.missingItems = missingItems;
        this.workflowEntryId = workflowEntryId;
    }

    public String itemId() { return itemId; }
    public String itemLabel() { return itemLabel; }
    public int totalRemaining() { return totalRemaining; }
    public int alreadyPlacedCount() { return alreadyPlacedCount; }
    public int conflictCount() { return conflictCount; }
    public long availableItems() { return availableItems; }
    public int neededItems() { return neededItems; }
    public long missingItems() { return missingItems; }
    public int workflowEntryId() { return workflowEntryId; }

    /**
     * 返回是否物品充足（没有缺少）。
     */
    public boolean hasEnoughItems() {
        return missingItems <= 0;
    }

    /**
     * 返回是否存在冲突方块。
     */
    public boolean hasConflicts() {
        return conflictCount > 0;
    }

    /**
     * 返回实际需要放置的数量（已扣除已存在的同种方块，但未扣除库存）。
     */
    public int effectivePlaceCount() {
        return totalRemaining - alreadyPlacedCount;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof RtsResumeScanResult)) return false;
        RtsResumeScanResult other = (RtsResumeScanResult) object;
        return totalRemaining == other.totalRemaining
                && alreadyPlacedCount == other.alreadyPlacedCount
                && conflictCount == other.conflictCount
                && availableItems == other.availableItems
                && neededItems == other.neededItems
                && missingItems == other.missingItems
                && workflowEntryId == other.workflowEntryId
                && Objects.equals(itemId, other.itemId)
                && Objects.equals(itemLabel, other.itemLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, itemLabel, totalRemaining, alreadyPlacedCount,
                conflictCount, availableItems, neededItems, missingItems, workflowEntryId);
    }

    @Override
    public String toString() {
        return "RtsResumeScanResult{itemId=" + itemId + ", remaining=" + totalRemaining
                + ", placed=" + alreadyPlacedCount + ", conflicts=" + conflictCount
                + ", available=" + availableItems + ", needed=" + neededItems
                + ", missing=" + missingItems + ", workflowEntryId=" + workflowEntryId + "}";
    }
}
