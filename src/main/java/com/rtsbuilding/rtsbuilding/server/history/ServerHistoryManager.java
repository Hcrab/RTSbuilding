package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 服务端历史记录管理器（类似 Ultimine-Rewind 的 RewindDataManager）。
 * <p>
 * 管理所有玩家的撤回栈。历史记录在服务端维护，
 * 客户端通过网络包发起 undo 请求，由服务端执行并同步结果。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>服务端权威：所有记录在服务端管理，防止作弊</li>
 *   <li>过期自动清理：超过 10 分钟的历史记录自动清除</li>
 *   <li>容量限制：每栈最多 {@link RtsHistoryConstants#SHAPE_HISTORY_LIMIT} 条</li>
 *   <li>线程模型：仅在服务端主线程读写</li>
 * </ul>
 */
public final class ServerHistoryManager {
    /** 清理间隔 */
    private static final long CLEANUP_INTERVAL_MS = 120_000L; // 2分钟

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
        List<HistoryBlockRecord> records = capturePlacedBlocks(
                player.getLevel(), positions, player.isCreative());
        if (records.isEmpty()) {
            return;
        }
        recordPlacementWithRecords(player, records, face);
    }

    /** 写入已经在放置前捕获、并在放置后补齐结果状态的精确建造历史。 */
    public static void recordPlacementWithRecords(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face) {
        recordPlacementWithRecords(player, records, face, player != null && player.isCreative());
    }

    public static void recordPlacementWithRecords(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face, boolean creativeOperation) {
        if (player == null || records == null || records.isEmpty()) return;
        HistoryOperation operation = creativeOperation
                ? HistoryOperation.CREATIVE_PLACEMENT : HistoryOperation.SURVIVAL_PLACEMENT;
        pushEntry(player, new HistoryEntry(
                operation, records, face, player.getLevel().dimension(), -1));
    }

    public static void recordBreak(ServerPlayer player, List<BlockPos> positions, Direction face) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        List<HistoryBlockRecord> records = captureBlocks(
                player.getLevel(), positions, player.isCreative());
        if (records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face, -1);
    }

    public static void recordBreakWithRecords(ServerPlayer player, List<HistoryBlockRecord> records, Direction face) {
        recordBreakWithRecords(player, records, face, -1);
    }

    public static void recordBreakWithRecords(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face, int sourceSlot) {
        recordBreakWithRecords(player, records, face, sourceSlot, player != null && player.isCreative());
    }

    public static void recordBreakWithRecords(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face,
            int sourceSlot, boolean creativeOperation) {
        if (player == null || records == null || records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face, sourceSlot, creativeOperation);
    }

    private static void pushBreakEntry(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face, int sourceSlot) {
        pushBreakEntry(player, records, face, sourceSlot, player.isCreative());
    }

    private static void pushBreakEntry(
            ServerPlayer player, List<HistoryBlockRecord> records, Direction face,
            int sourceSlot, boolean creativeOperation) {
        HistoryOperation operation = creativeOperation
                ? HistoryOperation.CREATIVE_BREAK : HistoryOperation.SURVIVAL_BREAK;
        HistoryEntry entry = new HistoryEntry(
                operation, records, face, player.getLevel().dimension(), sourceSlot);
        pushEntry(player, entry);
    }

    private static void pushEntry(ServerPlayer player, HistoryEntry entry) {
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUUID(), k -> new PlayerHistory());
        // 任何新的世界操作都会切断旧的重做分支；即使该操作因过大而不进入撤回栈也一样。
        ph.redoStack.clear();
        if (!HistoryCapacityPolicy.accepts(entry.getBlocks())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.rtsbuilding.history.too_large"), true);
            sendSync(player);
            return;
        }
        ph.undoStack.add(entry);
        trimToLimit(ph.undoStack);
        cleanupIfNeeded();
        sendSync(player);
    }

    // ======================================================================
    //  撤回 完整流程
    // ======================================================================

    public static int executeUndo(ServerPlayer player) {
        if (player == null) return 0;
        HistoryEntry entry = undo(player);
        if (entry == null) return 0;

        if (!entry.getDimension().equals(player.getLevel().dimension())) {
            PlayerHistory ph = playerHistories.get(player.getUUID());
            if (ph != null) {
                ph.undoStack.addLast(entry);
            }
            sendSync(player);
            return 0;
        }

        HistoryExecutionResult result = HistoryExecutor.executeUndo(player, entry);
        int executed = result.executedCount();
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUUID(), k -> new PlayerHistory());
        if (result.completedPositions().size() < entry.getBlockCount()) {
            if (result.completedPositions().isEmpty()) {
                ph.undoStack.add(entry);
            } else {
                HistoryEntry remaining = entry.remainingAfter(result.completedPositions());
                if (remaining != null) {
                    ph.undoStack.addLast(remaining);
                }
            }
        }
        if (entry.getOperation().creative() && !result.completedPositions().isEmpty()) {
            HistoryEntry completed = entry.completedOnly(result.completedPositions());
            if (completed != null) {
                ph.redoStack.addLast(completed);
                trimToLimit(ph.redoStack);
            }
        }
        sendSync(player);
        return executed;
    }

    /**
     * 重做最近一次成功撤回的创造操作。生存模式、跨维度和不匹配快照都会保持栈与世界不变。
     */
    public static int executeRedo(ServerPlayer player) {
        if (player == null || !player.isCreative()) return 0;
        PlayerHistory ph = playerHistories.get(player.getUUID());
        if (ph == null || ph.redoStack.isEmpty()) return 0;
        HistoryEntry entry = ph.redoStack.peekLast();
        if (entry == null || !entry.getOperation().creative()
                || !entry.getDimension().equals(player.getLevel().dimension())) {
            return 0;
        }

        ph.redoStack.removeLast();
        HistoryExecutionResult result = HistoryExecutor.executeRedo(player, entry);
        if (result.completedPositions().size() < entry.getBlockCount()) {
            if (result.completedPositions().isEmpty()) {
                ph.redoStack.addLast(entry);
            } else {
                HistoryEntry remaining = entry.remainingAfter(result.completedPositions());
                if (remaining != null) ph.redoStack.addLast(remaining);
            }
        }
        if (!result.completedPositions().isEmpty()) {
            HistoryEntry completed = entry.completedOnly(result.completedPositions());
            if (completed != null) {
                ph.undoStack.addLast(completed);
                trimToLimit(ph.undoStack);
            }
        }
        sendSync(player);
        return result.executedCount();
    }

    public static void sendSync(ServerPlayer player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markHistory(player.getUUID());
    }

    /** 仅由 Tick 末 Effect Committer 调用。 */
    public static void sendSyncNow(ServerPlayer player) {
        if (player == null) return;
        int undoSize = getUndoSize(player.getUUID());
        int redoSize = getRedoSize(player.getUUID());
        RtsClientboundPackets.sendToPlayer(player,
                new com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload(
                        undoSize, redoSize));
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

    public static int getRedoSize(UUID playerId) {
        PlayerHistory ph = playerHistories.get(playerId);
        if (ph == null) return 0;
        cleanupExpired(ph);
        return ph.redoStack.size();
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
        ph.redoStack.removeIf(HistoryEntry::isExpired);
    }

    @Nullable
    public static HistoryBlockRecord captureBlock(ServerLevel level, BlockPos pos) {
        return captureBlock(level, pos, true);
    }

    @Nullable
    public static HistoryBlockRecord captureBlock(
            ServerLevel level, BlockPos pos, boolean includeBlockEntityData) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return null;
        CompoundTag beData = includeBlockEntityData ? captureBlockEntityData(level, pos) : null;
        return new HistoryBlockRecord(pos, state, beData);
    }

    /** 建造前快照允许显式记录空气，从而区分“删除新方块”和“恢复被覆盖方块”。 */
    @Nullable
    public static HistoryBlockRecord capturePlacementBefore(
            ServerLevel level, BlockPos pos, boolean includeBlockEntityData) {
        if (level == null || pos == null || !level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        CompoundTag beData = includeBlockEntityData && !state.isAir()
                ? captureBlockEntityData(level, pos) : null;
        return HistoryBlockRecord.placement(pos, state, beData, state);
    }

    // ======================================================================
    //  内部方法
    // ======================================================================

    private static List<HistoryBlockRecord> captureBlocks(
            ServerLevel level, List<BlockPos> positions, boolean includeBlockEntityData) {
        List<HistoryBlockRecord> records = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            CompoundTag beData = includeBlockEntityData ? captureBlockEntityData(level, pos) : null;
            records.add(new HistoryBlockRecord(pos, state, beData));
        }
        return records;
    }

    private static List<HistoryBlockRecord> capturePlacedBlocks(
            ServerLevel level, List<BlockPos> positions, boolean includeAfterBlockEntityData) {
        List<HistoryBlockRecord> records = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!level.isLoaded(pos)) continue;
            BlockState placed = level.getBlockState(pos);
            if (placed.isAir()) continue;
            CompoundTag afterData = includeAfterBlockEntityData
                    ? captureBlockEntityData(level, pos) : null;
            records.add(HistoryBlockRecord.placement(
                    pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), null,
                    placed, afterData));
        }
        return records;
    }

    @Nullable
    public static CompoundTag captureBlockEntityData(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return null;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return null;
        return blockEntity.saveWithFullMetadata();
    }

    // ======================================================================
    //  内部数据结构
    // ======================================================================

    /** 每个玩家独立的撤回栈。所有访问均为单线程（服务端游戏主线程）。 */
    private static final class PlayerHistory {
        final ArrayDeque<HistoryEntry> undoStack = new ArrayDeque<>();
        final ArrayDeque<HistoryEntry> redoStack = new ArrayDeque<>();
    }

    private static void trimToLimit(ArrayDeque<HistoryEntry> stack) {
        while (stack.size() > RtsHistoryConstants.SHAPE_HISTORY_LIMIT) {
            stack.removeFirst();
        }
    }
}
