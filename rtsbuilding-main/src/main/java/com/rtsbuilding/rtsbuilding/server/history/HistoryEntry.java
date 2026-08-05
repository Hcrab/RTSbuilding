package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.Direction;
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
 *   <li>放置/破坏两种操作类型</li>
 *   <li>单次预算分批撤销（超预算部分由调用方重新入栈）</li>
 * </ul>
 */
public class HistoryEntry {

    /** 默认过期时间：10 分钟 */
    private static final long DEFAULT_EXPIRY_MS = 600_000L;

    private final UUID entryId;
    private final long timestamp;
    private final boolean isDestructive;
    private final List<HistoryBlockRecord> blocks;
    private final Direction face;
    /** 操作所属维度，用于防止跨维度误操作 */
    private final ResourceKey<Level> dimension;

    /**
     * @param isDestructive true=破坏操作（撤回=重新放置），false=放置操作（撤回=破坏方块）
     * @param blocks       每个方块的位置和操作前的完整状态
     * @param face         所有位置的公共操作面
     * @param dimension    操作发生时的维度，用于执行时校验
     */
    public HistoryEntry(boolean isDestructive, List<HistoryBlockRecord> blocks, Direction face, ResourceKey<Level> dimension) {
        this.entryId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.isDestructive = isDestructive;
        this.blocks = List.copyOf(blocks);
        this.face = face;
        this.dimension = dimension;
    }

    // ===== 获取器 =====

    public UUID getEntryId() {
        return entryId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isDestructive() {
        return isDestructive;
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
}
