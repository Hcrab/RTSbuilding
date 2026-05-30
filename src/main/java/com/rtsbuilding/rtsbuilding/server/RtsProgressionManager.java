package com.rtsbuilding.rtsbuilding.server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsFeature;
import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.progression.RtsUnlockEffect;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

import com.rtsbuilding.rtsbuilding.network.S2CRtsProgressionStatePayload;

public final class RtsProgressionManager {
    public static final int DEFAULT_MAX_ACTION_RADIUS_BLOCKS = 128;
    public static final int DEFAULT_FLUID_CAPACITY_BUCKETS = 100;
    public static final int DEFAULT_ULTIMINE_LIMIT = 256;
    public static final int HOME_SELECTION_RADIUS_BLOCKS = 34;

    private static final String NBT_ROOT = "rtsbuilding_progression";
    private static final String NBT_VERSION = "version";
    private static final String NBT_UNLOCKED_NODES = "unlocked_nodes";
    private static final String NBT_HOME_POS = "home_pos";
    private static final String NBT_HOME_DIMENSION = "home_dimension";
    private static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    private static final ConcurrentMap<UUID, HomeSelection> HOME_SELECTIONS = new ConcurrentHashMap<>();

    private RtsProgressionManager() {
    }

    public static boolean isEnabled() {
        return Config.ENABLE_SURVIVAL_PROGRESSION.get();
    }

    public static boolean canUse(ServerPlayer player, RtsFeature feature) {
        if (!isEnabled()) {
            return true;
        }
        if (player == null || feature == null) {
            return false;
        }
        return derive(player).features().contains(feature);
    }

    public static double getActionRadius(ServerPlayer player) {
        if (!isEnabled()) {
            return Config.maxActionRadiusBlocks();
        }
        return Math.max(1.0D, derive(player).radiusBlocks());
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
        return !isEnabled() || derive(player).bypassHomeRadius();
    }

    public static boolean hasHome(ServerPlayer player) {
        return getHome(player) != null;
    }

