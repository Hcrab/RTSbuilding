package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsHarvestTierSkippedPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.List;

/** 采掘视觉反馈的版本适配边界。 */
public final class RtsMiningNetworkHelper {
    private RtsMiningNetworkHelper() { }
    public static void sendMineProgress(EntityPlayerMP player, BlockPos pos, int stage) {
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsMineProgressPayload(pos, (byte) stage));
    }
    public static void sendBreakAnimation(EntityPlayerMP player, BlockPos pos, IBlockState state, IBlockState result) {
        if (player != null && pos != null) RtsClientboundPackets.sendToPlayer(player,
                new S2CRtsBreakAnimationPayload(pos.toImmutable(), state, result));
    }
    public static void sendUltimineProgress(EntityPlayerMP player, int processed, int total) {
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsUltimineProgressPayload(processed, total));
    }
    public static void sendHarvestTierSkipped(EntityPlayerMP player, List<BlockPos> positions) {
        if (player != null && positions != null && !positions.isEmpty())
            RtsClientboundPackets.sendToPlayer(player,
                    new S2CRtsHarvestTierSkippedPayload(new ArrayList<BlockPos>(positions)));
    }
    public static void notifyHarvestTierLimit(EntityPlayerMP player, List<BlockPos> positions) {
        if (player == null || positions == null || positions.isEmpty()) return;
        player.sendStatusMessage(new TextComponentTranslation("message.rtsbuilding.plugin.harvest_tier_limited"), true);
        sendHarvestTierSkipped(player, positions);
    }
    public static void clearMineProgress(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null) return;
        player.getServerWorld().sendBlockBreakProgress(player.getEntityId(), pos, -1);
        sendMineProgress(player, pos, -1);
    }
    public static void sendUltimineBatchProgress(EntityPlayerMP player, RtsStorageSession session) {
        if (session.mining.ultimineProgressPos == null) return;
        int total = Math.max(1, session.mining.ultimineTotalTargets);
        int stage = Math.min(9, (int) (session.mining.ultimineBrokenTargets / (double) total * 10.0D));
        sendMineProgress(player, session.mining.ultimineProgressPos, stage);
    }
}
