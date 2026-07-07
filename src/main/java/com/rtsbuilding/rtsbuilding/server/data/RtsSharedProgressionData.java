package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RtsSharedProgressionData extends SavedData {
    private static final String DATA_NAME = "rtsbuilding_shared_progression";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_GROUP = "group";
    private static final String KEY_HOME_POS = "home_pos";
    private static final String KEY_HOME_DIMENSION = "home_dimension";
    private static final String KEY_HOME_SET_GAME_TIME = "home_set_game_time";
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
        ListTag groups = tag.getList(KEY_GROUPS, Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag groupTag = groups.getCompound(i);
            String groupKey = groupTag.getString(KEY_GROUP);
            if (groupKey == null || groupKey.isBlank()) {
                continue;
            }

            SharedProgression progression = new SharedProgression();
            if (groupTag.contains(KEY_HOME_POS, Tag.TAG_LONG) && groupTag.contains(KEY_HOME_DIMENSION, Tag.TAG_STRING)) {
                ResourceLocation dimensionId = ResourceLocation.tryParse(groupTag.getString(KEY_HOME_DIMENSION));
                if (dimensionId != null) {
                    progression.homePos = BlockPos.of(groupTag.getLong(KEY_HOME_POS)).immutable();
                    progression.homeDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                    progression.homeSetGameTime = groupTag.getLong(KEY_HOME_SET_GAME_TIME);
                }
            }

            ListTag plugins = groupTag.getList(KEY_PLUGINS, Tag.TAG_COMPOUND);
            for (int j = 0; j < plugins.size(); j++) {
                CompoundTag pluginTag = plugins.getCompound(j);
                ResourceLocation pluginId = ResourceLocation.tryParse(pluginTag.getString(KEY_PLUGIN_ID));
                if (pluginId == null) {
                    continue;
                }
                ItemStack stack = ItemStack.parseOptional(registries, pluginTag.getCompound(KEY_PLUGIN_STACK));
                if (stack.isEmpty()) {
                    continue;
                }
                UUID owner = pluginTag.contains(KEY_PLUGIN_OWNER, Tag.TAG_INT_ARRAY)
                        ? pluginTag.getUUID(KEY_PLUGIN_OWNER)
                        : null;
                progression.plugins.add(new SharedPlugin(
                        pluginId,
                        stack,
                        pluginTag.getLong(KEY_PLUGIN_INSTALLED_GAME_TIME),
                        owner,
                        pluginTag.getString(KEY_PLUGIN_OWNER_NAME)));
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
                groupTag.putString(KEY_HOME_DIMENSION, progression.homeDimension.location().toString());
                groupTag.putLong(KEY_HOME_SET_GAME_TIME, progression.homeSetGameTime);
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
                        pluginTag.putUUID(KEY_PLUGIN_OWNER, plugin.ownerId());
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
            ResourceLocation pluginId,
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
        private final List<SharedPlugin> plugins = new ArrayList<>();
    }
}
