package com.rtsbuilding.rtsbuilding.server.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 共享进度数据的世界存档管理。
 * 记录每个队伍（group）已解锁的科技节点和共享家的位置。
 */
public final class RtsSharedProgressionData extends SavedData {
    private static final String DATA_NAME = "rtsbuilding_shared_progression";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_GROUP = "group";
    private static final String KEY_UNLOCKED_NODES = "unlocked_nodes";
    private static final String KEY_HOME_POS = "home_pos";
    private static final String KEY_HOME_DIMENSION = "home_dimension";
    private static final String KEY_HOME_SET_GAME_TIME = "home_set_game_time";

    /** 存档数据工厂实例 */
    private static final Factory<RtsSharedProgressionData> FACTORY = new Factory<>(
            RtsSharedProgressionData::new,
            RtsSharedProgressionData::load);

    /** 队伍 → 共享进度 映射表 */
    private final Map<String, SharedProgression> groups = new HashMap<>();

    /** 创建新的空共享进度数据实例 */
    private RtsSharedProgressionData() {
    }

    /** 从 NBT 加载共享进度数据 */
    private static RtsSharedProgressionData load(CompoundTag tag, HolderLookup.Provider registries) {
        RtsSharedProgressionData data = new RtsSharedProgressionData();
        ListTag groups = tag.getList(KEY_GROUPS, Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag groupTag = groups.getCompound(i);
            String groupKey = groupTag.getString(KEY_GROUP);
            if (groupKey.isBlank()) {
                continue;
            }

            SharedProgression progression = new SharedProgression();
            ListTag unlockedNodes = groupTag.getList(KEY_UNLOCKED_NODES, Tag.TAG_STRING);
            for (int nodeIndex = 0; nodeIndex < unlockedNodes.size(); nodeIndex++) {
                ResourceLocation nodeId = ResourceLocation.tryParse(unlockedNodes.getString(nodeIndex));
                if (nodeId != null) {
                    progression.unlockedNodes.add(nodeId);
                }
            }

            if (groupTag.contains(KEY_HOME_POS, Tag.TAG_LONG) && groupTag.contains(KEY_HOME_DIMENSION, Tag.TAG_STRING)) {
                ResourceLocation dimensionId = ResourceLocation.tryParse(groupTag.getString(KEY_HOME_DIMENSION));
                if (dimensionId != null) {
                    progression.homePos = BlockPos.of(groupTag.getLong(KEY_HOME_POS)).immutable();
                    progression.homeDimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                    progression.homeSetGameTime = groupTag.getLong(KEY_HOME_SET_GAME_TIME);
                }
            }

            data.groups.put(groupKey, progression);
        }
        return data;
    }

    /**
     * 获取指定世界的共享进度数据。
     * 如果不存在则创建新的实例。
     */
    public static RtsSharedProgressionData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * 获取指定队伍已解锁的科技节点列表。
     *
     * @param groupKey 队伍标识
     * @return 已解锁节点 ID 集合，如果队伍标识无效则返回空集合
     */
    public LinkedHashSet<ResourceLocation> unlockedNodes(String groupKey) {
        if (groupKey == null || groupKey.isBlank()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(group(groupKey).unlockedNodes);
    }

    /**
     * 保存指定队伍已解锁的科技节点列表。
     *
     * @param groupKey      队伍标识
     * @param unlockedNodes 要保存的已解锁节点 ID 集合
     */
    public void saveUnlockedNodes(String groupKey, Set<ResourceLocation> unlockedNodes) {
        if (groupKey == null || groupKey.isBlank()) {
            return;
        }
        SharedProgression progression = group(groupKey);
        progression.unlockedNodes.clear();
        progression.unlockedNodes.addAll(unlockedNodes);
        setDirty();
    }

    /**
     * 获取指定队伍的共享家信息。
     *
     * @param groupKey 队伍标识
     * @return 共享家的位置与维度信息，如果未设置则返回 null
     */
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

    /**
     * 设置指定队伍的共享家位置。
     *
     * @param groupKey  队伍标识
     * @param pos       家的方块位置
     * @param dimension 所在维度
     * @param gameTime  设置时的游戏内时间
     */
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

    /** 获取或创建指定队伍的共享进度 */
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

            ListTag unlockedNodes = new ListTag();
            for (ResourceLocation nodeId : progression.unlockedNodes) {
                if (nodeId != null) {
                    unlockedNodes.add(StringTag.valueOf(nodeId.toString()));
                }
            }
            groupTag.put(KEY_UNLOCKED_NODES, unlockedNodes);

            if (progression.homePos != null && progression.homeDimension != null) {
                groupTag.putLong(KEY_HOME_POS, progression.homePos.asLong());
                groupTag.putString(KEY_HOME_DIMENSION, progression.homeDimension.location().toString());
                groupTag.putLong(KEY_HOME_SET_GAME_TIME, progression.homeSetGameTime);
            }

            groups.add(groupTag);
        }
        tag.put(KEY_GROUPS, groups);
        return tag;
    }

    /** 共享家信息记录 */
    public record SharedHome(BlockPos pos, ResourceKey<Level> dimension, long setGameTime) {
    }

    /** 单个队伍的共享进度数据 */
    private static final class SharedProgression {
        /** 已解锁的科技节点 ID 集合 */
        private final LinkedHashSet<ResourceLocation> unlockedNodes = new LinkedHashSet<>();
        /** 共享家的方块位置 */
        private BlockPos homePos;
        /** 共享家所在的维度 */
        private ResourceKey<Level> homeDimension;
        /** 设置共享家时的游戏内时间戳 */
        private long homeSetGameTime;
    }
}
