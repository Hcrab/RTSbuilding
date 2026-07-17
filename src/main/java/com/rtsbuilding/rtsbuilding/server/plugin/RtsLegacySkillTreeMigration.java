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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 旧技能树到插件系统的一次性迁移器。
 *
 * <p>旧版本把解锁节点保存在 {@code unlocked_nodes}。当前版本以插件物品作为生存平衡入口，
 * 所以这里在玩家首次登录新版本时把旧节点折算为已安装插件，并写入迁移版本标记。
 * 该类只处理旧数据兼容，不参与后续插件安装、卸载或功能判定。
 */
final class RtsLegacySkillTreeMigration {
    private static final int MIGRATION_VERSION = 1;
    private static final String OLD_PERSISTENT_ROOT = "rtsbuilding_progression";
    private static final String NBT_UNLOCKED_NODES = "unlocked_nodes";
    private static final String NBT_PLUGIN_MIGRATION_VERSION = "plugin_migration_version";
    private static final String LEGACY_OWNER_NAME = "Legacy Skill Tree";

    private static final Identifier CAMERA_CORE = node("camera_core");
    private static final Identifier RADIUS_1 = node("radius_1");
    private static final Identifier RADIUS_2 = node("radius_2");
    private static final Identifier RADIUS_3 = node("radius_3");
    private static final Identifier RADIUS_MAX = node("radius_max");
    private static final Identifier STORAGE_LINK = node("storage_link");
    private static final Identifier REMOTE_PLACE = node("remote_place");
    private static final Identifier REMOTE_BREAK = node("remote_break");
    private static final Identifier ROTATE_BLOCK = node("rotate_block");
    private static final Identifier AUTO_STORE_MINED = node("auto_store_mined");
    private static final Identifier FUNNEL = node("funnel");
    private static final Identifier FLUID_BUFFER = node("fluid_buffer");
    private static final Identifier REMOTE_GUI = node("remote_gui");
    private static final Identifier CRAFT_TERMINAL = node("craft_terminal");
    private static final Identifier JEI_TRANSFER = node("jei_transfer");
    private static final Identifier ULTIMINE = node("ultimine");
    private static final Identifier AREA_DESTROY = node("area_destroy");
    private static final Identifier BLUEPRINTS = node("blueprints");
    private static final Identifier FIELD_DEPLOYMENT = node("field_deployment");

    private RtsLegacySkillTreeMigration() {
    }

    static List<RtsPluginDefinition> migrate(ServerPlayer player) {
        if (player == null || !RtsProgressionManager.isEnabled()) {
            return List.of();
        }

        List<RtsPluginTeamService.StoredPlugin> installed = RtsPluginTeamService.installedPlugins(player);
        List<RtsPluginDefinition> added = new ArrayList<>();
        boolean changed = false;

        String sharedKey = RtsProgressionManager.sharedProgressionKey(player);
        if (!sharedKey.isBlank()) {
            RtsSharedProgressionData sharedData = RtsProgressionManager.sharedProgressionData(player);
            if (sharedData.pluginMigrationVersion(sharedKey) < MIGRATION_VERSION) {
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
        CompoundTag oldPersistentRoot = player.getPersistentData().getCompoundOrEmpty(OLD_PERSISTENT_ROOT);
        if (migrationVersion(currentRoot, oldPersistentRoot) < MIGRATION_VERSION) {
            LinkedHashSet<Identifier> personalNodes = readUnlockedNodes(currentRoot);
            personalNodes.addAll(readUnlockedNodes(oldPersistentRoot));
            changed |= addMigratedPlugins(
                    player,
                    installed,
                    added,
                    personalNodes,
                    player.getUUID(),
                    player.getGameProfile().name());
            currentRoot.putInt(NBT_PLUGIN_MIGRATION_VERSION, MIGRATION_VERSION);
            SaveScheduler.INSTANCE.player(player).set(PlayerComponents.PROGRESSION, currentRoot);
            if (!oldPersistentRoot.isEmpty()) {
                oldPersistentRoot.putInt(NBT_PLUGIN_MIGRATION_VERSION, MIGRATION_VERSION);
                player.getPersistentData().put(OLD_PERSISTENT_ROOT, oldPersistentRoot);
            }
        }

        if (changed) {
            RtsPluginTeamService.saveInstalledPlugins(player, installed);
        }
        return List.copyOf(added);
    }

    private static boolean addMigratedPlugins(ServerPlayer player, List<RtsPluginTeamService.StoredPlugin> installed,
            List<RtsPluginDefinition> added, Set<Identifier> legacyNodes, UUID ownerId, String ownerName) {
        if (legacyNodes == null || legacyNodes.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Identifier pluginId : pluginsFor(legacyNodes)) {
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

    private static LinkedHashSet<Identifier> pluginsFor(Set<Identifier> nodes) {
        LinkedHashSet<Identifier> plugins = new LinkedHashSet<>();
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

    private static void addHighestRangeExtension(Set<Identifier> nodes, LinkedHashSet<Identifier> plugins) {
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

    private static boolean containsAny(Set<Identifier> nodes, Identifier... ids) {
        for (Identifier id : ids) {
            if (nodes.contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static LinkedHashSet<Identifier> readUnlockedNodes(CompoundTag root) {
        LinkedHashSet<Identifier> nodes = new LinkedHashSet<>();
        if (root == null || root.isEmpty()) {
            return nodes;
        }
        ListTag list = root.getListOrEmpty(NBT_UNLOCKED_NODES);
        for (int i = 0; i < list.size(); i++) {
            Identifier id = Identifier.tryParse(list.getStringOr(i, ""));
            if (id != null && RtsbuildingMod.MODID.equals(id.getNamespace())) {
                nodes.add(id);
            }
        }
        return nodes;
    }

    private static int migrationVersion(CompoundTag currentRoot, CompoundTag oldPersistentRoot) {
        return Math.max(
                currentRoot == null ? 0 : currentRoot.getIntOr(NBT_PLUGIN_MIGRATION_VERSION, 0),
                oldPersistentRoot == null ? 0 : oldPersistentRoot.getIntOr(NBT_PLUGIN_MIGRATION_VERSION, 0));
    }

    private static ItemStack pluginStack(RtsPluginDefinition definition) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(definition.itemId()));
    }

    private static Identifier node(String path) {
        return Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, path);
    }

    static Component migrationMessage(List<RtsPluginDefinition> added) {
        long count = added.stream().map(RtsPluginDefinition::id).distinct().count();
        return Component.translatable("message.rtsbuilding.plugin.legacy_migrated", count);
    }
}
