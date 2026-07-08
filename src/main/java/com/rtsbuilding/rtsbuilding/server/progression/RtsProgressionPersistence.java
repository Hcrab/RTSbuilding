package com.rtsbuilding.rtsbuilding.server.progression;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.ftb.RtsFtbCompat;
import com.rtsbuilding.rtsbuilding.compat.openpac.RtsOpenPacCompat;
import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.Team;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 进度系统??NBT 持久化与共享数据访问??
 * <p>包私有——仅??{@link RtsProgressionManager} ??{@link RtsHomeManager} 内部使用??
 */
final class RtsProgressionPersistence {

    static final String NBT_ROOT = "rtsbuilding_progression";
    static final String NBT_VERSION = "version";
    static final String NBT_UNLOCKED_NODES = "unlocked_nodes";
    static final String NBT_HOME_POS = "home_pos";
    static final String NBT_HOME_DIMENSION = "home_dimension";
    static final String NBT_HOME_SET_GAME_TIME = "home_set_game_time";

    private RtsProgressionPersistence() {
    }

    // ======================================================================
    //  NBT ??
    // ======================================================================

    static CompoundTag root(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(NBT_ROOT);
        if (root.isEmpty()) {
            root.putInt(NBT_VERSION, 1);
            player.getPersistentData().put(NBT_ROOT, root);
        }
        return root;
    }

    // ======================================================================
    //  共享进度??& 数据
    // ======================================================================

    static String sharedProgressionKey(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null
                || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get()) {
            return "";
        }
        String openPacTeamKey = RtsOpenPacCompat.progressionTeamKey(player);
        if (openPacTeamKey != null && !openPacTeamKey.isBlank()) {
            return openPacTeamKey;
        }
        String ftbTeamKey = RtsFtbCompat.progressionTeamKey(player);
        if (ftbTeamKey != null && !ftbTeamKey.isBlank()) {
            return ftbTeamKey;
        }
        Team vanillaTeam = player.getTeam();
        return vanillaTeam == null ? "" : "scoreboard:" + vanillaTeam.getName();
    }

    static String sharedProgressionLabel(ServerPlayer player) {
        if (!RtsProgressionManager.isEnabled() || player == null
                || !Config.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get()) {
            return "";
        }
        String openPacLabel = RtsOpenPacCompat.progressionTeamLabel(player);
        if (openPacLabel != null && !openPacLabel.isBlank()) {
            return openPacLabel;
        }
        String ftbLabel = RtsFtbCompat.progressionTeamLabel(player);
        if (ftbLabel != null && !ftbLabel.isBlank()) {
            return ftbLabel;
        }
        Team vanillaTeam = player.getTeam();
        return vanillaTeam == null ? "" : vanillaTeam.getName();
    }

    static RtsSharedProgressionData sharedProgressionData(ServerPlayer player) {
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        return RtsSharedProgressionData.get(overworld == null ? player.serverLevel() : overworld);
    }

    // ======================================================================
    // 解锁节点读写
    // ======================================================================

    static LinkedHashSet<ResourceLocation> unlockedNodes(ServerPlayer player) {
        String sharedKey = sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            LinkedHashSet<ResourceLocation> sharedUnlocked = sharedProgressionData(player).unlockedNodes(sharedKey);
            sharedUnlocked.addAll(personalUnlockedNodes(player));
            sharedUnlocked.removeIf(id -> !RtsProgressionNodes.contains(id));
            return sharedUnlocked;
        }
        return personalUnlockedNodes(player);
    }

    static LinkedHashSet<ResourceLocation> personalUnlockedNodes(ServerPlayer player) {
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

    static boolean ensureStarterUnlocked(Set<ResourceLocation> unlocked) {
        return unlocked.add(RtsProgressionNodes.CAMERA_CORE);
    }

    static void saveUnlockedNodes(ServerPlayer player, Set<ResourceLocation> unlocked) {
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

    // ======================================================================
    //  前置依赖检??
    // ======================================================================

    static boolean dependenciesMet(Set<ResourceLocation> unlocked, RtsProgressionNode node) {
        for (ResourceLocation dependency : node.dependencies()) {
            if (!unlocked.contains(dependency)) {
                return false;
            }
        }
        return true;
    }

    // ======================================================================
    //  消耗品检查与扣除
    // ======================================================================

    static boolean hasCosts(ServerPlayer player, List<RtsIngredientCost> costs) {
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

    static void consumeCosts(ServerPlayer player, List<RtsIngredientCost> costs) {
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
}
