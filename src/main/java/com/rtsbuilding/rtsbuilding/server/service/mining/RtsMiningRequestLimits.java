package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.common.mining.MiningSelectionBounds;
import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 所有区域挖掘入口共用的接纳检查与反馈，不拥有任务、工具或配置缓存。
 * 合法请求整体接纳；旧客户端或配置变更导致的超限请求整体退回，绝不挖出一个静默缩水的区域。
 */
public final class RtsMiningRequestLimits {
    private RtsMiningRequestLimits() {
    }

    public static boolean accepts(ServerPlayer player, List<BlockPos> positions) {
        return accepts(player, positions, false);
    }

    /** 连通树群有自己的计数规划，不能因稀疏枝叶的包围盒而破坏整组砍树的正常流程。 */
    public static boolean accepts(ServerPlayer player, List<BlockPos> positions, boolean connectedGroup) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        int maxAllowed = connectedGroup
                ? com.rtsbuilding.rtsbuilding.Config.maxTreeBlocks()
                : limit.maxVolume();
        boolean accepted = connectedGroup
                ? positions != null && !positions.isEmpty() && positions.size() <= maxAllowed
                        && positions.stream().allMatch(java.util.Objects::nonNull)
                : MiningSelectionBounds.accepts(positions, limit);
        return report(player, accepted, maxAllowed);
    }

    public static boolean accepts(ServerPlayer player, MiningSelectionBounds bounds) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        return report(player, bounds != null && bounds.fits(limit), limit.maxVolume());
    }

    public static boolean acceptsShape(ServerPlayer player,
            com.rtsbuilding.rtsbuilding.common.shape.model.AreaShape shape,
            com.rtsbuilding.rtsbuilding.common.shape.model.AreaShapeInput input) {
        SelectionVolumeLimit limit = RtsMiningValidator.areaMineSelectionLimit();
        return report(player, com.rtsbuilding.rtsbuilding.common.mining.MiningShapeVolume.fits(
                shape, input, limit), limit.maxVolume());
    }

    private static boolean report(ServerPlayer player, boolean accepted, int maxAllowed) {
        if (!accepted && player != null) {
            player.displayClientMessage(Component.translatableWithFallback(
                    "message.rtsbuilding.mining.selection_too_large",
                    "Selection exceeds the server volume limit (%s blocks). Refresh the preview and try again.",
                    maxAllowed), false);
        }
        return accepted;
    }
}
