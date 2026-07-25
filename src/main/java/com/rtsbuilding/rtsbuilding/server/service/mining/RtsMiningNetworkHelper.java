package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHarvestTierSkippedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 挖掘网络包发送辅助器，向客户端发送视觉反馈数据包。
 *
 * <p>提供对 {@link PacketDistributor#sendToPlayer} 的便捷包装，用于在远程挖掘过程中
 * 向客户端发送破坏阶段裂纹更新、破坏动画和连锁挖掘进度。
 *
 * <p>核心方法：
 * <ul>
 *   <li>{@link #sendMineProgress} — 发送指定位置的破坏阶段裂纹更新（0-9 阶段）</li>
 *   <li>{@link #sendBreakAnimation} — 发送方块破坏动画（从 state → resultState 的视觉变化）</li>
 *   <li>{@link #sendUltimineProgress} — 发送连锁挖掘进度（已处理数/总数）</li>
 *   <li>{@link #clearMineProgress} — 清除服务端和客户端的破坏阶段粒子</li>
 *   <li>{@link #sendUltimineBatchProgress} — 将连锁挖掘批次进度映射为破坏阶段发送</li>
 * </ul>
 */
public final class RtsMiningNetworkHelper {
    private RtsMiningNetworkHelper() {
    }

    /** 向指定位置发送破坏阶段裂纹更新。 */
    public static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    /**
     * 发送破坏动画数据包，显示方块从哪种状态变为哪种状态。
     */
    public static void sendBreakAnimation(ServerPlayer player, BlockPos pos, BlockState state, BlockState resultState) {
        if (player == null || pos == null) {
            return;
        }
        RtsClientboundPackets.sendToPlayer(player,
                new S2CRtsBreakAnimationPayload(pos.immutable(), state, resultState));
    }

    /** 发送连锁挖掘进度更新（已处理数/总数）。 */
    public static void sendUltimineProgress(ServerPlayer player, int processed, int total) {
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsUltimineProgressPayload(processed, total));
    }

    /** 让客户端从已确认的范围破坏预览中移除采掘等级不足的方块。 */
    public static void sendHarvestTierSkipped(ServerPlayer player, List<BlockPos> positions) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        RtsClientboundPackets.sendToPlayer(
                player,
                new S2CRtsHarvestTierSkippedPayload(List.copyOf(positions)));
    }

    /** 统一发送“RTS 采掘等级插件不足”的玩家反馈，并同步清理客户端预览中的被跳过方块。 */
    public static void notifyHarvestTierLimit(ServerPlayer player, List<BlockPos> positions) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.rtsbuilding.plugin.harvest_tier_limited"),
                true);
        sendHarvestTierSkipped(player, positions);
    }

    /**
     * 清除指定位置的服务端和客户端破坏阶段粒子。
     */
    public static void clearMineProgress(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        player.serverLevel().destroyBlockProgress(player.getId(), pos, -1);
        sendMineProgress(player, pos, -1);
    }

    /**
     * 向客户端发送当前连锁挖掘批次的进度，将 {@code processed / total}
     * 映射到破坏阶段（0-9）。
     */
    public static void sendUltimineBatchProgress(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineProgressPos == null) {
            return;
        }
        int total = Math.max(1, session.mining.ultimineTotalTargets);
        int broken = session.mining.ultimineBrokenTargets;
        int stage = Math.min(9, (int) (broken / (double) total * 10.0D));
        sendMineProgress(player, session.mining.ultimineProgressPos, stage);
    }
}
