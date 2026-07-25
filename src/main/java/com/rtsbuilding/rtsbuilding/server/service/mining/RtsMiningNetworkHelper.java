package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHarvestTierSkippedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import java.util.List;

/**
 * Stateless helper that sends break-stage, ultimine-progress, and break-animation
 * payloads to the client for visual feedback.
 *
 * <p>All methods are one-liner wrappers around {@link PacketDistributor}.</p>
 */
public final class RtsMiningNetworkHelper {

    private RtsMiningNetworkHelper() {
    }

    /** Sends a break-stage crack update at the given position. */
    public static void sendMineProgress(ServerPlayer player, BlockPos pos, int stage) {
        PacketDistributor.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }

    /**
     * Sends a break-animation payload showing which block changed from which
     * state to which state.
     */
    public static void sendBreakAnimation(ServerPlayer player, BlockPos pos, BlockState state, BlockState resultState) {
        if (player == null || pos == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new S2CRtsBreakAnimationPayload(pos.immutable(), state, resultState));
    }

    /** Sends an ultimine progress update (processed / total). */
    public static void sendUltimineProgress(ServerPlayer player, int processed, int total) {
        PacketDistributor.sendToPlayer(player, new S2CRtsUltimineProgressPayload(processed, total));
    }

    /** 移除客户端已确认预览中被服务端采掘等级规则剔除的坐标。 */
    public static void sendHarvestTierSkipped(ServerPlayer player, List<BlockPos> positions) {
        if (player == null || positions == null || positions.isEmpty()) {
            return;
        }
        PacketDistributor.sendToPlayer(
                player,
                new S2CRtsHarvestTierSkippedPayload(List.copyOf(positions)));
    }

    /**
     * 每批只显示一次聚合提示，并同步裁掉预览，避免逐方块刷屏和持续高亮。
     */
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
     * Clears break-stage particles on both the server and client for the
     * given position.
     */
    public static void clearMineProgress(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return;
        }
        player.serverLevel().destroyBlockProgress(player.getId(), pos, -1);
        sendMineProgress(player, pos, -1);
    }

    /**
     * Sends the current ultimine batch progress to the client, mapping
     * {@code processed / total} to a break stage (0-9).
     */
    public static void sendUltimineBatchProgress(ServerPlayer player, RtsStorageSession session) {
        if (session.mining.ultimineProgressPos == null) {
            return;
        }
        int total = Math.max(1, session.mining.ultimineTotalTargets);
        int processed = session.mining.ultimineProcessedTargets;
        int stage = Math.min(9, (int) (processed / (double) total * 10.0D));
        sendMineProgress(player, session.mining.ultimineProgressPos, stage);
        sendUltimineProgress(player, processed, total);
    }
}
