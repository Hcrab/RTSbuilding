package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

public final class RtsFtbCompat {
    private static final boolean FTB_QUESTS_LOADED = ModList.get().isLoaded("ftbquests");
    private static final boolean FTB_TEAMS_LOADED = ModList.get().isLoaded("ftbteams");
    private static final RtsFtbCompatImpl IMPL = createImpl();
    private static final RtsFtbTeamsCompatImpl TEAMS_IMPL = createTeamsImpl();

    private RtsFtbCompat() {
    }

    public static boolean isDetectAvailable() {
        return IMPL != null;
    }

    public static QuestDetectResult detectNow(ServerPlayer player) {
        if (IMPL == null || player == null) {
            return QuestDetectResult.unavailable();
        }
        return IMPL.detectNow(player);
    }

    public static String progressionTeamKey(ServerPlayer player) {
        if (TEAMS_IMPL == null || player == null) {
            return "";
        }
        return TEAMS_IMPL.teamKey(player);
    }

    private static RtsFtbCompatImpl createImpl() {
        if (!FTB_QUESTS_LOADED || !FTB_TEAMS_LOADED) {
            return null;
        }
        try {
            return new RtsFtbCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn("FTB compat init failed; quest detect disabled.", throwable);
            return null;
        }
    }

    private static RtsFtbTeamsCompatImpl createTeamsImpl() {
        if (!FTB_TEAMS_LOADED) {
            return null;
        }
        try {
            return new RtsFtbTeamsCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn("FTB Teams compat init failed; shared RTS progression will use vanilla teams only.", throwable);
            return null;
        }
    }

    public record QuestDetectResult(
            boolean available,
            boolean error,
            int scannedTasks,
            int newlyCompletedTasks) {
        public static QuestDetectResult unavailable() {
            return new QuestDetectResult(false, false, 0, 0);
        }

        public static QuestDetectResult failed() {
            return new QuestDetectResult(true, true, 0, 0);
        }

        public static QuestDetectResult complete(int scannedTasks, int newlyCompletedTasks) {
            return new QuestDetectResult(true, false, Math.max(0, scannedTasks), Math.max(0, newlyCompletedTasks));
        }
    }
}
