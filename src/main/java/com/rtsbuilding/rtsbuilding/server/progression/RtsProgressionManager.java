package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.progression.resolver.RtsCapabilityResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;
import com.rtsbuilding.rtsbuilding.server.plugin.RtsPluginService;

import java.util.*;

public final class RtsProgressionManager {
    public static final int DEFAULT_MAX_ACTION_RADIUS_BLOCKS = 128;
    public static final int DEFAULT_FLUID_CAPACITY_BUCKETS = 100;
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;
    public static final int HOME_SELECTION_RADIUS_BLOCKS = 34;
    public static final int HOME_RELOCATION_COOLDOWN_DAYS = 20;
    public static final long TICKS_PER_GAME_DAY = 24000L;
    public static final long HOME_RELOCATION_COOLDOWN_TICKS =
            HOME_RELOCATION_COOLDOWN_DAYS * TICKS_PER_GAME_DAY;

    private static final RtsCapabilityResolver RESOLVER =
            new RtsCapabilityResolver(RtsProgressionNodes.tree());

    private RtsProgressionManager() {
    }

    // ======================================================================
    //  启用???
    // ======================================================================

    public static boolean isEnabled() {
        return Config.ENABLE_SURVIVAL_PROGRESSION.get();
    }

    // ======================================================================
    //  能力派生（保持原????使用 RESOLVER??
    // ======================================================================

    private static DerivedCapabilities derive(ServerPlayer player) {
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
        RtsProgressionPersistence.ensureStarterUnlocked(unlocked);

        RtsCapabilityResolver.DerivedCapabilities caps = RESOLVER.resolve(unlocked);

        int radius = caps.actionRadius();
        if (unlocked.contains(RtsProgressionNodes.RADIUS_MAX)) {
            radius = Math.max(radius, Config.maxActionRadiusBlocks());
        }

        return new DerivedCapabilities(
                caps.features(),
                Math.max(16, radius),
                caps.fluidCapacityBuckets(),
                caps.ultimineLimit(),
                caps.bypassHomeRadius());
    }

    // ======================================================================
    //  特征 / 能力查询
    // ======================================================================

    public static boolean canUse(ServerPlayer player, RtsFeature feature) {
        return RtsPluginService.canUse(player, feature);
    }

    public static double getActionRadius(ServerPlayer player) {
        return RtsPluginService.actionRadius(player);
    }

    public static int getFluidCapacityBuckets(ServerPlayer player) {
        if (!isEnabled()) {
            return DEFAULT_FLUID_CAPACITY_BUCKETS;
        }
        return Math.max(0, derive(player).fluidCapacityBuckets());
    }

    public static int getUltimineLimit(ServerPlayer player) {
        if (!isEnabled()) {
            return DEFAULT_ULTIMINE_LIMIT;
        }
        return Math.max(0, derive(player).ultimineLimit());
    }

    public static boolean canBypassHomeRadius(ServerPlayer player) {
        return RtsPluginService.canBypassHomeRadius(player);
    }

