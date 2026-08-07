package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.common.RtsHistoryConstants;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 服务端撤销/重做历史所有者。
 *
 * <p>本类只在服务器主线程读写。新世界操作会切断旧重做分支；部分执行按精确位置推进，
 * 跨维度、未加载目标和领地拒绝都不会吞掉仍未执行的历史。</p>
 */
public final class ServerHistoryManager {
    private static final long CLEANUP_INTERVAL_MS = 120_000L;
    private static final Map<UUID, PlayerHistory> PLAYER_HISTORIES = new HashMap<UUID, PlayerHistory>();
    private static long lastCleanupTime = System.currentTimeMillis();

    private ServerHistoryManager() {}

    public static void recordPlacement(EntityPlayerMP player, List<BlockPos> positions, EnumFacing face) {
        if (player == null || positions == null || positions.isEmpty()) return;
        List<HistoryBlockRecord> records = capturePlacedBlocks(
                player.getServerWorld(), positions, player.capabilities.isCreativeMode);
        if (records.isEmpty()) return;
        HistoryOperation operation = player.capabilities.isCreativeMode
                ? HistoryOperation.CREATIVE_PLACEMENT : HistoryOperation.SURVIVAL_PLACEMENT;
        pushEntry(player, new HistoryEntry(operation, records, face, player.dimension, -1));
    }

    public static void recordBreak(EntityPlayerMP player, List<BlockPos> positions, EnumFacing face) {
        if (player == null || positions == null || positions.isEmpty()) return;
        List<HistoryBlockRecord> records = captureBlocks(player.getServerWorld(), positions,
                player.capabilities.isCreativeMode);
        recordBreakWithRecords(player, records, face);
    }

    public static void recordBreakWithRecords(
            EntityPlayerMP player, List<HistoryBlockRecord> records, EnumFacing face) {
        recordBreakWithRecords(player, records, face, -1);
    }

    public static void recordBreakWithRecords(
            EntityPlayerMP player, List<HistoryBlockRecord> records, EnumFacing face, int sourceSlot) {
        if (player == null || records == null || records.isEmpty()) return;
        HistoryOperation operation = player.capabilities.isCreativeMode
                ? HistoryOperation.CREATIVE_BREAK : HistoryOperation.SURVIVAL_BREAK;
        pushEntry(player, new HistoryEntry(operation, records, face, player.dimension, sourceSlot));
    }

    private static void pushEntry(EntityPlayerMP player, HistoryEntry entry) {
        PlayerHistory history = history(player.getUniqueID());
        history.redoStack.clear();
        if (!HistoryCapacityPolicy.accepts(entry.getBlocks())) {
            player.sendStatusMessage(new net.minecraft.util.text.TextComponentTranslation(
                    "message.rtsbuilding.history.too_large"), true);
            sendSync(player);
            return;
        }
        history.undoStack.addLast(entry);
        trimToLimit(history.undoStack);
        cleanupIfNeeded();
        sendSync(player);
    }

    public static int executeUndo(EntityPlayerMP player) {
        if (player == null) return 0;
        PlayerHistory history = PLAYER_HISTORIES.get(player.getUniqueID());
        if (history == null || history.undoStack.isEmpty()) return 0;
        HistoryEntry entry = history.undoStack.removeLast();
        if (entry.getDimension() != player.dimension) {
            history.undoStack.addLast(entry);
            sendSync(player);
            return 0;
        }

        HistoryExecutionResult result = HistoryExecutor.executeUndo(player, entry);
        HistoryEntry remaining = entry.remainingAfter(result.completedPositions());
        if (remaining != null) history.undoStack.addLast(remaining);
        if (entry.getOperation().creative()) {
            HistoryEntry completed = entry.completedOnly(result.completedPositions());
            if (completed != null) {
                history.redoStack.addLast(completed);
                trimToLimit(history.redoStack);
            }
        }
        sendSync(player);
        return result.executedCount();
    }

    /** 重做仅开放给创造历史，避免生存资源被双向搬运制造物品。 */
    public static int executeRedo(EntityPlayerMP player) {
        if (player == null || !player.capabilities.isCreativeMode) return 0;
        PlayerHistory history = PLAYER_HISTORIES.get(player.getUniqueID());
        if (history == null || history.redoStack.isEmpty()) return 0;
        HistoryEntry entry = history.redoStack.peekLast();
        if (entry == null || !entry.getOperation().creative() || entry.getDimension() != player.dimension) return 0;
        history.redoStack.removeLast();

        HistoryExecutionResult result = HistoryExecutor.executeRedo(player, entry);
        HistoryEntry remaining = entry.remainingAfter(result.completedPositions());
        if (remaining != null) history.redoStack.addLast(remaining);
        HistoryEntry completed = entry.completedOnly(result.completedPositions());
        if (completed != null) {
            history.undoStack.addLast(completed);
            trimToLimit(history.undoStack);
        }
        sendSync(player);
        return result.executedCount();
    }

