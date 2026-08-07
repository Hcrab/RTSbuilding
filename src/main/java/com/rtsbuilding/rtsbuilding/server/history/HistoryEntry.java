package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.util.EnumFacing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单次操作的历史记录（类似 Ultimine-Rewind 的 UltimineRecord）。
 * <p>
 * 记录一次放置或破坏操作的所有方块信息，支持：
 * <ul>
 *   <li>时间戳与过期机制（自动清理旧记录）</li>
 *   <li>部分恢复（跳过已被占用的位置）</li>
 *   <li>放置/破坏两种操作类型</li>
 * </ul>
 */
public class HistoryEntry {

    /** 默认过期时间：10 分钟 */
    private static final long DEFAULT_EXPIRY_MS = 600_000L;

    private final UUID entryId;
    private final long timestamp;
    private final HistoryOperation operation;
    private final List<HistoryBlockRecord> blocks;
    private final EnumFacing face;
    /** 操作所属维度，用于防止跨维度误操作 */
    private final int dimension;
    private final int sourceSlot;

    /**
     * @param isDestructive true=破坏操作（撤回=重新放置），false=放置操作（撤回=破坏方块）
     * @param blocks       每个方块的位置和操作前的完整状态
     * @param face         所有位置的公共操作面
     * @param dimension    操作发生时的维度，用于执行时校验
     */
    public HistoryEntry(boolean isDestructive, List<HistoryBlockRecord> blocks, EnumFacing face, int dimension) {
        this(isDestructive ? HistoryOperation.CREATIVE_BREAK : HistoryOperation.CREATIVE_PLACEMENT,
                blocks, face, dimension, -1);
    }

    public HistoryEntry(HistoryOperation operation, List<HistoryBlockRecord> blocks,
            EnumFacing face, int dimension, int sourceSlot) {
        this.entryId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.operation = operation;
        this.blocks = com.rtsbuilding.rtsbuilding.server.task.Java8Collections.copyList(blocks);
        this.face = face;
        this.dimension = dimension;
        this.sourceSlot = sourceSlot;
    }

    // ===== 获取器 =====

    public UUID getEntryId() {
        return entryId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDestructive() {
        return operation.destructive();
    }

    public HistoryOperation getOperation() { return operation; }

    public List<HistoryBlockRecord> getBlocks() {
        return blocks;
    }

    public EnumFacing getFace() {
        return face;
    }

    public int getDimension() {
        return dimension;
    }

    public int getSourceSlot() { return sourceSlot; }

    public int getBlockCount() {
        return blocks.size();
    }

    // ===== 过期检查 =====

    public boolean isExpired() {
        return isExpired(DEFAULT_EXPIRY_MS);
    }

    public boolean isExpired(long expiryMs) {
        return System.currentTimeMillis() - timestamp > expiryMs;
    }

    // ===== 部分恢复支持 =====

    /**
     * 从记录中移除已恢复的方块，返回剩余方块的记录。
     *
     * @param restoredCount 已成功恢复/撤销的方块数量
     * @return 剩余方块的记录；如果全部完成则返回 null
     */
    public HistoryEntry remainingAfter(java.util.Set<net.minecraft.util.math.BlockPos> completedPositions) {
        if (completedPositions == null || completedPositions.isEmpty()) return this;
        List<HistoryBlockRecord> remaining = new ArrayList<HistoryBlockRecord>();
        for (HistoryBlockRecord record : blocks) {
            if (!completedPositions.contains(record.pos())) remaining.add(record);
        }
        return remaining.isEmpty() ? null
                : new HistoryEntry(operation, remaining, face, dimension, sourceSlot);
    }

    /** 只保留本次真正执行成功的位置，用于迁移到相反方向的历史栈。 */
    public HistoryEntry completedOnly(java.util.Set<net.minecraft.util.math.BlockPos> completedPositions) {
        if (completedPositions == null || completedPositions.isEmpty()) return null;
        List<HistoryBlockRecord> completed = new ArrayList<HistoryBlockRecord>();
        for (HistoryBlockRecord record : blocks) {
            if (completedPositions.contains(record.pos())) completed.add(record);
        }
        return completed.isEmpty() ? null
                : new HistoryEntry(operation, completed, face, dimension, sourceSlot);
    }
}
