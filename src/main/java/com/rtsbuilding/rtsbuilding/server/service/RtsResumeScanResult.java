package com.rtsbuilding.rtsbuilding.server.service;

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
public record RtsResumeScanResult(
        String itemId,
        String itemLabel,
        int totalRemaining,
        int alreadyPlacedCount,
        int conflictCount,
        long availableItems,
        int neededItems,
        long missingItems,
        int workflowEntryId) {

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
}
