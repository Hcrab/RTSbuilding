package com.rtsbuilding.rtsbuilding.server.storage.wake;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 为异维度已连接储存提供有界、短期且不持久化的区块唤醒。
 *
 * <p>本服务不创建储存链接、不解析 capability、不修改物品，也不让目标区块
 * 强制参与实体 Tick。解析器真正需要端点时刷新一张 FULL 区块票据；若区块
 * 尚未完成加载，本轮将端点视为暂不可用，等区块加载后再请求客户端刷新。
 * 票据在停止访问后自动过期，登出和停服还会主动释放。</p>
 */
public final class RtsCrossDimensionStorageWakeService {
    public static final RtsCrossDimensionStorageWakeService INSTANCE =
            new RtsCrossDimensionStorageWakeService();

    static final int TICKET_LIFESPAN_TICKS = 100;
    private static final int REGION_DISTANCE = 0;
    private static final TicketType<WakeTicketKey> TICKET_TYPE = TicketType.create(
            "rtsbuilding_cross_dimension_storage",
            Comparator.comparing(WakeTicketKey::playerId)
                    .thenComparingLong(WakeTicketKey::chunkPos),
            TICKET_LIFESPAN_TICKS);

    private final RtsCrossDimensionWakeLeaseTable leases = new RtsCrossDimensionWakeLeaseTable();

    private RtsCrossDimensionStorageWakeService() {
    }

    /** 确认目标区块已可读；异维度未加载时只提交唤醒请求，不同步阻塞服务端。 */
    public boolean ensureReady(ServerPlayer player, ServerLevel targetLevel, BlockPos pos) {
        if (player == null || targetLevel == null || pos == null) {
            return false;
        }
        if (player.serverLevel().dimension().equals(targetLevel.dimension())) {
            return targetLevel.hasChunkAt(pos);
        }
        if (!Config.isCrossDimensionStorageEnabled()
                || !RtsProgressionManager.canUse(player, RtsFeature.CROSS_DIMENSION_STORAGE)) {
            return false;
        }

        ChunkPos chunkPos = new ChunkPos(pos);
        long packedChunk = chunkPos.toLong();
        long now = player.server.overworld().getGameTime();
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint =
                new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(targetLevel.dimension(), packedChunk);
        RtsCrossDimensionWakeLeaseTable.TouchResult result = leases.touch(
                player.getUUID(), endpoint, now,
                Config.maxCrossDimensionAwakeChunks(), TICKET_LIFESPAN_TICKS);
        if (result == RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED) {
            return targetLevel.hasChunkAt(pos);
        }

        targetLevel.getChunkSource().addRegionTicket(
                TICKET_TYPE, chunkPos, REGION_DISTANCE,
                new WakeTicketKey(player.getUUID(), packedChunk), false);
        return targetLevel.hasChunkAt(pos);
    }

    /** 区块完成加载后只唤醒等待该精确维度和区块的玩家储存视图。 */
    public void onChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) {
            return;
        }
        Set<UUID> owners = leases.ownersOf(new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(
                level.dimension(), chunkPos.toLong()));
        for (UUID owner : owners) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                RtsEffectAccumulator.INSTANCE.markStorageViewDirty(owner, player.level().dimension());
            }
        }
    }

    /** 登出时主动移除该玩家仍未自然过期的票据和内存租约。 */
    public void releasePlayer(MinecraftServer server, UUID playerId) {
        if (server == null || playerId == null) {
            return;
        }
        removeTickets(server, playerId, leases.release(playerId));
    }

    /** 停服收口；世界关闭本来也会丢弃票据，这里同时清空静态内存。 */
    public void clear(MinecraftServer server) {
        if (server == null) {
            leases.releaseAll();
            return;
        }
        for (Map.Entry<UUID, List<RtsCrossDimensionWakeLeaseTable.WakeEndpoint>> entry
                : leases.releaseAll().entrySet()) {
            removeTickets(server, entry.getKey(), entry.getValue());
        }
    }

    /** 返回诊断和 GameTest 可见的当前短租约数，不暴露票据实现细节。 */
    public int activeLeaseCount(UUID playerId) {
        return leases.size(playerId);
    }

    private static void removeTickets(MinecraftServer server, UUID playerId,
            List<RtsCrossDimensionWakeLeaseTable.WakeEndpoint> endpoints) {
        for (RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint : endpoints) {
            ServerLevel level = server.getLevel(endpoint.dimension());
            if (level == null) {
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(endpoint.chunkPos());
            level.getChunkSource().removeRegionTicket(
                    TICKET_TYPE, chunkPos, REGION_DISTANCE,
                    new WakeTicketKey(playerId, endpoint.chunkPos()), false);
        }
    }

    private record WakeTicketKey(UUID playerId, long chunkPos) {
    }
}
