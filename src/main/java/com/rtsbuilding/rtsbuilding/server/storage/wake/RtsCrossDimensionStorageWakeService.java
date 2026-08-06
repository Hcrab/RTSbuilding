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
 * <p>它不创建储存链接、不解析 capability、不移动物品，也不让目标区块永久保持活跃。
 * 请求端点时只刷新短票据；区块尚未完成加载则本轮不可用，加载事件会通知对应玩家刷新储存页。</p>
 */
public final class RtsCrossDimensionStorageWakeService {
    public static final RtsCrossDimensionStorageWakeService INSTANCE =
            new RtsCrossDimensionStorageWakeService();

    static final int TICKET_LIFESPAN_TICKS = 100;
    private static final int REGION_DISTANCE = 0;
    private static final TicketType<WakeTicketKey> TICKET_TYPE = TicketType.create(
            "rtsbuilding_cross_dimension_storage",
            Comparator.comparing(WakeTicketKey::playerId).thenComparingLong(WakeTicketKey::chunkPos),
            TICKET_LIFESPAN_TICKS);

    private final RtsCrossDimensionWakeLeaseTable leases = new RtsCrossDimensionWakeLeaseTable();

    private RtsCrossDimensionStorageWakeService() {
    }

    /**
     * 确认目标区块已经可读；异维度未加载时只提交唤醒请求，不同步阻塞服务端线程。
     */
    public boolean ensureReady(ServerPlayer player, ServerLevel targetLevel, BlockPos pos) {
        if (player == null || targetLevel == null || pos == null) {
            return false;
        }
        if (player.getLevel().dimension().equals(targetLevel.dimension())) {
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
                TICKET_TYPE, chunkPos, REGION_DISTANCE, new WakeTicketKey(player.getUUID(), packedChunk));
        return targetLevel.hasChunkAt(pos);
    }

    /** 区块完成加载后只刷新等待该精确维度/区块的储存视图。 */
    public void onChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) {
            return;
        }
        Set<UUID> owners = leases.ownersOf(new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(
                level.dimension(), chunkPos.toLong()));
        for (UUID owner : owners) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                RtsEffectAccumulator.INSTANCE.markStorageViewDirty(owner, player.getLevel().dimension());
            }
        }
    }

    /** 登出时主动移除该玩家仍未自然过期的票据和内存租约。 */
    public void releasePlayer(MinecraftServer server, UUID playerId) {
        if (server != null && playerId != null) {
            removeTickets(server, playerId, leases.release(playerId));
        }
    }

    /** 停服时释放票据和静态内存，避免集成服重开沿用旧租约。 */
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

    int activeLeaseCount(UUID playerId) {
        return leases.size(playerId);
    }

    private static void removeTickets(MinecraftServer server, UUID playerId,
            List<RtsCrossDimensionWakeLeaseTable.WakeEndpoint> endpoints) {
        for (RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint : endpoints) {
            ServerLevel level = server.getLevel(endpoint.dimension());
            if (level != null) {
                ChunkPos chunkPos = new ChunkPos(endpoint.chunkPos());
                level.getChunkSource().removeRegionTicket(
                        TICKET_TYPE, chunkPos, REGION_DISTANCE,
                        new WakeTicketKey(playerId, endpoint.chunkPos()));
            }
        }
    }

    private record WakeTicketKey(UUID playerId, long chunkPos) {
    }
}
