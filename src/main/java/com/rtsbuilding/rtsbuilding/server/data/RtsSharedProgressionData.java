package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RtsSharedProgressionData extends SavedData {
    private static final String DATA_NAME = "rtsbuilding_shared_progression";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_GROUP = "group";
    private static final String KEY_HOME_POS = "home_pos";
    private static final String KEY_HOME_DIMENSION = "home_dimension";
    private static final String KEY_HOME_SET_GAME_TIME = "home_set_game_time";
    private static final String KEY_LEGACY_UNLOCKED_NODES = "unlocked_nodes";
    private static final String KEY_PLUGIN_MIGRATION_VERSION = "plugin_migration_version";
    private static final String KEY_PLUGINS = "plugins";
    private static final String KEY_PLUGIN_ID = "plugin_id";
    private static final String KEY_PLUGIN_STACK = "stack";
    private static final String KEY_PLUGIN_INSTALLED_GAME_TIME = "installed_game_time";
    private static final String KEY_PLUGIN_OWNER = "owner";
    private static final String KEY_PLUGIN_OWNER_NAME = "owner_name";

    private static final Factory<RtsSharedProgressionData> FACTORY = new Factory<>(
            RtsSharedProgressionData::new,
            RtsSharedProgressionData::load);

    private final Map<String, SharedProgression> groups = new HashMap<>();

    private RtsSharedProgressionData() {
    }

    private static RtsSharedProgressionData load(CompoundTag tag, HolderLookup.Provider registries) {
        RtsSharedProgressionData data = new RtsSharedProgressionData();
        ListTag groups = tag.getListOrEmpty(KEY_GROUPS);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag groupTag = groups.getCompoundOrEmpty(i);
            String groupKey = groupTag.getStringOr(KEY_GROUP, "");
            if (groupKey == null || groupKey.isBlank()) {
                continue;
            }

            SharedProgression progression = new SharedProgression();
            if (groupTag.contains(KEY_HOME_POS) && groupTag.contains(KEY_HOME_DIMENSION)) {
                Identifier dimensionId = Identifier.tryParse(groupTag.getStringOr(KEY_HOME_DIMENSION, ""));
                if (dimensionId != null) {
                    progression.homePos = BlockPos.of(groupTag.getLongOr(KEY_HOME_POS, 0L)).immutable();
                    progression.homeDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                    progression.homeSetGameTime = groupTag.getLongOr(KEY_HOME_SET_GAME_TIME, 0L);
                }
            }

            ListTag unlockedNodes = groupTag.getListOrEmpty(KEY_LEGACY_UNLOCKED_NODES);
            for (int nodeIndex = 0; nodeIndex < unlockedNodes.size(); nodeIndex++) {
                Identifier nodeId = Identifier.tryParse(unlockedNodes.getStringOr(nodeIndex, ""));
                if (nodeId != null) {
                    progression.legacyUnlockedNodes.add(nodeId);
                }
            }
            progression.pluginMigrationVersion = groupTag.getIntOr(KEY_PLUGIN_MIGRATION_VERSION, 0);

            ListTag plugins = groupTag.getListOrEmpty(KEY_PLUGINS);
            for (int j = 0; j < plugins.size(); j++) {
                CompoundTag pluginTag = plugins.getCompoundOrEmpty(j);
                Identifier pluginId = Identifier.tryParse(pluginTag.getStringOr(KEY_PLUGIN_ID, ""));
                if (pluginId == null) {
                    continue;
                }
                ItemStack stack = ItemStack.parseOptional(registries, pluginTag.getCompoundOrEmpty(KEY_PLUGIN_STACK));
                if (stack.isEmpty()) {
                    continue;
                }
                UUID owner = pluginTag.contains(KEY_PLUGIN_OWNER)
                        ? com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.getUuid(pluginTag, KEY_PLUGIN_OWNER)
                        : null;
                progression.plugins.add(new SharedPlugin(
                        pluginId,
                        stack,
                        pluginTag.getLongOr(KEY_PLUGIN_INSTALLED_GAME_TIME, 0L),
                        owner,
                        pluginTag.getStringOr(KEY_PLUGIN_OWNER_NAME, "")));
            }

            data.groups.put(groupKey, progression);
        }
        return data;
    }

    public static RtsSharedProgressionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public SharedHome home(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return null;
        }
        SharedProgression progression = this.groups.get(groupKey);
        if (progression == null || progression.homePos == null || progression.homeDimension == null) {
            return null;
        }
        return new SharedHome(progression.homePos, progression.homeDimension, progression.homeSetGameTime);
    }

    public void setHome(String groupKey, BlockPos pos, ResourceKey<Level> dimension, long gameTime) {
        if (groupKey == null || groupKey.isBlank() || pos == null || dimension == null) {
            return;
        }
        SharedProgression progression = group(groupKey);
        progression.homePos = pos.immutable();
        progression.homeDimension = dimension;
        progression.homeSetGameTime = gameTime;
        setDirty();
    }

    public List<SharedPlugin> plugins(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return List.of();
        }
        SharedProgression progression = this.groups.get(groupKey);
        if (progression == null || progression.plugins.isEmpty()) {
            return List.of();
        }
        return List.copyOf(progression.plugins);
    }

    public void setPlugins(String groupKey, List<SharedPlugin> plugins) {
        if (groupKey == null || groupKey.isBlank()) {
            return;
        }
        SharedProgression progression = group(groupKey);
        progression.plugins.clear();
        if (plugins != null) {
            for (SharedPlugin plugin : plugins) {
                if (plugin != null && plugin.pluginId() != null && !plugin.stack().isEmpty()) {
                    progression.plugins.add(plugin);
                }
            }
        }
        setDirty();
    }

    public LinkedHashSet<Identifier> legacyUnlockedNodes(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return new LinkedHashSet<>();
        }
        SharedProgression progression = this.groups.get(groupKey);
        if (progression == null || progression.legacyUnlockedNodes.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(progression.legacyUnlockedNodes);
    }

    public int pluginMigrationVersion(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return 0;
        }
        SharedProgression progression = this.groups.get(groupKey);
        return progression == null ? 0 : progression.pluginMigrationVersion;
    }

    public void setPluginMigrationVersion(String groupKey, int version) {
        if (groupKey == null || groupKey.isBlank()) {
            return;
        }
        group(groupKey).pluginMigrationVersion = Math.max(0, version);
        setDirty();
    }

    private SharedProgression group(String groupKey) {
        return this.groups.computeIfAbsent(groupKey, ignored -> new SharedProgression());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag groups = new ListTag();
        for (var entry : this.groups.entrySet()) {
            String groupKey = entry.getKey();
            SharedProgression progression = entry.getValue();
            if (groupKey == null || groupKey.isBlank() || progression == null) {
                continue;
            }

            CompoundTag groupTag = new CompoundTag();
            groupTag.putString(KEY_GROUP, groupKey);

            if (progression.homePos != null && progression.homeDimension != null) {
                groupTag.putLong(KEY_HOME_POS, progression.homePos.asLong());
                groupTag.putString(KEY_HOME_DIMENSION, progression.homeDimension.identifier().toString());
                groupTag.putLong(KEY_HOME_SET_GAME_TIME, progression.homeSetGameTime);
            }

            if (!progression.legacyUnlockedNodes.isEmpty()) {
                ListTag unlockedNodes = new ListTag();
                for (Identifier nodeId : progression.legacyUnlockedNodes) {
                    if (nodeId != null) {
                        unlockedNodes.add(StringTag.valueOf(nodeId.toString()));
                    }
                }
                groupTag.put(KEY_LEGACY_UNLOCKED_NODES, unlockedNodes);
            }
            if (progression.pluginMigrationVersion > 0) {
                groupTag.putInt(KEY_PLUGIN_MIGRATION_VERSION, progression.pluginMigrationVersion);
            }

            if (!progression.plugins.isEmpty()) {
                ListTag plugins = new ListTag();
                for (SharedPlugin plugin : progression.plugins) {
                    if (plugin == null || plugin.pluginId() == null || plugin.stack().isEmpty()) {
                        continue;
                    }
                    CompoundTag pluginTag = new CompoundTag();
                    pluginTag.putString(KEY_PLUGIN_ID, plugin.pluginId().toString());
                    pluginTag.put(KEY_PLUGIN_STACK, plugin.stack().copyWithCount(1).save(registries));
                    pluginTag.putLong(KEY_PLUGIN_INSTALLED_GAME_TIME, plugin.installedGameTime());
                    if (plugin.ownerId() != null) {
                        com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.putUuid(pluginTag, KEY_PLUGIN_OWNER, plugin.ownerId());
                    }
                    pluginTag.putString(KEY_PLUGIN_OWNER_NAME, plugin.ownerName());
                    plugins.add(pluginTag);
                }
                groupTag.put(KEY_PLUGINS, plugins);
            }

            groups.add(groupTag);
        }
        tag.put(KEY_GROUPS, groups);
        return tag;
    }

    public record SharedHome(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }

    public record SharedPlugin(
            Identifier pluginId,
            ItemStack stack,
            long installedGameTime,
            UUID ownerId,
            String ownerName) {
        public SharedPlugin {
            stack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
            ownerName = ownerName == null ? "" : ownerName;
        }
    }

    private static final class SharedProgression {
        private BlockPos homePos;
        private ResourceKey<Level> homeDimension;
        private long homeSetGameTime;
        private final Set<Identifier> legacyUnlockedNodes = new LinkedHashSet<>();
        private int pluginMigrationVersion;
        private final List<SharedPlugin> plugins = new ArrayList<>();
    }
}
