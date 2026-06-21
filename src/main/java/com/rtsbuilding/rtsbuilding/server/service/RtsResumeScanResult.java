package com.rtsbuilding.rtsbuilding.server.service;

/**
 * 挂起放置作业的世界扫描结果。
 *
 * <p>这个 record 只描述“现在恢复会遇到什么”：剩余格数、已被玩家手动补上的格、
 * 冲突格、库存可用数和缺口。真正恢复仍由放置批处理队列负责。</p>
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

    public boolean hasEnoughItems() {
        return this.missingItems <= 0;
    }

    public boolean hasConflicts() {
        return this.conflictCount > 0;
    }
}
