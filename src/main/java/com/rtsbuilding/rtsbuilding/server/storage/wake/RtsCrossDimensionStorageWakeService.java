package com.rtsbuilding.rtsbuilding.server.storage.wake;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 为跨维已连接存储提供有界、短期的 Forge 区块 ticket。
 *
 * <p>本服务只拥有强加载 ticket 的生命周期：每个玩家、维度和区块组合至多持有一张
 * ticket，后续访问仅刷新 100 tick 租约。它不建立连接、不读取 capability，也不绕过
 * 配置或进度权限；同维访问完全不经过本服务。这样分页、取放和动作可在短窗口内继续
 * 使用同一目标区块，同时在超时、退出和停服时无条件释放 ticket。</p>
 */
public final class RtsCrossDimensionStorageWakeService {
    public static final RtsCrossDimensionStorageWakeService INSTANCE =
            new RtsCrossDimensionStorageWakeService();

    static final int TICKET_LIFESPAN_TICKS = 100;
    private final RtsCrossDimensionWakeLeaseTable leases = new RtsCrossDimensionWakeLeaseTable();
    private final Map<UUID, LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding>>
            ticketsByPlayer = new LinkedHashMap<UUID,
                    LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding>>();

    private RtsCrossDimensionStorageWakeService() {
    }

    /**
     * 确认跨维目标区块可读，并在成功申请后保留对应 ticket 到租约到期。
     * Confirm that a cross-dimensional target is readable and retain its ticket until expiry.
     */
    public synchronized boolean ensureReady(EntityPlayerMP player, WorldServer targetLevel, BlockPos pos) {
        if (player == null || targetLevel == null || pos == null) {
            return false;
        }
        if (player.dimension == targetLevel.provider.getDimension()) {
            return targetLevel.isBlockLoaded(pos);
        }
        if (!Config.isCrossDimensionStorageEnabled()
                || !RtsProgressionManager.canUse(player, RtsFeature.CROSS_DIMENSION_STORAGE)) {
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint =
                new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(
                        targetLevel.provider.getDimension(), ChunkPos.asLong(chunkPos.x, chunkPos.z));
        long now = targetLevel.getTotalWorldTime();
        releaseExpiredTickets(now);

        RtsCrossDimensionWakeLeaseTable.TouchResult result = leases.touch(
                player.getUniqueID(), endpoint, now,
                Config.maxCrossDimensionAwakeChunks(), TICKET_LIFESPAN_TICKS);
        if (result == RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED) {
            // 名额已满时不抢占其他端点；已经加载的区块仍可正常完成本次访问。
            return targetLevel.isBlockLoaded(pos);
        }

        TicketBinding existing = ticketFor(player.getUniqueID(), endpoint);
        if (existing != null) {
            return provideChunk(targetLevel, existing.chunkPos, pos);
        }

        ForgeChunkManager.Ticket ticket = requestTicket(player, targetLevel);
        if (ticket == null) {
            // 申请被整合包或 Forge 拒绝时，撤回刚占用的租约并安全退化为已加载区块。
            leases.release(player.getUniqueID(), endpoint);
            return targetLevel.isBlockLoaded(pos);
        }

        boolean forced = false;
        boolean bound = false;
        try {
            ticket.setChunkListDepth(1);
            ForgeChunkManager.forceChunk(ticket, chunkPos);
            forced = true;
            bindTicket(player.getUniqueID(), endpoint, new TicketBinding(ticket, chunkPos));
            bound = true;
            return provideChunk(targetLevel, chunkPos, pos);
        } catch (RuntimeException | LinkageError failure) {
            if (bound) {
                releaseTicket(player.getUniqueID(), endpoint);
            } else {
                releaseTicket(ticket, chunkPos, forced);
            }
            leases.release(player.getUniqueID(), endpoint);
            RtsbuildingMod.LOGGER.warn("RTS 跨维存储无法保持短期区块 ticket，将只使用已加载区块：{}",
                    failure.toString());
            return targetLevel.isBlockLoaded(pos);
        }
    }

    /** 每个服务端 tick 回收超时 ticket；租约到期后会先 unforce 再 release。 */
    public synchronized void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        WorldServer overworld = server.getWorld(0);
        if (overworld != null) {
            releaseExpiredTickets(overworld.getTotalWorldTime());
        }
    }