    // ======================================================================
    //  ????委托??RtsHomeManager
    // ======================================================================

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
        return isEnabled() && player != null && !RtsHomeManager.hasHome(player) && canUse(player, RtsFeature.CAMERA);
    }

    public static String sharedProgressionKey(ServerPlayer player) {
        return RtsProgressionPersistence.sharedProgressionKey(player);
    }

    public static String sharedProgressionLabel(ServerPlayer player) {
        return RtsProgressionPersistence.sharedProgressionLabel(player);
    }

    public static com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData sharedProgressionData(ServerPlayer player) {
        return RtsProgressionPersistence.sharedProgressionData(player);
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

    // ======================================================================
    //  节点解锁
    // ======================================================================

    public static UnlockResult unlockNode(ServerPlayer player, ResourceLocation nodeId) {
        if (!isEnabled()) {
            return UnlockResult.disabledResult();
        }
        RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
        if (node == null) {
            return UnlockResult.failure("Unknown RTS node.");
        }
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
        RtsProgressionPersistence.ensureStarterUnlocked(unlocked);
        if (unlocked.contains(nodeId)) {
            return UnlockResult.failure("Already unlocked.");
        }
        for (ResourceLocation dependency : node.dependencies()) {
            if (!unlocked.contains(dependency)) {
                return UnlockResult.failure("Missing prerequisite.");
            }
        }
        List<RtsIngredientCost> costs = RtsProgressionNodes.costsFor(node);
        if (!RtsProgressionPersistence.hasCosts(player, costs)) {
            return UnlockResult.failure("Missing materials.");
        }
        RtsProgressionPersistence.consumeCosts(player, costs);
        unlocked.add(nodeId);
        RtsProgressionPersistence.saveUnlockedNodes(player, unlocked);
        syncRelatedPlayers(player);
        return UnlockResult.ok();
    }

    // ======================================================================
    //  玩家生命周期
    // ======================================================================

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LinkedHashSet<ResourceLocation> unlocked = RtsProgressionPersistence.unlockedNodes(player);
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
        if (RtsProgressionPersistence.ensureStarterUnlocked(unlocked) || !sharedKey.isBlank()) {
            RtsProgressionPersistence.saveUnlockedNodes(player, unlocked);
        }
        syncToPlayer(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        RtsHomeManager.endHomeSelection(player);
    }

    // ======================================================================
    //  同步
    // ======================================================================

    public static void syncToPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        List<String> costOverrides = Config.progressionCostOverrides().entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        if (!isEnabled()) {
            PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                    false,
                    false,
                    BlockPos.ZERO,
                    "",
                    0L,
                    Config.maxActionRadiusBlocks(),
                    DEFAULT_FLUID_CAPACITY_BUCKETS,
                    DEFAULT_ULTIMINE_LIMIT,
                    true,
                    List.of(),
                    List.of(),
                    costOverrides));
            return;
        }
        DerivedCapabilities derived = derive(player);
        HomeAnchor home = RtsHomeManager.getHome(player);
        LinkedHashSet<ResourceLocation> unlockedSet = RtsProgressionPersistence.unlockedNodes(player);
        if (RtsProgressionPersistence.ensureStarterUnlocked(unlockedSet)) {
            RtsProgressionPersistence.saveUnlockedNodes(player, unlockedSet);
        }
        List<String> unlocked = unlockedSet.stream().map(ResourceLocation::toString).toList();
        List<String> unlockable = RtsProgressionNodes.all().stream()
                .filter(node -> !unlockedSet.contains(node.id()))
                .filter(node -> RtsProgressionPersistence.dependenciesMet(unlockedSet, node))
                .filter(node -> RtsProgressionPersistence.hasCosts(player, RtsProgressionNodes.costsFor(node)))
                .map(node -> node.id().toString())
                .toList();
        PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                isEnabled(),
                home != null,
                home == null ? BlockPos.ZERO : home.pos(),
                home == null ? "" : home.dimension().location().toString(),
                RtsHomeManager.remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                derived.fluidCapacityBuckets(),
                derived.ultimineLimit(),
                derived.bypassHomeRadius(),
                unlocked,
                unlockable,
                costOverrides));
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

    // ======================================================================
    //  内部记录
    // ======================================================================

    public record HomeAnchor(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }

    private record DerivedCapabilities(
            EnumSet<RtsFeature> features,
            int radiusBlocks,
            int fluidCapacityBuckets,
            int ultimineLimit,
            boolean bypassHomeRadius) {
    }

    public record UnlockResult(boolean success, boolean disabled, String message) {
        private static UnlockResult ok() {
            return new UnlockResult(true, false, "");
        }

        private static UnlockResult disabledResult() {
            return new UnlockResult(false, true, "Survival progression is disabled.");
        }

        private static UnlockResult failure(String message) {
            return new UnlockResult(false, false, message);
        }

        public void notifyPlayer(ServerPlayer player) {
            if (!success && player != null && message != null && !message.isBlank()) {
                player.displayClientMessage(Component.literal(message), true);
            }
        }
    }
}
