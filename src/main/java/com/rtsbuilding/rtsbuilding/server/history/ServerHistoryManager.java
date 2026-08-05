package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import javax.annotation.Nullable;

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
 *   <li>线程安全：使用 ConcurrentHashMap</li>
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

    public static void recordPlacement(EntityPlayerMP player, List<BlockPos> positions, EnumFacing face) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        List<HistoryBlockRecord> records = captureBlocks(player.getServerForPlayer(), positions);
        if (records.isEmpty()) {
            return;
        }
        HistoryEntry entry = new HistoryEntry(false, records, face, player.dimension);
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUniqueID(), k -> new PlayerHistory());
        ph.undoStack.add(entry);
        if (ph.undoStack.size() > RtsHistoryConstants.SHAPE_HISTORY_LIMIT) {
            ph.undoStack.removeFirst();
        }
        cleanupIfNeeded();
        sendSync(player);
    }

    public static void recordBreak(EntityPlayerMP player, List<BlockPos> positions, EnumFacing face) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        List<HistoryBlockRecord> records = captureBlocks(player.getServerForPlayer(), positions);
        if (records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face);
    }

    public static void recordBreakWithRecords(EntityPlayerMP player, List<HistoryBlockRecord> records, EnumFacing face) {
        if (player == null || records == null || records.isEmpty()) {
            return;
        }
        pushBreakEntry(player, records, face);
    }

    private static void pushBreakEntry(EntityPlayerMP player, List<HistoryBlockRecord> records, EnumFacing face) {
        HistoryEntry entry = new HistoryEntry(true, records, face, player.dimension);
        PlayerHistory ph = playerHistories.computeIfAbsent(player.getUniqueID(), k -> new PlayerHistory());
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

    public static int executeUndo(EntityPlayerMP player) {
        if (player == null) return 0;
        HistoryEntry entry = undo(player);
        if (entry == null) return 0;

        if (entry.getDimension() != player.dimension) {
            PlayerHistory ph = playerHistories.get(player.getUniqueID());
            if (ph != null) {
                ph.undoStack.addLast(entry);
            }
            return 0;
        }

        int executed = HistoryExecutor.executeUndo(player, entry);
        if (executed < entry.getBlockCount()) {
            if (executed <= 0) {
                PlayerHistory ph0 = playerHistories.get(player.getUniqueID());
                if (ph0 != null) {
                    ph0.undoStack.add(entry);
                }
            } else {
                HistoryEntry remaining = entry.removeRestored(executed);
                if (remaining != null) {
                    updateUndoEntry(player, remaining);
                }
            }
        }
        sendSync(player);
        return executed;
    }

    public static void sendSync(EntityPlayerMP player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markHistory(player.getUniqueID());
    }

    /** 仅由 Tick 末 Effect Committer 调用。 */
    public static void sendSyncNow(EntityPlayerMP player) {
        if (player == null) return;
        int undoSize = getUndoSize(player.getUniqueID());
        RtsClientboundPackets.sendToPlayer(player,
                new com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload(undoSize));
    }

    // ======================================================================
    //  撤回（底层栈操作）
    // ======================================================================

    @Nullable
    public static HistoryEntry undo(EntityPlayerMP player) {
        if (player == null) return null;
        PlayerHistory ph = playerHistories.get(player.getUniqueID());
        if (ph == null) return null;
        if (ph.undoStack.isEmpty()) return null;
        return ph.undoStack.removeLast();
    }

    // ======================================================================
    //  部分恢复支持
    // ======================================================================

    public static void updateUndoEntry(EntityPlayerMP player, HistoryEntry entry) {
        if (player == null || entry == null) return;
        PlayerHistory ph = playerHistories.get(player.getUniqueID());
        if (ph == null) return;
        if (!ph.undoStack.isEmpty()) {
            ph.undoStack.removeLast();
            ph.undoStack.add(entry);
        }
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
    public static HistoryBlockRecord captureBlock(WorldServer level, BlockPos pos) {
        if (level == null || pos == null || !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) return null;
        BlockState state = BlockState.fromWorld(level, pos);
        if (state.getBlock() == Blocks.air) return null;
        NBTTagCompound beData = captureBlockEntityData(level, pos);
        return new HistoryBlockRecord(pos, state, beData);
    }

    // ======================================================================
    //  内部方法
    // ======================================================================

    private static List<HistoryBlockRecord> captureBlocks(WorldServer level, List<BlockPos> positions) {
        List<HistoryBlockRecord> records = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) continue;
            BlockState state = BlockState.fromWorld(level, pos);
            if (state.getBlock() == Blocks.air) continue;
            NBTTagCompound beData = captureBlockEntityData(level, pos);
            records.add(new HistoryBlockRecord(pos, state, beData));
        }
        return records;
    }

    @Nullable
    private static NBTTagCompound captureBlockEntityData(WorldServer level, BlockPos pos) {
        if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) return null;
        TileEntity blockEntity = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
        if (blockEntity == null) return null;
        NBTTagCompound tag = new NBTTagCompound();
        blockEntity.writeToNBT(tag);
        return tag;
    }

    // ======================================================================
    //  内部数据结构
    // ======================================================================

    /** 每个玩家独立的撤回栈。所有访问均为单线程（服务端游戏主线程）。 */
    private static final class PlayerHistory {
        final ArrayDeque<HistoryEntry> undoStack = new ArrayDeque<>();
    }
}
