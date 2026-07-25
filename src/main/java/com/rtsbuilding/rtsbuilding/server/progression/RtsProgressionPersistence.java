package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

final class RtsProgressionPersistence {
    static final String NBT_VERSION = "version";
    static final String NBT_HOME_POS = "home_pos";
    static final String NBT_HOME_DIMENSION = "home_dimension";
    static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    private RtsProgressionPersistence() {
    }

    static CompoundTag root(ServerPlayer player) {
        CompoundTag root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.PROGRESSION);
        if (root.isEmpty()) {
            root.putInt(NBT_VERSION, 1);
            SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, root);
        }
        return root;
    }

    static void save(ServerPlayer player, CompoundTag root) {
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, root);
    }

    static String sharedProgressionKey(ServerPlayer player) {
        return sharedProgressionContext(player).key();
    }

    static String sharedProgressionLabel(ServerPlayer player) {
        return sharedProgressionContext(player).label();
    }

    static TeamProgressionContext sharedProgressionContext(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null
                || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.getAsBoolean()) {
            return TeamProgressionContext.NONE;
        }
        String ftbTeamKey = RtsFtbCompat.progressionTeamKey(player);
        if (ftbTeamKey != null && !ftbTeamKey.isBlank()) {
            return new TeamProgressionContext(ftbTeamKey, RtsFtbCompat.progressionTeamLabel(player));
        }
        String openPacTeamKey = RtsOpenPacCompat.progressionTeamKey(player);
        if (openPacTeamKey != null && !openPacTeamKey.isBlank()) {
            return new TeamProgressionContext(openPacTeamKey, RtsOpenPacCompat.progressionTeamLabel(player));
        }
        PlayerTeam vanillaTeam = player.getTeam();
        return vanillaTeam == null
                ? TeamProgressionContext.NONE
                : new TeamProgressionContext("scoreboard:" + vanillaTeam.getName(), vanillaTeam.getName());
    }

    static RtsSharedProgressionData sharedProgressionData(ServerPlayer player) {
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        return RtsSharedProgressionData.get(overworld == null ? player.serverLevel() : overworld);
    }

    record TeamProgressionContext(String key, String label) {
        static final TeamProgressionContext NONE = new TeamProgressionContext("", "");

        TeamProgressionContext {
            key = key == null ? "" : key;
            label = label == null ? "" : label;
        }
    }
}
