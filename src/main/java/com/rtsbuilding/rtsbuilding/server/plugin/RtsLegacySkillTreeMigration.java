package com.rtsbuilding.rtsbuilding.server.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.server.data.PlayerComponents;
import com.rtsbuilding.rtsbuilding.server.data.RtsSharedProgressionData;
import com.rtsbuilding.rtsbuilding.server.data.SaveScheduler;
import com.rtsbuilding.rtsbuilding.server.progression.RtsProgressionManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 鏃ф妧鑳芥爲鍒版彃浠剁郴缁熺殑涓€娆℃€ц縼绉诲櫒銆? *
 * <p>鏃х増鏈妸瑙ｉ攣鑺傜偣淇濆瓨鍦?{@code unlocked_nodes}銆傚綋鍓嶇増鏈互鎻掍欢鐗╁搧浣滀负鐢熷瓨骞宠　鍏ュ彛锛? * 鎵€浠ヨ繖閲屽湪鐜╁棣栨鐧诲綍鏂扮増鏈椂鎶婃棫鑺傜偣鎶樼畻涓哄凡瀹夎鎻掍欢锛屽苟鍐欏叆杩佺Щ鐗堟湰鏍囪銆? * 璇ョ被鍙鐞嗘棫鏁版嵁鍏煎锛屼笉鍙備笌鍚庣画鎻掍欢瀹夎銆佸嵏杞芥垨鍔熻兘鍒ゅ畾銆? */
final class RtsLegacySkillTreeMigration {
    private static final int MIGRATION_VERSION = 2;
    private static final String OLD_PERSISTENT_ROOT = "rtsbuilding_progression";
    private static final String NBT_UNLOCKED_NODES = "unlocked_nodes";
    private static final String NBT_PLUGIN_MIGRATION_VERSION = "plugin_migration_version";
    private static final String LEGACY_OWNER_NAME = "Legacy Skill Tree";

    private static final ResourceLocation CAMERA_CORE = node("camera_core");
    private static final ResourceLocation RADIUS_1 = node("radius_1");
    private static final ResourceLocation RADIUS_2 = node("radius_2");
    private static final ResourceLocation RADIUS_3 = node("radius_3");
    private static final ResourceLocation RADIUS_MAX = node("radius_max");
    private static final ResourceLocation STORAGE_LINK = node("storage_link");
    private static final ResourceLocation REMOTE_PLACE = node("remote_place");
    private static final ResourceLocation REMOTE_BREAK = node("remote_break");
    private static final ResourceLocation ROTATE_BLOCK = node("rotate_block");
    private static final ResourceLocation AUTO_STORE_MINED = node("auto_store_mined");
    private static final ResourceLocation FUNNEL = node("funnel");
    private static final ResourceLocation FLUID_BUFFER = node("fluid_buffer");
    private static final ResourceLocation REMOTE_GUI = node("remote_gui");
    private static final ResourceLocation CRAFT_TERMINAL = node("craft_terminal");
    private static final ResourceLocation JEI_TRANSFER = node("jei_transfer");
    private static final ResourceLocation ULTIMINE = node("ultimine");
    private static final ResourceLocation AREA_DESTROY = node("area_destroy");
    private static final ResourceLocation BLUEPRINTS = node("blueprints");
    private static final ResourceLocation FIELD_DEPLOYMENT = node("field_deployment");

    private RtsLegacySkillTreeMigration() {
    }

    static List<RtsPluginDefinition> migrate(ServerPlayer player) {
        if (player == null || !RtsProgressionManager.isEnabled()) {
            return List.of();
        }

        List<RtsPluginTeamService.StoredPlugin> installed = RtsPluginTeamService.installedPlugins(player);
        List<RtsPluginDefinition> added = new ArrayList<>();
        boolean changed = false;
        boolean needsHarvestTierCompatibility = false;

        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            RtsSharedProgressionData sharedData = RtsProgressionManager.sharedProgressionData(player);
            if (sharedData.pluginMigrationVersion(sharedKey) < MIGRATION_VERSION) {
                needsHarvestTierCompatibility = true;
                changed |= addMigratedPlugins(
                        player,
                        installed,
                        added,
                        sharedData.legacyUnlockedNodes(sharedKey),
                        null,
                        LEGACY_OWNER_NAME);
                sharedData.setPluginMigrationVersion(sharedKey, MIGRATION_VERSION);
            }
        }

