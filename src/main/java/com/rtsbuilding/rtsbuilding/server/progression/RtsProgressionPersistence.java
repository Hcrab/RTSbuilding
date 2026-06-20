package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

final class RtsProgressionPersistence {
    static final String NBT_ROOT = "rtsbuilding_progression";
    static final String NBT_VERSION = "version";
    static final String NBT_HOME_POS = "home_pos";
    static final String NBT_HOME_DIMENSION = "home_dimension";
    static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    private RtsProgressionPersistence() {
    }

    static CompoundTag root(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) {
            root.putInt(NBT_VERSION, 1);
            player.getPersistentData().put(NBT_ROOT, root);
        }
        return root;
    }

    static String sharedProgressionKey(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null
                || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get()) {
            return "";
        }
        String ftbTeamKey = RtsFtbCompat.progressionTeamKey(player);
        if (ftbTeamKey != null && !ftbTeamKey.isBlank()) {
            return ftbTeamKey;
        }
        Team vanillaTeam = player.getTeam();
        return vanillaTeam instanceof PlayerTeam playerTeam ? "scoreboard:" + playerTeam.getName() : "";
    }

    static RtsSharedProgressionData sharedProgressionData(ServerPlayer player) {
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        return RtsSharedProgressionData.get(overworld == null ? player.serverLevel() : overworld);
    }
}
