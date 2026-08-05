package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.server.task.persistence.NbtCompat;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 1.12.2 的全服共享 RTS 进度数据。维度以整数 ID 表示。 */
public final class RtsSharedProgressionData extends WorldSavedData {
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

    private final Map<String, SharedProgression> groups = new HashMap<String, SharedProgression>();

    public RtsSharedProgressionData() {
        this(DATA_NAME);
    }

    /** MapStorage 反射加载所需的 String 构造器。 */
    public RtsSharedProgressionData(String name) {
        super(name);
    }

    public static RtsSharedProgressionData get(WorldServer level) {
        MapStorage storage = level.mapStorage;
        RtsSharedProgressionData data = (RtsSharedProgressionData) storage.loadData(
                RtsSharedProgressionData.class, DATA_NAME);
        if (data == null) {
            data = new RtsSharedProgressionData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        groups.clear();
        NBTTagList encodedGroups = tag.getTagList(KEY_GROUPS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < encodedGroups.tagCount(); i++) {
            NBTTagCompound groupTag = encodedGroups.getCompoundTagAt(i);
            String groupKey = groupTag.getString(KEY_GROUP);
            if (isBlank(groupKey)) continue;

            SharedProgression progression = new SharedProgression();
            if (groupTag.hasKey(KEY_HOME_POS, Constants.NBT.TAG_LONG)
                    && groupTag.hasKey(KEY_HOME_DIMENSION, Constants.NBT.TAG_STRING)) {
                Integer dimension = parseDimension(groupTag.getString(KEY_HOME_DIMENSION));
                if (dimension != null) {
                    progression.homePos = BlockPos.fromLong(groupTag.getLong(KEY_HOME_POS));
                    progression.homeDimension = dimension;
                    progression.homeSetGameTime = groupTag.getLong(KEY_HOME_SET_GAME_TIME);
                }
            }

            NBTTagList unlocked = groupTag.getTagList(
                    KEY_LEGACY_UNLOCKED_NODES, Constants.NBT.TAG_STRING);
            for (int j = 0; j < unlocked.tagCount(); j++) {
                ResourceLocation id = parseResourceLocation(unlocked.getStringTagAt(j));
                if (id != null) progression.legacyUnlockedNodes.add(id);
            }
            progression.pluginMigrationVersion = groupTag.getInteger(KEY_PLUGIN_MIGRATION_VERSION);

            NBTTagList plugins = groupTag.getTagList(KEY_PLUGINS, Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < plugins.tagCount(); j++) {
                NBTTagCompound pluginTag = plugins.getCompoundTagAt(j);
                ResourceLocation pluginId = parseResourceLocation(pluginTag.getString(KEY_PLUGIN_ID));
                ItemStack stack = ItemStack.loadItemStackFromNBT(
                        pluginTag.getCompoundTag(KEY_PLUGIN_STACK));
                if (pluginId == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) continue;
                UUID owner = NbtCompat.hasUuid(pluginTag, KEY_PLUGIN_OWNER)
                        ? NbtCompat.getUuid(pluginTag, KEY_PLUGIN_OWNER) : null;
                progression.plugins.add(new SharedPlugin(pluginId, stack,
                        pluginTag.getLong(KEY_PLUGIN_INSTALLED_GAME_TIME), owner,
                        pluginTag.getString(KEY_PLUGIN_OWNER_NAME)));
            }
            groups.put(groupKey, progression);
        }
    }

    public SharedHome home(String groupKey) {
        if (isBlank(groupKey)) return null;
        SharedProgression progression = groups.get(groupKey);
        if (progression == null || progression.homePos == null
                || progression.homeDimension == null) return null;
        return new SharedHome(progression.homePos, progression.homeDimension,
                progression.homeSetGameTime);
    }

    public void setHome(String groupKey, BlockPos pos, int dimension, long gameTime) {
        if (isBlank(groupKey) || pos == null) return;
        SharedProgression progression = group(groupKey);
        progression.homePos = pos.toImmutable();
        progression.homeDimension = dimension;
        progression.homeSetGameTime = gameTime;
        markDirty();
    }

    public List<SharedPlugin> plugins(String groupKey) {
        if (isBlank(groupKey)) return Collections.emptyList();
        SharedProgression progression = groups.get(groupKey);
        if (progression == null || progression.plugins.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<SharedPlugin>(progression.plugins));
    }

    public void setPlugins(String groupKey, List<SharedPlugin> plugins) {
        if (isBlank(groupKey)) return;
        SharedProgression progression = group(groupKey);
        progression.plugins.clear();
        if (plugins != null) {
            for (SharedPlugin plugin : plugins) {
                if (plugin != null && plugin.pluginId() != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(plugin.stack())) {
                    progression.plugins.add(plugin);
                }
            }
        }
        markDirty();
    }

    public LinkedHashSet<ResourceLocation> legacyUnlockedNodes(String groupKey) {
        SharedProgression progression = isBlank(groupKey) ? null : groups.get(groupKey);
        return progression == null
                ? new LinkedHashSet<ResourceLocation>()
                : new LinkedHashSet<ResourceLocation>(progression.legacyUnlockedNodes);
    }

    public int pluginMigrationVersion(String groupKey) {
        SharedProgression progression = isBlank(groupKey) ? null : groups.get(groupKey);
        return progression == null ? 0 : progression.pluginMigrationVersion;
    }

    public void setPluginMigrationVersion(String groupKey, int version) {
        if (isBlank(groupKey)) return;
        group(groupKey).pluginMigrationVersion = Math.max(0, version);
        markDirty();
    }

    private SharedProgression group(String groupKey) {
        SharedProgression result = groups.get(groupKey);
        if (result == null) {
            result = new SharedProgression();
            groups.put(groupKey, result);
        }
        return result;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        NBTTagList encodedGroups = new NBTTagList();
        for (Map.Entry<String, SharedProgression> entry : groups.entrySet()) {
            String groupKey = entry.getKey();
            SharedProgression progression = entry.getValue();
            if (isBlank(groupKey) || progression == null) continue;

            NBTTagCompound groupTag = new NBTTagCompound();
            groupTag.setString(KEY_GROUP, groupKey);
            if (progression.homePos != null && progression.homeDimension != null) {
                groupTag.setLong(KEY_HOME_POS, progression.homePos.toLong());
                groupTag.setString(KEY_HOME_DIMENSION, dimensionName(progression.homeDimension));
                groupTag.setLong(KEY_HOME_SET_GAME_TIME, progression.homeSetGameTime);
            }

            if (!progression.legacyUnlockedNodes.isEmpty()) {
                NBTTagList unlocked = new NBTTagList();
                for (ResourceLocation id : progression.legacyUnlockedNodes) {
                    if (id != null) unlocked.appendTag(new NBTTagString(id.toString()));
                }
                groupTag.setTag(KEY_LEGACY_UNLOCKED_NODES, unlocked);
            }
            if (progression.pluginMigrationVersion > 0) {
                groupTag.setInteger(KEY_PLUGIN_MIGRATION_VERSION, progression.pluginMigrationVersion);
            }

            if (!progression.plugins.isEmpty()) {
                NBTTagList plugins = new NBTTagList();
                for (SharedPlugin plugin : progression.plugins) {
                    if (plugin == null || plugin.pluginId() == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(plugin.stack())) continue;
                    NBTTagCompound pluginTag = new NBTTagCompound();
                    pluginTag.setString(KEY_PLUGIN_ID, plugin.pluginId().toString());
                    pluginTag.setTag(KEY_PLUGIN_STACK, copyOne(plugin.stack()).writeToNBT(new NBTTagCompound()));
                    pluginTag.setLong(KEY_PLUGIN_INSTALLED_GAME_TIME, plugin.installedGameTime());
                    if (plugin.ownerId() != null) {
                        NbtCompat.setUuid(pluginTag, KEY_PLUGIN_OWNER, plugin.ownerId());
                    }
                    pluginTag.setString(KEY_PLUGIN_OWNER_NAME, plugin.ownerName());
                    plugins.appendTag(pluginTag);
                }
                groupTag.setTag(KEY_PLUGINS, plugins);
            }
            encodedGroups.appendTag(groupTag);
        }
        tag.setTag(KEY_GROUPS, encodedGroups);
    }

    private static ItemStack copyOne(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResourceLocation parseResourceLocation(String value) {
        if (isBlank(value)) return null;
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Integer parseDimension(String value) {
        if ("minecraft:overworld".equals(value)) return 0;
        if ("minecraft:the_nether".equals(value)) return -1;
        if ("minecraft:the_end".equals(value)) return 1;
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String dimensionName(int dimension) {
        if (dimension == 0) return "minecraft:overworld";
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return Integer.toString(dimension);
    }

    public static final class SharedHome {
        private final BlockPos pos;
        private final int dimension;
        private final long setGameTime;

        public SharedHome(BlockPos pos, int dimension, long setGameTime) {
            this.pos = pos;
            this.dimension = dimension;
            this.setGameTime = setGameTime;
        }

        public BlockPos pos() { return pos; }
        public int dimension() { return dimension; }
        public long setGameTime() { return setGameTime; }
    }

    public static final class SharedPlugin {
        private final ResourceLocation pluginId;
        private final ItemStack stack;
        private final long installedGameTime;
        private final UUID ownerId;
        private final String ownerName;

        public SharedPlugin(ResourceLocation pluginId, ItemStack stack, long installedGameTime,
                UUID ownerId, String ownerName) {
            this.pluginId = pluginId;
            this.stack = stack == null ? null : copyOne(stack);
            this.installedGameTime = installedGameTime;
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? "" : ownerName;
        }

        public ResourceLocation pluginId() { return pluginId; }
        public ItemStack stack() { return stack; }
        public long installedGameTime() { return installedGameTime; }
        public UUID ownerId() { return ownerId; }
        public String ownerName() { return ownerName; }
    }

    private static final class SharedProgression {
        private BlockPos homePos;
        private Integer homeDimension;
        private long homeSetGameTime;
        private final Set<ResourceLocation> legacyUnlockedNodes = new LinkedHashSet<ResourceLocation>();
        private int pluginMigrationVersion;
        private final List<SharedPlugin> plugins = new ArrayList<SharedPlugin>();
    }
}