    /** 玩家登出时释放其全部跨维 ticket。 */
    public synchronized void releasePlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        leases.release(playerId);
        releasePlayerTickets(playerId);
    }

    /** 停服时释放所有已持有 ticket，即使租约表与 ticket 表意外不同步也不遗留强加载。 */
    public synchronized void clear() {
        leases.releaseAll();
        for (LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding> tickets
                : ticketsByPlayer.values()) {
            for (TicketBinding binding : tickets.values()) {
                releaseTicket(binding.ticket, binding.chunkPos, true);
            }
        }
        ticketsByPlayer.clear();
    }

    public synchronized int activeLeaseCount(UUID playerId) {
        return leases.size(playerId);
    }

    private void releaseExpiredTickets(long now) {
        List<RtsCrossDimensionWakeLeaseTable.OwnedEndpoint> expired =
                leases.releaseExpired(now, TICKET_LIFESPAN_TICKS);
        for (RtsCrossDimensionWakeLeaseTable.OwnedEndpoint lease : expired) {
            releaseTicket(lease.playerId(), lease.endpoint());
        }
    }

    private TicketBinding ticketFor(UUID playerId, RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint) {
        LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding> tickets =
                ticketsByPlayer.get(playerId);
        return tickets == null ? null : tickets.get(endpoint);
    }

    private void bindTicket(UUID playerId, RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint,
            TicketBinding binding) {
        LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding> tickets =
                ticketsByPlayer.get(playerId);
        if (tickets == null) {
            tickets = new LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding>();
            ticketsByPlayer.put(playerId, tickets);
        }
        tickets.put(endpoint, binding);
    }

    private void releasePlayerTickets(UUID playerId) {
        LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding> tickets =
                ticketsByPlayer.remove(playerId);
        if (tickets == null) {
            return;
        }
        for (TicketBinding binding : tickets.values()) {
            releaseTicket(binding.ticket, binding.chunkPos, true);
        }
    }

    private void releaseTicket(UUID playerId, RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint) {
        LinkedHashMap<RtsCrossDimensionWakeLeaseTable.WakeEndpoint, TicketBinding> tickets =
                ticketsByPlayer.get(playerId);
        if (tickets == null) {
            return;
        }
        TicketBinding binding = tickets.remove(endpoint);
        if (tickets.isEmpty()) {
            ticketsByPlayer.remove(playerId);
        }
        if (binding != null) {
            releaseTicket(binding.ticket, binding.chunkPos, true);
        }
    }

    /**
     * Ticket 必须由原始 chunk 解除强加载后再归还 Forge，避免页面刷新结束后留下孤儿 ticket。
     * Unforce the original chunk before returning the ticket to Forge.
     */
    private static void releaseTicket(ForgeChunkManager.Ticket ticket, ChunkPos chunkPos, boolean forced) {
        if (ticket == null) {
            return;
        }
        try {
            if (forced && chunkPos != null) {
                ForgeChunkManager.unforceChunk(ticket, chunkPos);
            }
        } catch (RuntimeException | LinkageError failure) {
            RtsbuildingMod.LOGGER.warn("RTS 跨维存储无法解除区块强加载：{}", failure.toString());
        } finally {
            try {
                ForgeChunkManager.releaseTicket(ticket);
            } catch (RuntimeException | LinkageError failure) {
                RtsbuildingMod.LOGGER.warn("RTS 跨维存储无法归还区块 ticket：{}", failure.toString());
            }
        }
    }

    private static boolean provideChunk(WorldServer level, ChunkPos chunkPos, BlockPos pos) {
        level.getChunkProvider().provideChunk(chunkPos.x, chunkPos.z);
        return level.isBlockLoaded(pos);
    }

    private static ForgeChunkManager.Ticket requestTicket(EntityPlayerMP player, WorldServer level) {
        if (RtsbuildingMod.INSTANCE == null) {
            return null;
        }
        try {
            return ForgeChunkManager.requestPlayerTicket(
                    RtsbuildingMod.INSTANCE, player.getName(), level, ForgeChunkManager.Type.NORMAL);
        } catch (RuntimeException | LinkageError failure) {
            RtsbuildingMod.LOGGER.warn("RTS 跨维存储无法申请短期区块 ticket，将只使用已加载区块：{}",
                    failure.toString());
            return null;
        }
    }

    /** 一个 map 项对应恰好一张已 force 的 ticket 和它的原始区块。 */
    private static final class TicketBinding {
        private final ForgeChunkManager.Ticket ticket;
        private final ChunkPos chunkPos;

        private TicketBinding(ForgeChunkManager.Ticket ticket, ChunkPos chunkPos) {
            this.ticket = ticket;
            this.chunkPos = chunkPos;
        }
    }
}
