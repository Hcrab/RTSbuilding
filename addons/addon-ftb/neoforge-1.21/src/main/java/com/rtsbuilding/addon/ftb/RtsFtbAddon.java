package com.rtsbuilding.addon.ftb;

import com.rtsbuilding.rtsbuilding.api.compat.QuestDetectResult;
import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import com.rtsbuilding.rtsbuilding.api.compat.RtsQuestIntegration;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

@Mod("rtsbuilding_addon_ftb")
public class RtsFtbAddon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/FTB");

    public RtsFtbAddon(IEventBus modEventBus, ModContainer modContainer) {
        var questsMod = FMLLoader.getLoadingModList().getModFileById("ftbquests");
        var teamsMod = FMLLoader.getLoadingModList().getModFileById("ftbteams");
        if (questsMod == null && teamsMod == null) {
            LOGGER.info("FTB Quests/Teams not detected — addon will not register");
            return;
        }
        var reflection = new FtbReflection();
        if (!reflection.loaded) {
            LOGGER.warn("FTB reflection load failed");
            return;
        }
        RtsCompatRegistry.register(new FtbQuestIntegration(reflection));
        LOGGER.info("FTB quest integration registered");
    }

    private static final class FtbQuestIntegration implements RtsQuestIntegration {
        private final FtbReflection ref;

        FtbQuestIntegration(FtbReflection ref) { this.ref = ref; }

        @Override public String getModId() { return "ftbquests"; }

        @Override
        public boolean isAvailable() {
            return ref.questsLoaded || ref.teamsLoaded;
        }

        @Override
        public QuestDetectResult detect(ServerPlayer player) {
            if (!ref.questsLoaded) return QuestDetectResult.unavailable();
            try {
                return ref.detectQuests(player);
            } catch (Throwable e) {
                LOGGER.warn("FTB quest detection failed", e);
                return QuestDetectResult.failed();
            }
        }

        @Override @Nullable
        public String progressionTeamKey(ServerPlayer player) {
            if (!ref.teamsLoaded) return null;
            try {
                return ref.resolveTeamKey(player);
            } catch (Throwable e) {
                return null;
            }
        }
    }

    private static final class FtbReflection {
        boolean loaded = false;
        boolean questsLoaded = false;
        boolean teamsLoaded = false;

        private Class<?> clServerQuestFile, clQuestFile, clItemTask, clTeamData;
        private MethodHandle mhGetServerQuestFile, mhGetAllItems, mhGetSubmitTasks;
        private MethodHandle mhGetTeamData, mhSetProgress, mhGetProgress;
        private MethodHandle mhGetTeamForPlayer, mhGetTeamId;

        FtbReflection() {
            try {
                var lookup = MethodHandles.publicLookup();
                var cl = getClass().getClassLoader();

                // FTB Quests
                try {
                    clServerQuestFile = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile", false, cl);
                    clQuestFile = Class.forName("dev.ftb.mods.ftbquests.quest.QuestFile", false, cl);
                    clItemTask = Class.forName("dev.ftb.mods.ftbquests.quest.task.ItemTask", false, cl);
                    clTeamData = Class.forName("dev.ftb.mods.ftbquests.util.TeamData", false, cl);

                    mhGetServerQuestFile = lookup.findStatic(clServerQuestFile, "getInstance",
                            MethodType.methodType(clServerQuestFile));
                    mhGetTeamData = lookup.findVirtual(clQuestFile, "getTeamData",
                            MethodType.methodType(clTeamData, UUID.class));

                    questsLoaded = true;
                } catch (Exception e) {
                    LOGGER.info("FTB Quests not available: {}", e.getMessage());
                }

                // FTB Teams
                try {
                    var clFtbTeamsAPI = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI", false, cl);
                    var clTeamManager = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager", false, cl);
                    var clTeam = Class.forName("dev.ftb.mods.ftbteams.api.Team", false, cl);

                    var mhApi = lookup.findStatic(clFtbTeamsAPI, "api",
                            MethodType.methodType(clFtbTeamsAPI));
                    var mhManager = lookup.findVirtual(clFtbTeamsAPI, "getManager",
                            MethodType.methodType(clTeamManager));

                    // Try both method names for cross-version compatibility
                    var api = mhApi.invoke();
                    var manager = mhManager.invoke(api);

                    try {
                        mhGetTeamForPlayer = lookup.findVirtual(clTeamManager, "getTeamForPlayerID",
                                MethodType.methodType(Optional.class, UUID.class));
                    } catch (NoSuchMethodException e) {
                        mhGetTeamForPlayer = lookup.findVirtual(clTeamManager, "getTeamForPlayer",
                                MethodType.methodType(Optional.class, UUID.class));
                    }

                    mhGetTeamId = lookup.findVirtual(clTeam, "getId",
                            MethodType.methodType(UUID.class));

                    teamsLoaded = true;
                } catch (Throwable e) {
                    LOGGER.info("FTB Teams not available: {}", e.getMessage());
                }

                // Load task reflection
                if (questsLoaded) {
                    try {
                        mhGetSubmitTasks = lookup.findVirtual(clItemTask, "getSubmitTasks",
                                MethodType.methodType(List.class));
                    } catch (NoSuchMethodException e) {
                        // Some versions have different API
                    }

                    var mhGetAllItems = lookup.findVirtual(clQuestFile, "getAllItems",
                            MethodType.methodType(List.class));
                }

                loaded = questsLoaded || teamsLoaded;
            } catch (Throwable e) {
                LOGGER.warn("FTB reflection load failed: {}", e.getMessage());
            }
        }

        QuestDetectResult detectQuests(ServerPlayer player) throws Throwable {
            if (!questsLoaded) return QuestDetectResult.unavailable();

            var questFile = mhGetServerQuestFile.invoke();
            var teamData = mhGetTeamData.invoke(questFile, player.getUUID());
            if (teamData == null) return QuestDetectResult.failed();

            var allItems = questFile.getClass().getMethod("getAllItems").invoke(questFile);
            if (!(allItems instanceof List<?> items)) return QuestDetectResult.complete(0, 0);

            int scanned = 0;
            int newlyCompleted = 0;

            for (var item : items) {
                if (!clItemTask.isInstance(item)) continue;
                scanned++;

                // getSubmitTasks equivalent — iterate quest's tasks
                var tasks = item.getClass().getMethod("getTasks").invoke(item);
                if (!(tasks instanceof List<?> taskList)) continue;

                for (var task : taskList) {
                    if (!(task.getClass().getName().contains("ItemTask"))) continue;

                    long currentProgress = (long) teamData.getClass()
                            .getMethod("getProgress", task.getClass().getInterfaces()[0])
                            .invoke(teamData, task);

                    // Count items in player inventory
                    long available = 0;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack stack = player.getInventory().getItem(i);
                        if (stack.isEmpty()) continue;
                        if (matchesTask(task, stack)) {
                            available += stack.getCount();
                        }
                    }

                    if (available > currentProgress) {
                        teamData.getClass()
                                .getMethod("setProgress", task.getClass().getInterfaces()[0], long.class)
                                .invoke(teamData, task, available);
                        newlyCompleted++;
                    }
                }
            }

            return QuestDetectResult.complete(scanned, newlyCompleted);
        }

        private boolean matchesTask(Object task, ItemStack stack) {
            try {
                var item = task.getClass().getMethod("getItem").invoke(task);
                if (item instanceof net.minecraft.world.item.ItemStack taskStack) {
                    return ItemStack.isSameItemSameComponents(taskStack, stack);
                }
            } catch (Exception ignored) {}
            return false;
        }

        @Nullable
        String resolveTeamKey(ServerPlayer player) throws Throwable {
            if (!teamsLoaded) return null;
            var optTeam = (Optional<?>) mhGetTeamForPlayer.invoke(player.getUUID());
            if (optTeam == null || optTeam.isEmpty()) return null;
            var team = optTeam.get();
            var teamId = (UUID) mhGetTeamId.invoke(team);
            return "ftb:" + teamId;
        }
    }
}
