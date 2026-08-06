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
 * 为已链接的异维度储存提供有界、短期、不持久化的区块唤醒。
 *
 * <p>玩家在有效 RTS 会话中访问已存在的跨维度链接时，服务端以设置、插件功能、区块名额和原版权限作为边界。
 * 它故意不用玩家在当前维度的距离来否决该请求；那会破坏 RTS 的正常远程管理。</p>
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

    /**
     * 请求目标区块的短期加载。首次请求不同步等待区块：本轮返回不可用，加载事件到达后再刷新储存视图。
     */
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
                new WakeTicketKey(player.getUUID(), packedChunk));
        return targetLevel.hasChunkAt(pos);
    }

    /** 区块加载后只刷新等待这个精确维度/区块端点的玩家储存视图。 */
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

    /** 玩家断线时主动释放尚未自然过期的票据。 */
    public void releasePlayer(MinecraftServer server, UUID playerId) {
        if (server != null && playerId != null) {
            removeTickets(server, playerId, leases.release(playerId));
        }
    }

    /** 停服时主动释放票据并清理内存租约。 */
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

    /** 仅供诊断与 GameTest 观测的活跃短租约数。 */
    public int activeLeaseCount(UUID playerId) {
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
