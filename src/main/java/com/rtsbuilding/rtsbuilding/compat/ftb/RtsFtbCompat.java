package com.rtsbuilding.rtsbuilding.compat.ftb;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;

/** 1.12 FTB Quests、FTB Library 队伍和 FTB Utilities claims 的可选兼容入口。 */
public final class RtsFtbCompat {
    private static final boolean FTB_LIB_LOADED = Loader.isModLoaded("ftblib");
    private static final boolean FTB_QUESTS_LOADED = Loader.isModLoaded("ftbquests");
    private static final boolean FTB_UTILITIES_LOADED = Loader.isModLoaded("ftbutilities");
    private static final RtsFtbCompatImpl QUESTS_IMPL = createQuestImpl();
    private static final RtsFtbTeamsCompatImpl TEAMS_IMPL = createTeamsImpl();
    private static final RtsFtbClaimsCompatImpl CLAIMS_IMPL = createClaimsImpl();

    private RtsFtbCompat() {
    }

    public static boolean isDetectAvailable() {
        return QUESTS_IMPL != null;
    }

    public static QuestDetectResult detectNow(EntityPlayerMP player) {
        if (QUESTS_IMPL == null || player == null) {
            return QuestDetectResult.unavailable();
        }
        return QUESTS_IMPL.detectNow(player);
    }

    public static String progressionTeamKey(EntityPlayerMP player) {
        return TEAMS_IMPL == null || player == null ? "" : TEAMS_IMPL.teamKey(player);
    }

    public static String progressionTeamLabel(EntityPlayerMP player) {
        return TEAMS_IMPL == null || player == null ? "" : TEAMS_IMPL.teamLabel(player);
    }

    /** 缺失 FTB Utilities 时不额外拒绝；检测到兼容层却初始化失败时保守拒绝。 */
    public static boolean canEditBlock(EntityPlayerMP player, BlockPos pos) {
        if (!FTB_UTILITIES_LOADED) {
            return true;
        }
        return player != null && pos != null && CLAIMS_IMPL != null && CLAIMS_IMPL.canEditBlock(player, pos);
    }

    public static boolean canInteractBlock(EntityPlayerMP player, BlockPos pos, EnumFacing face,
            EnumHand hand, ItemStack heldItem) {
        if (!FTB_UTILITIES_LOADED) {
            return true;
        }
        return player != null && pos != null && CLAIMS_IMPL != null
                && CLAIMS_IMPL.canInteractBlock(player, pos, hand, heldItem);
    }

    /** 使用目标世界的维度身份检查 claim，供跨维储存使用。 */
    public static boolean canInteractBlockInWorld(EntityPlayerMP player, WorldServer level, BlockPos pos) {
        if (!FTB_UTILITIES_LOADED) {
            return true;
        }
        return player != null && level != null && pos != null && CLAIMS_IMPL != null
                && CLAIMS_IMPL.canInteractBlockInDimension(
                        player, pos, level.provider.getDimension(), level.getBlockState(pos));
    }

    public static boolean canInteractEntity(EntityPlayerMP player, Entity target, EnumHand hand,
            ItemStack heldItem, boolean attack) {
        if (!FTB_UTILITIES_LOADED) {
            return true;
        }
        return player != null && target != null && CLAIMS_IMPL != null
                && CLAIMS_IMPL.canInteractEntity(player, target, attack);
    }

    private static RtsFtbCompatImpl createQuestImpl() {
        if (!FTB_LIB_LOADED || !FTB_QUESTS_LOADED) {
            return null;
        }
        try {
            return new RtsFtbCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn("FTB Quests 1.12 compat init failed; quest detect disabled.", throwable);
            return null;
        }
    }

    private static RtsFtbTeamsCompatImpl createTeamsImpl() {
        if (!FTB_LIB_LOADED) {
            return null;
        }
        try {
            return new RtsFtbTeamsCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn(
                    "FTB Library Legacy team compat init failed; RTS progression will use vanilla teams.",
                    throwable);
            return null;
        }
    }

    private static RtsFtbClaimsCompatImpl createClaimsImpl() {
        if (!FTB_LIB_LOADED || !FTB_UTILITIES_LOADED) {
            return null;
        }
        try {
            return new RtsFtbClaimsCompatImpl();
        } catch (Throwable throwable) {
            RtsbuildingMod.LOGGER.warn(
                    "FTB Utilities claim compat init failed; RTS world actions will be denied while it is loaded.",
                    throwable);
            return null;
        }
    }

    public static final class QuestDetectResult {
        private final boolean available;
        private final boolean error;
        private final int scannedTasks;
        private final int newlyCompletedTasks;

        private QuestDetectResult(boolean available, boolean error, int scannedTasks, int newlyCompletedTasks) {
            this.available = available;
            this.error = error;
            this.scannedTasks = Math.max(0, scannedTasks);
            this.newlyCompletedTasks = Math.max(0, newlyCompletedTasks);
        }

        public boolean available() { return this.available; }
        public boolean error() { return this.error; }
        public int scannedTasks() { return this.scannedTasks; }
        public int newlyCompletedTasks() { return this.newlyCompletedTasks; }

        public static QuestDetectResult unavailable() {
            return new QuestDetectResult(false, false, 0, 0);
        }

        public static QuestDetectResult failed() {
            return new QuestDetectResult(true, true, 0, 0);
        }

        public static QuestDetectResult complete(int scannedTasks, int newlyCompletedTasks) {
            return new QuestDetectResult(true, false, scannedTasks, newlyCompletedTasks);
        }
    }
}