    public static void sendSync(EntityPlayerMP player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markHistory(player.getUniqueID());
    }

    /** 仅由 Tick 末统一副作用提交器调用。 */
    public static void sendSyncNow(EntityPlayerMP player) {
        if (player == null) return;
        RtsClientboundPackets.sendToPlayer(player,
                new com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHistorySyncPayload(
                        getUndoSize(player.getUniqueID()), getRedoSize(player.getUniqueID())));
    }

    public static int getUndoSize(UUID playerId) {
        PlayerHistory history = PLAYER_HISTORIES.get(playerId);
        if (history == null) return 0;
        cleanupExpired(history);
        return history.undoStack.size();
    }

    public static int getRedoSize(UUID playerId) {
        PlayerHistory history = PLAYER_HISTORIES.get(playerId);
        if (history == null) return 0;
        cleanupExpired(history);
        return history.redoStack.size();
    }

    public static void clear(UUID playerId) { PLAYER_HISTORIES.remove(playerId); }

    public static void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) return;
        lastCleanupTime = now;
        for (PlayerHistory history : PLAYER_HISTORIES.values()) cleanupExpired(history);
    }

    private static void cleanupExpired(PlayerHistory history) {
        history.undoStack.removeIf(HistoryEntry::isExpired);
        history.redoStack.removeIf(HistoryEntry::isExpired);
    }

    @Nullable
    public static HistoryBlockRecord captureBlock(WorldServer world, BlockPos pos) {
        return captureBlock(world, pos, true);
    }

    @Nullable
    public static HistoryBlockRecord captureBlock(WorldServer world, BlockPos pos, boolean includeBlockEntityData) {
        if (world == null || pos == null || !world.isBlockLoaded(pos)) return null;
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == Blocks.AIR) return null;
        return new HistoryBlockRecord(pos, state,
                includeBlockEntityData ? captureBlockEntityData(world, pos) : null,
                Blocks.AIR.getDefaultState(), null);
    }

    private static List<HistoryBlockRecord> captureBlocks(
            WorldServer world, List<BlockPos> positions, boolean includeBlockEntityData) {
        List<HistoryBlockRecord> records = new ArrayList<HistoryBlockRecord>(positions.size());
        for (BlockPos pos : positions) {
            HistoryBlockRecord record = captureBlock(world, pos, includeBlockEntityData);
            if (record != null) records.add(record);
        }
        return records;
    }

    private static List<HistoryBlockRecord> capturePlacedBlocks(
            WorldServer world, List<BlockPos> positions, boolean includeAfterBlockEntityData) {
        List<HistoryBlockRecord> records = new ArrayList<HistoryBlockRecord>(positions.size());
        for (BlockPos pos : positions) {
            if (!world.isBlockLoaded(pos)) continue;
            IBlockState placed = world.getBlockState(pos);
            if (placed.getBlock() == Blocks.AIR) continue;
            records.add(HistoryBlockRecord.placement(pos, Blocks.AIR.getDefaultState(), null,
                    placed, includeAfterBlockEntityData ? captureBlockEntityData(world, pos) : null));
        }
        return records;
    }

    @Nullable
    private static NBTTagCompound captureBlockEntityData(WorldServer world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)) return null;
        TileEntity blockEntity = world.getTileEntity(pos);
        return blockEntity == null ? null : blockEntity.writeToNBT(new NBTTagCompound());
    }

    private static PlayerHistory history(UUID playerId) {
        PlayerHistory history = PLAYER_HISTORIES.get(playerId);
        if (history == null) {
            history = new PlayerHistory();
            PLAYER_HISTORIES.put(playerId, history);
        }
        return history;
    }

    private static void trimToLimit(ArrayDeque<HistoryEntry> stack) {
        while (stack.size() > RtsHistoryConstants.SHAPE_HISTORY_LIMIT) stack.removeFirst();
    }

    private static final class PlayerHistory {
        private final ArrayDeque<HistoryEntry> undoStack = new ArrayDeque<HistoryEntry>();
        private final ArrayDeque<HistoryEntry> redoStack = new ArrayDeque<HistoryEntry>();
    }
}
