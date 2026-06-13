package com.rtsbuilding.rtsbuilding.server.policy;

import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.loadout.RtsMiningRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

public final class RtsBreakPolicy {
    private RtsBreakPolicy() {
    }

    public static boolean canInstantBreak(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false;
        }

        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);
        if (tracker.isPlaced(pos)) {
            return true;
        }

        return RtsMiningRules.hasRequiredLoadoutTool(player, state);
    }
}

