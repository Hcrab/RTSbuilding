package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

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
        return Config.ENABLE_SURVIVAL_PROGRESSION.get();
    }

    public static boolean canUse(ServerPlayer player, RtsFeature feature) {
        return RtsPluginService.canUse(player, feature);
    }

    public static double getActionRadius(ServerPlayer player) {
        return RtsPluginService.actionRadius(player);
    }

    public static int getFluidCapacityBuckets(ServerPlayer player) {
        return DEFAULT_FLUID_CAPACITY_BUCKETS;
    }

    public static int getUltimineLimit(ServerPlayer player) {
        return DEFAULT_ULTIMINE_LIMIT;
    }

    public static boolean canBypassHomeRadius(ServerPlayer player) {
        return RtsPluginService.canBypassHomeRadius(player);
    }

    public static String sharedProgressionKey(ServerPlayer player) {
        return RtsProgressionPersistence.sharedProgressionKey(player);
    }

    public static boolean hasHome(ServerPlayer player) {
        return RtsHomeManager.hasHome(player);
    }

    public static HomeAnchor getHome(ServerPlayer player) {
        return RtsHomeManager.getHome(player);
    }

    public static boolean canAccessHomeRadius(ServerPlayer player, BlockPos pos) {
        return RtsHomeManager.canAccessHomeRadius(player, pos);
    }

    public static boolean canStartNormalRts(ServerPlayer player) {
        return !isEnabled() || RtsHomeManager.hasHome(player);
    }

    public static boolean shouldStartHomeSelection(ServerPlayer player) {
        return isEnabled() && player != null && !RtsHomeManager.hasHome(player);
    }

    public static void beginHomeSelection(ServerPlayer player) {
        RtsHomeManager.beginHomeSelection(player);
    }

    public static void endHomeSelection(ServerPlayer player) {
        RtsHomeManager.endHomeSelection(player);
    }

    public static boolean isHomeSelectionActive(ServerPlayer player) {
        return RtsHomeManager.isHomeSelectionActive(player);
    }

    public static boolean canSelectHome(ServerPlayer player, BlockPos pos) {
        return RtsHomeManager.canSelectHome(player, pos);
    }

    public static boolean canChangeHome(ServerPlayer player) {
        return RtsHomeManager.canChangeHome(player);
    }

    public static long remainingHomeCooldownTicks(ServerPlayer player) {
        return RtsHomeManager.remainingHomeCooldownTicks(player);
    }

    public static long remainingHomeCooldownDays(ServerPlayer player) {
        return RtsHomeManager.remainingHomeCooldownDays(player);
    }

    public static boolean commitHome(ServerPlayer player, BlockPos pos) {
        if (RtsHomeManager.commitHome(player, pos)) {
            syncRelatedPlayers(player);
            return true;
        }
        return false;
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (!sharedKey.isBlank()
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
        syncToPlayer(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        RtsHomeManager.endHomeSelection(player);
    }

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        HomeAnchor home = RtsHomeManager.getHome(player);
        PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                isEnabled(),
                home != null,
                home == null ? BlockPos.ZERO : home.pos(),
                home == null ? "" : home.dimension().location().toString(),
                RtsHomeManager.remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                getFluidCapacityBuckets(player),
                getUltimineLimit(player),
                canBypassHomeRadius(player)));
    }

    private static void syncRelatedPlayers(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String sharedKey = RtsProgressionPersistence.sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            syncToPlayer(player);
            return;
        }
        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            if (sharedKey.equals(RtsProgressionPersistence.sharedProgressionKey(onlinePlayer))) {
                syncToPlayer(onlinePlayer);
            }
        }
    }

    public record HomeAnchor(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }
}
