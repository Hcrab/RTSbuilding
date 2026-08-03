package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.server.camera.RtsCameraManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketChunkData;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 为正在打开的 RTS 远程菜单保留一个目标区块，并在开窗前向该玩家同步完整区块。
 *
 * <p>本类只解决 1.12.2 的远程 GUI 世界上下文问题，不负责判权、执行右键或放宽
 * {@code Container#canInteractWith}。许多旧模组的客户端 GUI 工厂会按坐标重新读取
 * TileEntity；只有方块更新包而没有完整区块时，它们会直接返回 {@code null}。
 * 每名玩家最多持有一个、深度为一的 Forge 区块票据，打开新目标或关闭菜单时立即释放，
 * 从而把强加载限制在当前正在使用的远程菜单上。
 */
final class RtsRemoteMenuChunkLease {
    private static final double VANILLA_INTERACTION_DISTANCE_SQ = 64.0D;
    private static final int FULL_CHUNK_SECTION_MASK = 0xFFFF;
    private static final Map<UUID, Lease> LEASES = new ConcurrentHashMap<UUID, Lease>();

    private RtsRemoteMenuChunkLease() {
    }

    /**
     * 远于原版交互距离时强加载并完整同步目标区块。
     *
     * @return 本次是否建立或复用了远程区块准备状态；返回 {@code false} 仅表示目标足够近，
     *         或输入不合法，不代表正常近距离交互应被拒绝。
     */
    static boolean prepare(EntityPlayerMP player, BlockPos pos) {
        return prepare(player, pos, RtsTraceIds.NONE);
    }

    static boolean prepare(EntityPlayerMP player, BlockPos pos, long traceId) {
        if (player == null || pos == null || !RtsCameraManager.isActive(player)
                || !RtsCameraManager.isWithinActionRange(player, pos)) {
            logPrepare(traceId, pos, "REJECTED", false, false);
            return false;
        }
        WorldServer level = player.getServerWorld();
        if (level == null || pos.getY() < 0 || pos.getY() >= level.getHeight()) {
            logPrepare(traceId, pos, "INVALID_WORLD", false, false);
            return false;
        }
        if (player.getDistanceSqToCenter(pos) <= VANILLA_INTERACTION_DISTANCE_SQ
                && level.isBlockLoaded(pos)) {
            logPrepare(traceId, pos, "NEAR_ALREADY_LOADED", false, true);
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        UUID playerId = player.getUniqueID();
        Lease current = LEASES.get(playerId);
        if (current != null && current.matches(level, chunkPos)) {
            boolean synced = synchronizeClientChunk(player, level, chunkPos);
            logPrepare(traceId, pos, "LEASE_REUSED", current.ticket != null, synced);
            return true;
        }

        release(player, traceId, "TARGET_CHANGED");
        ForgeChunkManager.Ticket ticket = requestTicket(player, level);
        if (ticket != null) {
            ticket.setChunkListDepth(1);
            ForgeChunkManager.forceChunk(ticket, chunkPos);
        }

        // 即使整合包耗尽 Forge ticket，也要同步加载一次并继续尝试开窗。
        Chunk chunk = level.getChunkProvider().provideChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            releaseTicket(ticket, chunkPos);
            logPrepare(traceId, pos, "CHUNK_MISSING", ticket != null, false);
            return false;
        }
        LEASES.put(playerId, new Lease(level, chunkPos, ticket));
        boolean synced = synchronizeClientChunk(player, chunk);
        logPrepare(traceId, pos, "LEASE_CREATED", ticket != null, synced);
        return true;
    }

    static void release(EntityPlayerMP player) {
        release(player, RtsTraceIds.NONE, "UNSPECIFIED");
    }

    static void release(EntityPlayerMP player, long traceId, String reason) {
        if (player == null) return;
        Lease lease = LEASES.remove(player.getUniqueID());
        if (lease != null) {
            releaseTicket(lease.ticket, lease.chunkPos);
            if (RtsTraceIds.isPresent(traceId)) {
                RtsbuildingMod.LOGGER.info(
                        "[RTS-TRACE] side=S event=CHUNK_RELEASED trace={} kind=REMOTE_GUI chunk={},{} ticket={} reason={}",
                        RtsTraceIds.format(traceId), lease.chunkPos.x, lease.chunkPos.z,
                        lease.ticket != null, reason);
            }
        }
    }

    private static ForgeChunkManager.Ticket requestTicket(EntityPlayerMP player, WorldServer level) {
        if (RtsbuildingMod.INSTANCE == null) return null;
        try {
            return ForgeChunkManager.requestPlayerTicket(
                    RtsbuildingMod.INSTANCE,
                    player.getName(),
                    level,
                    ForgeChunkManager.Type.NORMAL);
        } catch (RuntimeException | LinkageError failure) {
            RtsbuildingMod.LOGGER.warn("RTS 远程菜单无法申请区块票据，将退化为单次强加载：{}", failure.toString());
            return null;
        }
    }

    private static boolean synchronizeClientChunk(EntityPlayerMP player, WorldServer level, ChunkPos chunkPos) {
        Chunk chunk = level.getChunkProvider().provideChunk(chunkPos.x, chunkPos.z);
        return chunk != null && synchronizeClientChunk(player, chunk);
    }

    private static boolean synchronizeClientChunk(EntityPlayerMP player, Chunk chunk) {
        try {
            player.connection.sendPacket(new SPacketChunkData(chunk, FULL_CHUNK_SECTION_MASK));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            // 完整区块同步失败时，后续仍会发送方块与 TE 增量包；不能让第三方坏 update tag 崩服。
            RtsbuildingMod.LOGGER.warn("RTS 远程菜单完整区块同步失败，将尝试增量同步：{}", failure.toString());
            return false;
        }
    }

    private static void logPrepare(long traceId, BlockPos pos, String outcome,
            boolean ticket, boolean chunkSynced) {
        if (!RtsTraceIds.isPresent(traceId)) return;
        RtsbuildingMod.LOGGER.info(
                "[RTS-TRACE] side=S event=CHUNK_PREPARE trace={} kind=REMOTE_GUI target={} outcome={} ticket={} chunkSynced={}",
                RtsTraceIds.format(traceId), pos, outcome, ticket, chunkSynced);
    }

    private static void releaseTicket(ForgeChunkManager.Ticket ticket, ChunkPos chunkPos) {
        if (ticket == null) return;
        try {
            ForgeChunkManager.unforceChunk(ticket, chunkPos);
        } finally {
            ForgeChunkManager.releaseTicket(ticket);
        }
    }

    private static final class Lease {
        private final WorldServer level;
        private final ChunkPos chunkPos;
        private final ForgeChunkManager.Ticket ticket;

        private Lease(WorldServer level, ChunkPos chunkPos, ForgeChunkManager.Ticket ticket) {
            this.level = level;
            this.chunkPos = chunkPos;
            this.ticket = ticket;
        }

        private boolean matches(WorldServer otherLevel, ChunkPos otherChunkPos) {
            return this.level == otherLevel && this.chunkPos.equals(otherChunkPos);
        }
    }
}