    public static HomeAnchor getHome(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            RtsSharedProgressionData.SharedHome sharedHome = sharedProgressionData(player).home(sharedKey);
            if (sharedHome != null) {
                return new HomeAnchor(sharedHome.pos(), sharedHome.dimension(), sharedHome.setGameTime());
            }
        }
        return personalHome(player);
    }

    private static HomeAnchor personalHome(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        CompoundTag root = root(player);
        if (!root.contains(NBT_HOME_POS) || !root.contains(NBT_HOME_DIMENSION)) {
            return null;
        }
        ResourceLocation dimensionId = ResourceLocation.tryParse(root.getString(NBT_HOME_DIMENSION));
        if (dimensionId == null) {
            return null;
        }
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        return new HomeAnchor(BlockPos.of(root.getLong(NBT_HOME_POS)).immutable(), dimension, root.getLong(NBT_HOME_SET_GAME_TIME));
    }

    public static boolean canAccessHomeRadius(ServerPlayer player, BlockPos pos) {
        if (!isEnabled() || canBypassHomeRadius(player)) {
            return true;
        }
        if (player == null || pos == null) {
            return false;
        }
        HomeAnchor home = getHome(player);
        if (home == null || !home.dimension().equals(player.serverLevel().dimension())) {
            return false;
        }
        double radius = getActionRadius(player);
        double dx = (pos.getX() + 0.5D) - (home.pos().getX() + 0.5D);
        double dz = (pos.getZ() + 0.5D) - (home.pos().getZ() + 0.5D);
        double halfExtent = radius + 8.0D;
        return Math.abs(dx) <= halfExtent && Math.abs(dz) <= halfExtent;
    }

    public static boolean canStartNormalRts(ServerPlayer player) {
        return !isEnabled() || hasHome(player);
    }

    public static boolean shouldStartHomeSelection(ServerPlayer player) {
        return isEnabled() && player != null && !hasHome(player) && canUse(player, RtsFeature.CAMERA);
    }

    public static void beginHomeSelection(ServerPlayer player) {
        if (player == null) {
            return;
        }
        int chunkX = player.blockPosition().getX() >> 4;
        int chunkZ = player.blockPosition().getZ() >> 4;
        HOME_SELECTIONS.put(player.getUUID(), new HomeSelection(player.serverLevel().dimension(), chunkX, chunkZ));
    }

    public static void endHomeSelection(ServerPlayer player) {
        if (player != null) {
            HOME_SELECTIONS.remove(player.getUUID());
        }
    }

    public static boolean isHomeSelectionActive(ServerPlayer player) {
        return player != null && HOME_SELECTIONS.containsKey(player.getUUID());
    }

    public static boolean canSelectHome(ServerPlayer player, BlockPos pos) {
        HomeSelection selection = player == null ? null : HOME_SELECTIONS.get(player.getUUID());
        if (selection == null || pos == null || !selection.dimension().equals(player.serverLevel().dimension())) {
            return false;
        }
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        return Math.abs(chunkX - selection.centerChunkX()) <= 1
                && Math.abs(chunkZ - selection.centerChunkZ()) <= 1;
    }

    public static boolean canChangeHome(ServerPlayer player) {
        if (!isEnabled()) {
            return true;
        }
        HomeAnchor home = getHome(player);
        if (home == null) {
            return true;
        }
        return unlockedNodes(player).contains(RtsProgressionNodes.FIELD_DEPLOYMENT);
    }

    public static long remainingHomeCooldownTicks(ServerPlayer player) {
        return 0L;
    }

    public static boolean commitHome(ServerPlayer player, BlockPos pos) {
        if (!isEnabled()) {
            return false;
        }
        if (player == null || pos == null || !canSelectHome(player, pos)) {
            return false;
        }
        if (hasHome(player) && !canChangeHome(player)) {
            return false;
        }
        String sharedKey = sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            CompoundTag root = root(player);
            root.putInt(NBT_VERSION, 1);
            root.putLong(NBT_HOME_POS, pos.immutable().asLong());
            root.putString(NBT_HOME_DIMENSION, player.serverLevel().dimension().location().toString());
            root.putLong(NBT_HOME_SET_GAME_TIME, player.serverLevel().getGameTime());
            player.getPersistentData().put(NBT_ROOT, root);
        } else {
            sharedProgressionData(player).setHome(
                    sharedKey,
                    pos,
                    player.serverLevel().dimension(),
                    player.serverLevel().getGameTime());
        }
        endHomeSelection(player);
        syncRelatedPlayers(player);
        return true;
    }

    public static UnlockResult unlockNode(ServerPlayer player, ResourceLocation nodeId) {
        if (!isEnabled()) {
            return UnlockResult.disabledResult();
        }
        RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
        if (node == null) {
            return UnlockResult.failure("Unknown RTS node.");
        }
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        ensureStarterUnlocked(unlocked);
        if (unlocked.contains(nodeId)) {
            return UnlockResult.failure("Already unlocked.");
        }
        for (ResourceLocation dependency : node.dependencies()) {
            if (!unlocked.contains(dependency)) {
                return UnlockResult.failure("Missing prerequisite.");
            }
        }
        List<RtsIngredientCost> costs = RtsProgressionNodes.costsFor(node);
        if (!hasCosts(player, costs)) {
            return UnlockResult.failure("Missing materials.");
        }
        consumeCosts(player, costs);
        unlocked.add(nodeId);
        saveUnlockedNodes(player, unlocked);
        syncRelatedPlayers(player);
        return UnlockResult.ok();
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (player == null) {
            return;
        }
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isBlank() && sharedProgressionData(player).home(sharedKey) == null) {
            HomeAnchor personalHome = personalHome(player);
            if (personalHome != null) {
                sharedProgressionData(player).setHome(
                        sharedKey,
                        personalHome.pos(),
                        personalHome.dimension(),
                        personalHome.setGameTime());
            }
        }
        if (ensureStarterUnlocked(unlocked) || !sharedKey.isBlank()) {
            saveUnlockedNodes(player, unlocked);
        }
        syncToPlayer(player);
    }

    public static void onPlayerLogout(ServerPlayer player) {
        endHomeSelection(player);
    }

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
        HomeAnchor home = getHome(player);
        LinkedHashSet<ResourceLocation> unlockedSet = unlockedNodes(player);
        if (ensureStarterUnlocked(unlockedSet)) {
            saveUnlockedNodes(player, unlockedSet);
        }
        List<String> unlocked = unlockedSet.stream().map(ResourceLocation::toString).toList();
        List<String> unlockable = RtsProgressionNodes.all().stream()
                .filter(node -> !unlockedSet.contains(node.id()))
                .filter(node -> dependenciesMet(unlockedSet, node))
                .filter(node -> hasCosts(player, RtsProgressionNodes.costsFor(node)))
                .map(node -> node.id().toString())
                .toList();
        PacketDistributor.sendToPlayer(player, new S2CRtsProgressionStatePayload(
                isEnabled(),
                home != null,
                home == null ? BlockPos.ZERO : home.pos(),
                home == null ? "" : home.dimension().location().toString(),
                remainingHomeCooldownTicks(player),
                (int) Math.round(getActionRadius(player)),
                derived.fluidCapacityBuckets(),
                derived.ultimineLimit(),
                derived.bypassHomeRadius(),
                unlocked,
                unlockable,
                costOverrides));
    }

    private static boolean dependenciesMet(Set<ResourceLocation> unlocked, RtsProgressionNode node) {
        for (ResourceLocation dependency : node.dependencies()) {
            if (!unlocked.contains(dependency)) {
                return false;
            }
        }
        return true;
    }

    private static DerivedCapabilities derive(ServerPlayer player) {
        LinkedHashSet<ResourceLocation> unlocked = unlockedNodes(player);
        ensureStarterUnlocked(unlocked);
        EnumSet<RtsFeature> features = EnumSet.noneOf(RtsFeature.class);
        int radius = 0;
        int fluidCapacity = 0;
        int ultimineLimit = 0;
        boolean bypassHome = false;
        for (ResourceLocation nodeId : unlocked) {
            RtsProgressionNode node = RtsProgressionNodes.get(nodeId);
            if (node == null) {
                continue;
            }
            for (RtsUnlockEffect effect : node.effects()) {
                switch (effect.type()) {
                    case UNLOCK_FEATURE -> {
                        if (effect.feature() != null) {
                            features.add(effect.feature());
                        }
                    }
                    case SET_RADIUS_BLOCKS -> {
                        int radiusValue = RtsProgressionNodes.RADIUS_MAX.equals(node.id())
                                ? Config.maxActionRadiusBlocks()
                                : effect.value();
                        radius = Math.max(radius, radiusValue);
                    }
                    case SET_FLUID_CAPACITY_BUCKETS -> fluidCapacity = Math.max(fluidCapacity, effect.value());
                    case SET_ULTIMINE_LIMIT -> ultimineLimit = Math.max(ultimineLimit, effect.value());
                    case BYPASS_HOME_RADIUS -> bypassHome = true;
                }
            }
        }
        return new DerivedCapabilities(features, radius <= 0 ? 16 : radius, fluidCapacity, ultimineLimit, bypassHome);
    }

    private static CompoundTag root(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) {
            root.putInt(NBT_VERSION, 1);
            player.getPersistentData().put(NBT_ROOT, root);
        }
        return root;
    }

    private static String sharedProgressionKey(ServerPlayer player) {
        if (!isEnabled() || player == null || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get()) {
            return "";
        }

        String ftbTeamKey = RtsFtbCompat.progressionTeamKey(player);
        if (ftbTeamKey != null && !ftbTeamKey.isBlank()) {
            return ftbTeamKey;
        }

        Team vanillaTeam = player.getTeam();
        return vanillaTeam == null ? "" : "scoreboard:" + vanillaTeam.getName();
    }

    private static RtsSharedProgressionData sharedProgressionData(ServerPlayer player) {
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        return RtsSharedProgressionData.get(overworld == null ? player.serverLevel() : overworld);
    }

    private static void syncRelatedPlayers(ServerPlayer player) {
        if (player == null) {
            return;
        }
        String sharedKey = sharedProgressionKey(player);
        if (sharedKey.isBlank()) {
            syncToPlayer(player);
            return;
        }
        for (ServerPlayer onlinePlayer : player.getServer().getPlayerList().getPlayers()) {
            if (sharedKey.equals(sharedProgressionKey(onlinePlayer))) {
                syncToPlayer(onlinePlayer);
            }
        }
    }

    private static LinkedHashSet<ResourceLocation> unlockedNodes(ServerPlayer player) {
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            LinkedHashSet<ResourceLocation> sharedUnlocked = sharedProgressionData(player).unlockedNodes(sharedKey);
            sharedUnlocked.addAll(personalUnlockedNodes(player));
            sharedUnlocked.removeIf(id -> !RtsProgressionNodes.contains(id));
            return sharedUnlocked;
        }

        return personalUnlockedNodes(player);
    }

    private static LinkedHashSet<ResourceLocation> personalUnlockedNodes(ServerPlayer player) {
        CompoundTag root = root(player);
        LinkedHashSet<ResourceLocation> unlocked = new LinkedHashSet<>();
        ListTag list = root.getList(NBT_UNLOCKED_NODES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && RtsProgressionNodes.contains(id)) {
                unlocked.add(id);
            }
        }
        return unlocked;
    }

    private static boolean ensureStarterUnlocked(Set<ResourceLocation> unlocked) {
        return unlocked.add(RtsProgressionNodes.CAMERA_CORE);
    }

    private static void saveUnlockedNodes(ServerPlayer player, Set<ResourceLocation> unlocked) {
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            LinkedHashSet<ResourceLocation> sanitized = new LinkedHashSet<>();
            for (ResourceLocation id : unlocked) {
                if (RtsProgressionNodes.contains(id)) {
                    sanitized.add(id);
                }
            }
            sharedProgressionData(player).saveUnlockedNodes(sharedKey, sanitized);
            return;
        }

        CompoundTag root = root(player);
        ListTag list = new ListTag();
        for (ResourceLocation id : unlocked) {
            if (RtsProgressionNodes.contains(id)) {
                list.add(StringTag.valueOf(id.toString()));
            }
        }
        root.put(NBT_UNLOCKED_NODES, list);
        player.getPersistentData().put(NBT_ROOT, root);
    }

    private static boolean hasCosts(ServerPlayer player, List<RtsIngredientCost> costs) {
        for (RtsIngredientCost cost : costs) {
            Item item = BuiltInRegistries.ITEM.get(cost.itemId());
            if (item == null || countItem(player, item) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    private static int countItem(ServerPlayer player, Item item) {
        int count = 0;
        NonNullList<ItemStack> items = player.getInventory().items;
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void consumeCosts(ServerPlayer player, List<RtsIngredientCost> costs) {
        for (RtsIngredientCost cost : costs) {
            Item item = BuiltInRegistries.ITEM.get(cost.itemId());
            int remaining = cost.count();
            NonNullList<ItemStack> items = player.getInventory().items;
            for (ItemStack stack : items) {
                if (remaining <= 0) {
                    break;
                }
                if (stack.isEmpty() || !stack.is(item)) {
                    continue;
                }
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        player.getInventory().setChanged();
    }

    public record HomeAnchor(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }

    private record HomeSelection(ResourceKey<Level> dimension, int centerChunkX, int centerChunkZ) {
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
