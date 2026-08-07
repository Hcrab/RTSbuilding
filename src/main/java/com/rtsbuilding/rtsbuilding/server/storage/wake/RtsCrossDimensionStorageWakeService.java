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

import java.util.Set;
import java.util.UUID;

/**
 * 为异维度已连接储存提供有界、短期且不持久化的区块唤醒。
 *
 * <p>它不创建链接、不解析 Transfer capability，也不改变物品。解析器真正访问端点时
 * 刷新一张短期票据；区块尚未完成加载时，本轮将端点视为暂不可用并等待 ChunkEvent.Load
 * 触发刷新。票据会自行超时；登出和停服仅释放内存租约，避免错误移除其他玩家刚刷新的
 * 同一端点票据。</p>
 */
public final class RtsCrossDimensionStorageWakeService {
    public static final RtsCrossDimensionStorageWakeService INSTANCE =
            new RtsCrossDimensionStorageWakeService();

    static final int TICKET_LIFESPAN_TICKS = 100;
    private static final int REGION_DISTANCE = 0;
    private static final TicketType TICKET_TYPE = new TicketType(
            TICKET_LIFESPAN_TICKS,
            TicketType.FLAG_LOADING | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    private final RtsCrossDimensionWakeLeaseTable leases = new RtsCrossDimensionWakeLeaseTable();

    private RtsCrossDimensionStorageWakeService() {
    }

    /** 提交短期唤醒并报告目标区块在本 tick 是否已经可以安全读取。 */
    public boolean ensureReady(ServerPlayer player, ServerLevel targetLevel, BlockPos pos) {
        if (player == null || targetLevel == null || pos == null) {
            return false;
        }
        if (player.level().dimension().equals(targetLevel.dimension())) {
            return targetLevel.hasChunkAt(pos);
        }
        if (!Config.isCrossDimensionStorageEnabled()
                || !RtsProgressionManager.canUse(player, RtsFeature.CROSS_DIMENSION_STORAGE)) {
            return false;
        }

        ChunkPos chunkPos = ChunkPos.containing(pos);
        long packedChunk = chunkPos.pack();
        long now = player.level().getServer().overworld().getGameTime();
        RtsCrossDimensionWakeLeaseTable.WakeEndpoint endpoint =
                new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(targetLevel.dimension(), packedChunk);
        RtsCrossDimensionWakeLeaseTable.TouchResult result = leases.touch(
                player.getUUID(), endpoint, now,
                Config.maxCrossDimensionAwakeChunks(), TICKET_LIFESPAN_TICKS);
        if (result == RtsCrossDimensionWakeLeaseTable.TouchResult.CAPACITY_REACHED) {
            return targetLevel.hasChunkAt(pos);
        }

        targetLevel.getChunkSource().addTicketWithRadius(TICKET_TYPE, chunkPos, REGION_DISTANCE);
        return targetLevel.hasChunkAt(pos);
    }

    /** 区块到达可读状态后，只标记等待该精确维度/区块的玩家储存视图。 */
    public void onChunkLoaded(ServerLevel level, ChunkPos chunkPos) {
        if (level == null || chunkPos == null) {
            return;
        }
        Set<UUID> owners = leases.ownersOf(new RtsCrossDimensionWakeLeaseTable.WakeEndpoint(
                level.dimension(), chunkPos.pack()));
        for (UUID owner : owners) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                RtsEffectAccumulator.INSTANCE.markStorageViewDirty(owner, player.level().dimension());
            }
        }
    }

    /** 登出时清理该玩家的内存租约；共享短期票据会自然过期。 */
    public void releasePlayer(MinecraftServer server, UUID playerId) {
        if (playerId != null) {
            leases.release(playerId);
        }
    }

    /** 停服收口；世界关闭也会销毁票据，这里同时释放静态内存。 */
    public void clear(MinecraftServer server) {
        leases.releaseAll();
    }

    int activeLeaseCount(UUID playerId) {
        return leases.size(playerId);
    }

}
