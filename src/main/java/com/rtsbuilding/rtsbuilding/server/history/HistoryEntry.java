package com.rtsbuilding.rtsbuilding.server.history;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 单次操作的历史记录（类似 Ultimine-Rewind ??UltimineRecord???
 * <p>
 * 记录一次放置或破坏操作的所有方块信息，支持??
 * <ul>
 *   <li>时间戳与过期机制（自动清理旧记录??/li>
 *   <li>部分恢复（跳过已被占用的位置??/li>
 *   <li>放置/破坏两种操作类型</li>
 * </ul>
 */
public class HistoryEntry {

    /** 默认过期时间??0 分钟 */
    private static final long DEFAULT_EXPIRY_MS = 600_000L;

    private final UUID entryId;
    private final long timestamp;
    private final boolean isDestructive;
    private final List<HistoryBlockRecord> blocks;
    private final Direction face;
    /** 操作所属维度，用于防止跨维度误操作 */
    private final ResourceKey<Level> dimension;

    /**
     * @param isDestructive true=破坏操作（撤??重新放置），false=放置操作（撤??破坏方块??
     * @param blocks       每个方块的位置和操作前的完整???
     * @param face         所有位置的公共操作??
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

    // ===== Getters =====

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

    // ===== 过期检??=====

    public boolean isExpired() {
        return isExpired(DEFAULT_EXPIRY_MS);
    }

    public boolean isExpired(long expiryMs) {
        return System.currentTimeMillis() - timestamp > expiryMs;
    }

    // ===== 部分恢复支持 =====

    /**
     * 从记录中移除已恢复的方块，返回剩余方块的记录??
     *
     * @param restoredCount 已成功恢??撤销的方块数??
     * @return 剩余方块的记录；如果全部完成则返??null
     */
    public HistoryEntry removeRestored(int restoredCount) {
        if (restoredCount >= blocks.size()) {
            return null;
        }
        List<HistoryBlockRecord> remaining = new ArrayList<>(blocks.subList(restoredCount, blocks.size()));
        return new HistoryEntry(isDestructive, remaining, face, dimension);
    }
}
