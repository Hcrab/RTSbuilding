package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

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
    private final Direction face;
    /** 操作所属维度，用于防止跨维度误操作 */
    private final ResourceKey<Level> dimension;
    /** 生存破坏撤回时优先检查的原快捷栏槽位；无槽位语义时为 -1。 */
    private final int sourceSlot;

    /**
     * @param operation  操作发生时冻结的模式与动作类型
     * @param blocks     每个方块的位置、操作前快照与操作后校验状态
     * @param face       所有位置的公共操作面
     * @param dimension  操作发生时的维度，用于执行时校验
     * @param sourceSlot 生存破坏时记录的原快捷栏槽位；无此语义时为 -1
     */
    public HistoryEntry(HistoryOperation operation, List<HistoryBlockRecord> blocks, Direction face,
            ResourceKey<Level> dimension, int sourceSlot) {
        this.entryId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.operation = operation;
        this.blocks = List.copyOf(blocks);
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

    public HistoryOperation getOperation() {
        return operation;
    }

    public List<HistoryBlockRecord> getBlocks() {
        return blocks;
    }

    public Direction getFace() {
        return face;
    }

    public ResourceKey<Level> getDimension() {
        return dimension;
    }

    public int getSourceSlot() {
        return sourceSlot;
    }

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
     * @param completedPositions 本次已经成功恢复/撤销的精确位置
     * @return 剩余方块的记录；如果全部完成则返回 null
     */
    public HistoryEntry remainingAfter(java.util.Set<BlockPos> completedPositions) {
        if (completedPositions == null || completedPositions.isEmpty()) {
            return this;
        }
        List<HistoryBlockRecord> remaining = blocks.stream()
                .filter(record -> !completedPositions.contains(record.pos()))
                .toList();
        if (remaining.isEmpty()) {
            return null;
        }
        return new HistoryEntry(operation, remaining, face, dimension, sourceSlot);
    }

    /**
     * 只保留本次真正执行成功的位置，用于把部分完成的条目精确迁移到相反历史栈。
     */
    public HistoryEntry completedOnly(java.util.Set<BlockPos> completedPositions) {
        if (completedPositions == null || completedPositions.isEmpty()) {
            return null;
        }
        List<HistoryBlockRecord> completed = blocks.stream()
                .filter(record -> completedPositions.contains(record.pos()))
                .toList();
        if (completed.isEmpty()) {
            return null;
        }
        return new HistoryEntry(operation, completed, face, dimension, sourceSlot);
    }
}
