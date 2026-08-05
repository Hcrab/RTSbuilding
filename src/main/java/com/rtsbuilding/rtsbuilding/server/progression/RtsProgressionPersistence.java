package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.Team;
import net.minecraft.world.WorldServer;

final class RtsProgressionPersistence {
    static final String NBT_VERSION = "version";
    static final String NBT_HOME_POS = "home_pos";
    static final String NBT_HOME_DIMENSION = "home_dimension";
    static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    private RtsProgressionPersistence() {
    }

    static NBTTagCompound root(EntityPlayerMP player) {
        NBTTagCompound root = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.PROGRESSION);
        if (root.func_150296_c().isEmpty()) {
            root.setInteger(NBT_VERSION, 1);
            SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, root);
        }
        return root;
    }

    static void save(EntityPlayerMP player, NBTTagCompound root) {
        SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, root);
    }

    static String sharedProgressionKey(EntityPlayerMP player) {
        return sharedProgressionContext(player).key();
    }

    static String sharedProgressionLabel(EntityPlayerMP player) {
        return sharedProgressionContext(player).label();
    }

    static TeamProgressionContext sharedProgressionContext(EntityPlayerMP player) {
        if (!RtsProgressionManager.isEnabled() || player == null
                || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.getAsBoolean()) {
            return TeamProgressionContext.NONE;
        }
        String ftbTeamKey = RtsFtbCompat.progressionTeamKey(player);
        if (!isBlank(ftbTeamKey)) {
            return new TeamProgressionContext(ftbTeamKey, RtsFtbCompat.progressionTeamLabel(player));
        }
        String openPacTeamKey = RtsOpenPacCompat.progressionTeamKey(player);
        if (!isBlank(openPacTeamKey)) {
            return new TeamProgressionContext(openPacTeamKey, RtsOpenPacCompat.progressionTeamLabel(player));
        }
        Team vanillaTeam = player.getTeam();
        return vanillaTeam == null
                ? TeamProgressionContext.NONE
                : new TeamProgressionContext("scoreboard:" + vanillaTeam.getRegisteredName(), vanillaTeam.getRegisteredName());
    }

    static RtsSharedProgressionData sharedProgressionData(EntityPlayerMP player) {
        WorldServer overworld = com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getWorld(com.rtsbuilding.rtsbuilding.platform.server.ServerCompat.getServer(player), 0);
        return RtsSharedProgressionData.get(overworld == null ? player.getServerForPlayer() : overworld);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static final class TeamProgressionContext {
        static final TeamProgressionContext NONE = new TeamProgressionContext("", "");

        private final String key;
        private final String label;

        TeamProgressionContext(String key, String label) {
            this.key = key == null ? "" : key;
            this.label = label == null ? "" : label;
        }

        String key() { return key; }
        String label() { return label; }
    }
}
