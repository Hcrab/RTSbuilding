package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.service.RtsProgressRefresher;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 服务端历史记录管理器
 * <p>
 * 管理所有玩家的撤回栈。历史记录在服务端维护，
 * 客户端通过网络包发起 undo 请求，由服务端执行并同步结果。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>服务端权威：所有记录在服务端管理，防止作弊</li>
 *   <li>过期自动清理：超过 10 分钟的历史记录自动清除</li>
 *   <li>容量限制：每栈最多 {@link RtsHistoryConstants#SHAPE_HISTORY_LIMIT} 条</li>
 *   <li>线程安全：所有访问均在服务端游戏主线程，无需并发容器</li>
 * </ul>
 */
public final class ServerHistoryManager {
    /** 清理间隔 */
    private static final long CLEANUP_INTERVAL_MS = 120_000L; // 2分钟

    /** 撤销冷却：客户端 Ctrl+Z 按住时每 tick 触发，这里限流防止一次性撤销过多 */
    private static final long UNDO_COOLDOWN_MS = 200L;

    /** 单次撤销最多处理的方块数：超大批次（如整面墙放置）分多次撤销，避免单 tick 卡顿 */
    private static final int UNDO_BUDGET_PER_TICK = 64;

    private static final Map<UUID, Long> lastUndoExecTimes = new HashMap<>();

    private static final Map<UUID, PlayerHistory> playerHistories = new HashMap<>();
    private static long lastCleanupTime = System.currentTimeMillis();

    private ServerHistoryManager() {
    }

    // ======================================================================
    //  记录操作
    // ======================================================================

    public static void recordPlacement(ServerPlayer player, List<BlockPos> positions, Direction face) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        List<HistoryBlockRecord> records = captureBlocks(player.serverLevel(), positions);
        if (records.isEmpty()) {
            return;
        }
        HistoryEntry entry = new HistoryEntry(false, records, face, player.serverLevel().dimension());
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUUID(), k -> new PlayerHistory());
        ph.undoStack.add(entry);
        if (ph.undoStack.size() > RtsHistoryConstants.SHAPE_HISTORY_LIMIT) {
            ph.undoStack.removeFirst();
        }
        cleanupIfNeeded();
        sendSync(player);
    }

    public static void recordBreak(ServerPlayer player, List<BlockPos> positions, Direction face) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        List<HistoryBlockRecord> records = captureBlocks(player.serverLevel(), positions);
        if (records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face);
    }

    public static void recordBreakWithRecords(ServerPlayer player, List<HistoryBlockRecord> records, Direction face) {
        if (player == null || records == null || records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face);
    }

    private static void pushBreakEntry(ServerPlayer player, List<HistoryBlockRecord> records, Direction face) {
        HistoryEntry entry = new HistoryEntry(true, records, face, player.serverLevel().dimension());
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUUID(), k -> new PlayerHistory());
        ph.undoStack.add(entry);
        if (ph.undoStack.size() > RtsHistoryConstants.SHAPE_HISTORY_LIMIT) {
            ph.undoStack.removeFirst();
        }
        cleanupIfNeeded();
        sendSync(player);
    }

    // ======================================================================
    //  撤回 完整流程
    // ======================================================================

    public static int executeUndo(ServerPlayer player) {
        if (player == null) return 0;

        // 冷却检查：客户端 Ctrl+Z 按住时每 tick 触发（GLFW repeat），服务端限流
        long now = System.currentTimeMillis();
        Long lastExec = lastUndoExecTimes.get(player.getUUID());
        if (lastExec != null && now - lastExec < UNDO_COOLDOWN_MS) {
            return 0;
        }

        PlayerHistory ph = playerHistories.get(player.getUUID());
        HistoryEntry entry = popSameDimensionEntry(player, ph);
        if (entry == null) {
            lastUndoExecTimes.put(player.getUUID(), now);
            sendSync(player);
            return 0;
        }

        lastUndoExecTimes.put(player.getUUID(), now);

        HistoryExecutor.UndoOutcome outcome = HistoryExecutor.executeUndo(player, entry, UNDO_BUDGET_PER_TICK);

        // 预算未处理完的部分重新入栈（保持原始顺序），下次撤销继续
        if (!outcome.pending().isEmpty()) {
            ph.undoStack.addLast(new HistoryEntry(
                    entry.isDestructive(), outcome.pending(), entry.getFace(), entry.getDimension()));
        }

        // 撤销后刷新工作流进度，确保进行中的批量作业显示与实际世界状态一致
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session != null) {
            RtsProgressRefresher.refreshWorkflowProgress(player, session);
        }
        sendSync(player);
        return outcome.executed();
    }

    /**
     * 从栈顶弹出与玩家当前维度一致的记录。
     * <p>
     * 维度不符的记录移到栈底保留（玩家回到该维度后仍可撤销），同时继续尝试下一条，
     * 避免单条跨维度记录永久挡栈导致撤销死锁。
     */
    @Nullable
    private static HistoryEntry popSameDimensionEntry(ServerPlayer player, @Nullable PlayerHistory ph) {
        if (ph == null || ph.undoStack.isEmpty()) return null;
        var currentDimension = player.serverLevel().dimension();
        int guard = ph.undoStack.size();
        while (guard-- > 0) {
            HistoryEntry candidate = ph.undoStack.peekLast();
            if (candidate.getDimension().equals(currentDimension)) {
                ph.undoStack.removeLast();
                return candidate;
            }
            // 维度不符：移到栈底保留记录，继续尝试下一条
            ph.undoStack.addFirst(ph.undoStack.removeLast());
        }
        return null; // 栈中全部为其他维度的记录
    }

    private static void feedback(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    public static void sendSync(ServerPlayer player) {
        if (player == null) return;
        int undoSize = getUndoSize(player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload(undoSize));
    }

    // ======================================================================
    //  撤回（底层栈操作）
    // ======================================================================

    @Nullable
    public static HistoryEntry undo(ServerPlayer player) {
        if (player == null) return null;
        PlayerHistory ph = playerHistories.get(player.getUUID());
        if (ph == null) return null;
        if (ph.undoStack.isEmpty()) return null;
        return ph.undoStack.removeLast();
    }

    // ======================================================================
    //  状态查询
    // ======================================================================

    public static int getUndoSize(UUID playerId) {
        PlayerHistory ph = playerHistories.get(playerId);
        if (ph == null) return 0;
        cleanupExpired(ph);
        return ph.undoStack.size();
    }

    // ======================================================================
    //  清理
    // ======================================================================

    public static void clear(UUID playerId) {
        playerHistories.remove(playerId);
    }

    public static void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;
        for (Map.Entry<UUID, PlayerHistory> entry : playerHistories.entrySet()) {
            cleanupExpired(entry.getValue());
        }
    }

    private static void cleanupExpired(PlayerHistory ph) {
        ph.undoStack.removeIf(HistoryEntry::isExpired);
    }

    @Nullable
    public static HistoryBlockRecord captureBlock(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        CompoundTag beData = captureBlockEntityData(level, pos);
        return new HistoryBlockRecord(pos, state, beData);
    }

    // ======================================================================
    //  内部方法
    // ======================================================================

    private static List<HistoryBlockRecord> captureBlocks(ServerLevel level, List<BlockPos> positions) {
        List<HistoryBlockRecord> records = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            CompoundTag beData = captureBlockEntityData(level, pos);
            records.add(new HistoryBlockRecord(pos, state, beData));
        }
        return records;
    }

    @Nullable
    private static CompoundTag captureBlockEntityData(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        return blockEntity.saveWithFullMetadata(level.registryAccess());
    }

    // ======================================================================
    //  内部数据结构
    // ======================================================================

    /** 每个玩家独立的撤回栈。所有访问均为单线程（服务端游戏主线程）。 */
    private static final class PlayerHistory {
        final ArrayDeque<HistoryEntry> undoStack = new ArrayDeque<>();
    }
}
