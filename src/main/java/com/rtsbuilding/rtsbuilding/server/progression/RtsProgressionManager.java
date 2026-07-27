package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.server.network.RtsClientboundPackets;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;

public final class RtsProgressionManager {
    public static final int DEFAULT_MAX_ACTION_RADIUS_BLOCKS = 128;
    public static final int DEFAULT_FLUID_CAPACITY_BUCKETS = 100;
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;
    public static final int HOME_SELECTION_RADIUS_BLOCKS = 34;
    public static final int HOME_RELOCATION_COOLDOWN_DAYS = 20;
    public static final long TICKS_PER_GAME_DAY = 24000L;
    public static final long HOME_RELOCATION_COOLDOWN_TICKS =
            HOME_RELOCATION_COOLDOWN_DAYS * TICKS_PER_GAME_DAY;

    private RtsProgressionManager() {
    }

    public static boolean isEnabled() {
        return Config.ENABLE_SURVIVAL_PROGRESSION.getAsBoolean();
    }

    public static boolean canUse(EntityPlayerMP player, RtsFeature feature) {
        return RtsPluginService.canUse(player, feature);
    }

    public static double getActionRadius(EntityPlayerMP player) {
        return RtsPluginService.actionRadius(player);
    }

    public static int getFluidCapacityBuckets(EntityPlayerMP player) {
        return DEFAULT_FLUID_CAPACITY_BUCKETS;
    }

    public static int getUltimineLimit(EntityPlayerMP player) {
        return DEFAULT_ULTIMINE_LIMIT;
    }

    public static boolean canBypassHomeRadius(EntityPlayerMP player) {
        return RtsPluginService.canBypassHomeRadius(player);
    }

    public static String sharedProgressionKey(EntityPlayerMP player) {
        return RtsProgressionPersistence.sharedProgressionKey(player);
    }

    public static String sharedProgressionLabel(EntityPlayerMP player) {
        return RtsProgressionPersistence.sharedProgressionLabel(player);
    }

    public static com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData sharedProgressionData(EntityPlayerMP player) {
        return RtsProgressionPersistence.sharedProgressionData(player);
    }

    public static boolean hasHome(EntityPlayerMP player) {
        return RtsHomeManager.hasHome(player);
    }

    public static HomeAnchor getHome(EntityPlayerMP player) {
        return RtsHomeManager.getHome(player);
    }

    public static boolean canStartNormalRts(EntityPlayerMP player) {
        return !isEnabled()
                || (RtsHomeManager.hasHome(player)
                && RtsHomeManager.canOpenRtsNearHome(player));
    }

    public static boolean shouldStartHomeSelection(EntityPlayerMP player) {
        return isEnabled() && player != null && !RtsHomeManager.hasHome(player);
    }

    public static void beginHomeSelection(EntityPlayerMP player) {
        RtsHomeManager.beginHomeSelection(player);
    }

    public static void endHomeSelection(EntityPlayerMP player) {
        RtsHomeManager.endHomeSelection(player);
    }

    public static boolean isHomeSelectionActive(EntityPlayerMP player) {
        return RtsHomeManager.isHomeSelectionActive(player);
    }

    public static boolean canSelectHome(EntityPlayerMP player, BlockPos pos) {
        return RtsHomeManager.canSelectHome(player, pos);
    }

    public static boolean canChangeHome(EntityPlayerMP player) {
        return RtsHomeManager.canChangeHome(player);
    }

    public static long remainingHomeCooldownTicks(EntityPlayerMP player) {
        return RtsHomeManager.remainingHomeCooldownTicks(player);
    }

    public static long remainingHomeCooldownDays(EntityPlayerMP player) {
        return RtsHomeManager.remainingHomeCooldownDays(player);
    }

    public static boolean commitHome(EntityPlayerMP player, BlockPos pos) {
        if (RtsHomeManager.commitHome(player, pos)) {
            syncRelatedPlayers(player);
            return true;
        }
        return false;
    }

    public static void onPlayerLogin(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (!isBlank(sharedKey)
                && RtsProgressionPersistence.sharedProgressionData(player).home(sharedKey) == null) {
            HomeAnchor personalHome = RtsHomeManager.personalHome(player);
            if (personalHome != null) {
                RtsProgressionPersistence.sharedProgressionData(player).setHome(
                        sharedKey,
                        personalHome.pos(),
                        personalHome.dimension(),
                        personalHome.setGameTime());
            }
        }
        RtsPluginService.migrateLegacySkillTree(player);
        syncToPlayer(player);
    }

    public static void onPlayerLogout(EntityPlayerMP player) {
        RtsHomeManager.endHomeSelection(player);
    }

    public static void syncToPlayer(EntityPlayerMP player) {
        if (player != null) RtsEffectAccumulator.INSTANCE.markProgressionState(player.getUniqueID());
    }

    /** 仅由 Tick 末 Effect Committer 调用，普通业务入口只登记最新完整快照。 */
    public static void syncToPlayerNow(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        HomeAnchor home = RtsHomeManager.getHome(player);
        RtsClientboundPackets.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                isEnabled(),
                home != null,
                home == null ? BlockPos.ORIGIN : home.pos(),
                home == null ? "" : Integer.toString(home.dimension()),
                RtsHomeManager.remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                getFluidCapacityBuckets(player),
                getUltimineLimit(player),
                canBypassHomeRadius(player)));
    }

    private static void syncRelatedPlayers(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (isBlank(sharedKey)) {
            syncToPlayer(player);
            return;
        }
        for (EntityPlayerMP onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            if (sharedKey.equals(RtsProgressionPersistence.sharedProgressionKey(onlinePlayer))) {
                syncToPlayer(onlinePlayer);
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class HomeAnchor {
        private final BlockPos pos;
        private final int dimension;
        private final long setGameTime;

        public HomeAnchor(BlockPos pos, int dimension, long setGameTime) {
            this.pos = pos;
            this.dimension = dimension;
            this.setGameTime = setGameTime;
        }

        public BlockPos pos() { return pos; }
        public int dimension() { return dimension; }
        public long setGameTime() { return setGameTime; }
    }
}
