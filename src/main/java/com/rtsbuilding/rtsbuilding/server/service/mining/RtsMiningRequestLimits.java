package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.common.mining.MiningSelectionBounds;
import com.rtsbuilding.rtsbuilding.common.mining.MiningShapeVolume;
import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

/** Forge 范围请求的完整选区接纳检查；不截断显式目标。 */
public final class RtsMiningRequestLimits {
    private RtsMiningRequestLimits() { }
    public static boolean accepts(ServerPlayer player, List<BlockPos> positions) { return accepts(player, positions, false); }
    public static boolean accepts(ServerPlayer player, List<BlockPos> positions, boolean connectedGroup) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        int maxAllowed = connectedGroup ? com.rtsbuilding.rtsbuilding.Config.maxTreeBlocks() : limit.maxVolume();
        boolean accepted = connectedGroup ? positions != null && !positions.isEmpty() && positions.size() <= maxAllowed
                && positions.stream().allMatch(java.util.Objects::nonNull) : MiningSelectionBounds.accepts(positions, limit);
        return report(player, accepted, maxAllowed);
    }
    public static boolean accepts(ServerPlayer player, com.rtsbuilding.rtsbuilding.common.mining.MiningSelectionBounds bounds) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        return report(player, bounds != null && bounds.fits(limit), limit.maxVolume());
    }
    public static boolean acceptsShape(ServerPlayer player, com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape shape,
            com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput input) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        return report(player, MiningShapeVolume.fits(shape, input, limit), limit.maxVolume());
    }
    private static boolean report(ServerPlayer player, boolean accepted, int volume) {
        if (!accepted && player != null) player.displayClientMessage(Component.translatable("message.rtsbuilding.mining.selection_too_large", volume), false);
        return accepted;
    }
}