        CompoundTag currentRoot = SaveScheduler.INSTANCE.player(player).get(PlayerComponents.PROGRESSION);
        CompoundTag oldPersistentRoot = player.getPersistentData().getCompound(OLD_PERSISTENT_ROOT);
        if (migrationVersion(currentRoot, oldPersistentRoot) < MIGRATION_VERSION) {
            needsHarvestTierCompatibility = true;
            LinkedHashSet<ResourceLocation> personalNodes = readUnlockedNodes(currentRoot);
            personalNodes.addAll(readUnlockedNodes(oldPersistentRoot));
            changed |= addMigratedPlugins(
                    player,
                    installed,
                    added,
                    personalNodes,
                    player.getUUID(),
                    player.getGameProfile().getName());
            currentRoot.putInt(NBT_PLUGIN_MIGRATION_VERSION, MIGRATION_VERSION);
            SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, currentRoot);
            if (!oldPersistentRoot.isEmpty()) {
                oldPersistentRoot.putInt(NBT_PLUGIN_MIGRATION_VERSION, MIGRATION_VERSION);
                player.getPersistentData().put(OLD_PERSISTENT_ROOT, oldPersistentRoot);
            }
        }

        if (needsHarvestTierCompatibility) {
            changed |= addLegacyHarvestTierIfNeeded(player, installed, added);
        }
        if (changed) {
            RtsPluginTeamService.saveInstalledPlugins(player, installed);
        }
        return List.copyOf(added);
    }

    /**
     * 鎻掍欢绯荤粺 v1 鐨勮寖鍥寸牬鍧忎笉闇€瑕佺嫭绔嬮噰鎺樼瓑绾ф彃浠躲€?     * 鏃у瓨妗ｅ崌绾ф椂琛ュ彂鍚屼竴璐＄尞鑰呭悕涓嬬殑鏈ㄧ骇鎻掍欢锛岄伩鍏嶅凡鏈夎兘鍔涙棤鏁呮秷澶便€?     */
    private static boolean addLegacyHarvestTierIfNeeded(ServerPlayer player,
            List<RtsPluginTeamService.StoredPlugin> installed,
            List<RtsPluginDefinition> added) {
        RtsPluginTeamService.StoredPlugin areaPlugin = null;
        for (RtsPluginTeamService.StoredPlugin entry : installed) {
            RtsPluginDefinition definition = RtsPluginRegistry.byId(entry.plugin().pluginId());
            if (definition == null) {
                continue;
            }
            if (definition.family() == RtsPluginFamily.HARVEST_TIER) {
                return false;
            }
            if (BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN.equals(definition.id())) {
                areaPlugin = entry;
            }
        }
        if (areaPlugin == null) {
            return false;
        }

        RtsPluginDefinition stoneTier = RtsPluginRegistry.byId(BuiltInRtsPluginCatalog.HARVEST_TIER_STONE);
        if (stoneTier == null) {
            return false;
        }
        RtsInstalledPlugin plugin = new RtsInstalledPlugin(
                stoneTier.id(),
                pluginStack(stoneTier),
                player.level().getGameTime());
        if (!RtsPluginTeamService.canAddWithoutTeamConflict(installed, plugin)) {
            return false;
        }
        installed.add(new RtsPluginTeamService.StoredPlugin(
                plugin, areaPlugin.ownerId(), areaPlugin.ownerName()));
        added.add(stoneTier);
        return true;
    }

    private static boolean addMigratedPlugins(ServerPlayer player, List<RtsPluginTeamService.StoredPlugin> installed,
            List<RtsPluginDefinition> added, Set<ResourceLocation> legacyNodes, UUID ownerId, String ownerName) {
        if (legacyNodes == null || legacyNodes.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (ResourceLocation pluginId : pluginsFor(legacyNodes)) {
            RtsPluginDefinition definition = RtsPluginRegistry.byId(pluginId);
            if (definition == null) {
                continue;
            }
            RtsInstalledPlugin plugin = new RtsInstalledPlugin(
                    definition.id(),
                    pluginStack(definition),
                    player.level().getGameTime());
            if (!RtsPluginTeamService.canAddWithoutTeamConflict(installed, plugin)) {
                continue;
            }
            installed.add(new RtsPluginTeamService.StoredPlugin(plugin, ownerId, ownerName));
            added.add(definition);
            changed = true;
        }
        return changed;
    }

    private static LinkedHashSet<ResourceLocation> pluginsFor(Set<ResourceLocation> nodes) {
        LinkedHashSet<ResourceLocation> plugins = new LinkedHashSet<>();
        if (!nodes.isEmpty()) {
            plugins.add(BuiltInRtsPluginCatalog.RTS_CONTROL_CORE);
        }
        if (containsAny(nodes, REMOTE_PLACE, REMOTE_BREAK, ROTATE_BLOCK, ULTIMINE, AREA_DESTROY, BLUEPRINTS)) {
            plugins.add(BuiltInRtsPluginCatalog.REMOTE_CONTROL_PLUGIN);
        }
        if (containsAny(nodes, STORAGE_LINK, AUTO_STORE_MINED, FUNNEL, FLUID_BUFFER, REMOTE_GUI,
                CRAFT_TERMINAL, JEI_TRANSFER)) {
            plugins.add(BuiltInRtsPluginCatalog.STORAGE_INTEGRATION_PLUGIN);
        }
        if (containsAny(nodes, CRAFT_TERMINAL, JEI_TRANSFER)) {
            plugins.add(BuiltInRtsPluginCatalog.CRAFT_TERMINAL_PLUGIN);
        }
        if (nodes.contains(ULTIMINE) || nodes.contains(AREA_DESTROY)) {
            plugins.add(BuiltInRtsPluginCatalog.CHAIN_BREAK_PLUGIN);
        }
        if (nodes.contains(AREA_DESTROY)) {
            plugins.add(BuiltInRtsPluginCatalog.AREA_DESTROY_PLUGIN);
        }
        if (nodes.contains(BLUEPRINTS)) {
            plugins.add(BuiltInRtsPluginCatalog.BLUEPRINT_PLUGIN);
        }
        if (nodes.contains(FIELD_DEPLOYMENT)) {
            plugins.add(BuiltInRtsPluginCatalog.FIELD_DEPLOYMENT_PLUGIN);
        }
        addHighestRangeExtension(nodes, plugins);
        return plugins;
    }

    private static void addHighestRangeExtension(Set<ResourceLocation> nodes, LinkedHashSet<ResourceLocation> plugins) {
        if (nodes.contains(RADIUS_MAX)) {
            plugins.add(BuiltInRtsPluginCatalog.RANGE_EXTENSION_MAX);
        } else if (nodes.contains(RADIUS_3)) {
            plugins.add(BuiltInRtsPluginCatalog.RANGE_EXTENSION_III);
        } else if (nodes.contains(RADIUS_2)) {
            plugins.add(BuiltInRtsPluginCatalog.RANGE_EXTENSION_II);
        } else if (nodes.contains(RADIUS_1)) {
            plugins.add(BuiltInRtsPluginCatalog.RANGE_EXTENSION_I);
        }
    }

    private static boolean containsAny(Set<ResourceLocation> nodes, ResourceLocation... ids) {
        for (ResourceLocation id : ids) {
            if (nodes.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static LinkedHashSet<ResourceLocation> readUnlockedNodes(CompoundTag root) {
        LinkedHashSet<ResourceLocation> nodes = new LinkedHashSet<>();
        if (root == null || root.isEmpty()) {
            return nodes;
        }
        ListTag list = root.getList(NBT_UNLOCKED_NODES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null && RtsbuildingMod.MODID.equals(id.getNamespace())) {
                nodes.add(id);
            }
        }
        return nodes;
    }

    private static int migrationVersion(CompoundTag currentRoot, CompoundTag oldPersistentRoot) {
        return Math.max(
                currentRoot == null ? 0 : currentRoot.getInt(NBT_PLUGIN_MIGRATION_VERSION),
                oldPersistentRoot == null ? 0 : oldPersistentRoot.getInt(NBT_PLUGIN_MIGRATION_VERSION));
    }

    private static ItemStack pluginStack(RtsPluginDefinition definition) {
        return new ItemStack(BuiltInRegistries.ITEM.get(definition.itemId()));
    }

    private static ResourceLocation node(String path) {
        return new ResourceLocation(RtsbuildingMod.MODID, path);
    }

    static Component migrationMessage(List<RtsPluginDefinition> added) {
        long count = added.stream().map(RtsPluginDefinition::id).distinct().count();
        return Component.translatable("message.rtsbuilding.plugin.legacy_migrated", count);
    }
}
