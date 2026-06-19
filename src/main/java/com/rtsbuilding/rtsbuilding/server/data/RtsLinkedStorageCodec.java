package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 链接存储（linked-storage）部分的 NBT 编解码器，用于 {@link RtsStorageSession}。
 *
 * <p>本类负责序列化和反序列化会话中关于链接存储的引用、模式、优先级、
 * 背包 UUID 以及背包分离状态。支持现代 {@code linked_entries} 复合列
 * 表格式和遗留的 {@code linked_positions} + {@code linked_dimension} 格式。
 *
 * <p>在 Phase 1.3 架构重构中从 {@link RtsStorageSessionCodec} 提取而来。
 * 本类有意不处理内部流体、近期条目、快速槽位、GUI 绑定或浏览状态。
 */
public final class RtsLinkedStorageCodec {

    // ---- 链接存储的 NBT 键名（现代格式） ----

    /** 顶层复合列表，存储所有链接存储条目 */
    public static final String NBT_LINKED_ENTRIES = "linked_entries";
    private static final String NBT_LINKED_ENTRY_POS = "pos";
    private static final String NBT_LINKED_ENTRY_DIMENSION = "dimension";
    private static final String NBT_LINKED_ENTRY_MODE = "mode";
    private static final String NBT_LINKED_ENTRY_PRIORITY = "priority";
    private static final String NBT_LINKED_ENTRY_BACKPACK_UUID = "bpUuid";
    private static final String NBT_LINKED_ENTRY_BACKPACK_ITEM = "bpItem";
    private static final String NBT_LINKED_ENTRY_BACKPACK_DETACHED = "bpDetached";

    // ---- 链接存储的 NBT 键名（遗留格式） ----

    private static final String NBT_LINKED_POSITIONS = "linked_positions";
    private static final String NBT_LINKED_MODES = "linked_modes";
    private static final String NBT_LINKED_PRIORITIES = "linked_priorities";
    private static final String NBT_LINKED_DIMENSION = "linked_dimension";

    /** 工具类，私有构造防止实例化 */
    private RtsLinkedStorageCodec() {
    }

    // ======================================================================
    //  反序列化
    // ======================================================================

    /**
     * 从给定的 NBT 根节点中将链接存储状态加载到会话中。
     * 加载前会先清空现有的链接存储字段。
     */
    @SuppressWarnings("resource")
    public static void load(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.linkedStorageInfo.clear();

        byte[] linkedModes = root.getByteArray(NBT_LINKED_MODES);
        int[] linkedPriorities = root.getIntArray(NBT_LINKED_PRIORITIES);

        ResourceKey<Level> legacyDimension = null;
        String legacyDimensionId = root.getString(NBT_LINKED_DIMENSION);
        if (!legacyDimensionId.isBlank()) {
            legacyDimension = parseDimensionKey(legacyDimensionId);
        }

        ListTag linkedEntries = root.getList(NBT_LINKED_ENTRIES, Tag.TAG_COMPOUND);
        if (!linkedEntries.isEmpty()) {
            loadModernFormat(linkedEntries, session);
            return;
        }

        ServerLevel level = player.serverLevel();
        ResourceKey<Level> dimension = legacyDimension == null ? level.dimension() : legacyDimension;
        long[] linkedPackedPositions = root.getLongArray(NBT_LINKED_POSITIONS);
        for (int i = 0; i < linkedPackedPositions.length; i++) {
            LinkedStorageRef ref = new LinkedStorageRef(
                    dimension,
                    BlockPos.of(linkedPackedPositions[i]).immutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = i < linkedModes.length ? linkedModes[i] : RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL;
                int priority = i < linkedPriorities.length ? linkedPriorities[i] : 0;
                session.linkedStorageInfo.add(ref,
                        RtsLinkedStorageResolver.sanitizeLinkMode(linkMode),
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
            }
        }
    }

    // ======================================================================
    //  序列化
    // ======================================================================

    /**
     * 将会话中的链接存储状态序列化到给定的 NBT 根节点中。
     * 同时写入现代复合列表格式和遗留扁平数组格式，以确保向后兼容。
     */
    public static void save(RtsStorageSession session, CompoundTag root) {
        ListTag linkedEntries = new ListTag();
        long[] linkedPacked = new long[session.linkedStorageInfo.size()];
        byte[] linkedModes = new byte[session.linkedStorageInfo.size()];
        int[] linkedPriorities = new int[session.linkedStorageInfo.size()];
        for (int i = 0; i < session.linkedStorageInfo.size(); i++) {
            LinkedStorageRef ref = session.linkedStorageInfo.get(i);
            if (ref == null || ref.pos() == null || ref.dimension() == null) {
                continue;
            }
            byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(
                    session.linkedStorageInfo.getMode(ref));
            int priority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedStorageInfo.getPriority(ref));
            linkedPacked[i] = ref.pos().asLong();
            linkedModes[i] = linkMode;
            linkedPriorities[i] = priority;

            CompoundTag linkedTag = new CompoundTag();
            linkedTag.putLong(NBT_LINKED_ENTRY_POS, ref.pos().asLong());
            linkedTag.putString(NBT_LINKED_ENTRY_DIMENSION, ref.dimension().location().toString());
            linkedTag.putByte(NBT_LINKED_ENTRY_MODE, linkMode);
            linkedTag.putInt(NBT_LINKED_ENTRY_PRIORITY, priority);
            UUID backpackUuid = session.linkedStorageInfo.getBackpackUuid(ref);
            if (backpackUuid != null) {
                linkedTag.putUUID(NBT_LINKED_ENTRY_BACKPACK_UUID, backpackUuid);
            }
            String backpackItemId = session.linkedStorageInfo.getBackpackItemId(ref);
            if (isRegisteredItemId(backpackItemId)) {
                linkedTag.putString(NBT_LINKED_ENTRY_BACKPACK_ITEM, backpackItemId);
            }
            if (session.linkedStorageInfo.isDetached(ref)) {
                linkedTag.putBoolean(NBT_LINKED_ENTRY_BACKPACK_DETACHED, true);
            }
            linkedEntries.add(linkedTag);
        }
        root.put(NBT_LINKED_ENTRIES, linkedEntries);
        root.putLongArray(NBT_LINKED_POSITIONS, linkedPacked);
        root.putByteArray(NBT_LINKED_MODES, linkedModes);
        root.putIntArray(NBT_LINKED_PRIORITIES, linkedPriorities);

        if (!session.linkedStorageInfo.isEmpty()) {
            LinkedStorageRef first = session.linkedStorageInfo.get(0);
            if (first != null && first.dimension() != null) {
                root.putString(NBT_LINKED_DIMENSION, first.dimension().location().toString());
            }
        }
    }

    // ======================================================================
    //  工具方法
    // ======================================================================

    /**
     * 将维度 ID 字符串解析为 {@link ResourceKey<Level>}。
     *
     * @param dimensionId 维度 ID 字符串（例如 "minecraft:overworld"）
     * @return 维度键，如果输入为空或无效则返回 {@code null}
     */
    public static ResourceKey<Level> parseDimensionKey(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    // ======================================================================
    //  内部方法
    // ======================================================================

    /** 从现代复合列表格式加载链接存储条目 */
    private static void loadModernFormat(ListTag linkedEntries, RtsStorageSession session) {
        for (int i = 0; i < linkedEntries.size(); i++) {
            CompoundTag linkedTag = linkedEntries.getCompound(i);
            if (!linkedTag.contains(NBT_LINKED_ENTRY_POS, Tag.TAG_LONG)) {
                continue;
            }
            ResourceKey<Level> dimension = parseDimensionKey(
                    linkedTag.getString(NBT_LINKED_ENTRY_DIMENSION));
            if (dimension == null) {
                continue;
            }
            LinkedStorageRef ref = new LinkedStorageRef(
                    dimension,
                    BlockPos.of(linkedTag.getLong(NBT_LINKED_ENTRY_POS)).immutable());
            if (!session.linkedStorageInfo.contains(ref)) {
                byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(
                        linkedTag.getByte(NBT_LINKED_ENTRY_MODE));
                int priority = linkedTag.contains(NBT_LINKED_ENTRY_PRIORITY, Tag.TAG_INT)
                        ? linkedTag.getInt(NBT_LINKED_ENTRY_PRIORITY)
                        : 0;
                UUID backpackUuid = linkedTag.contains(NBT_LINKED_ENTRY_BACKPACK_UUID, Tag.TAG_INT_ARRAY)
                        ? linkedTag.getUUID(NBT_LINKED_ENTRY_BACKPACK_UUID)
                        : null;
                String backpackItemId = isRegisteredItemId(linkedTag.getString(NBT_LINKED_ENTRY_BACKPACK_ITEM))
                        ? linkedTag.getString(NBT_LINKED_ENTRY_BACKPACK_ITEM)
                        : null;
                session.linkedStorageInfo.add(ref, linkMode,
                        RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority),
                        backpackUuid, backpackItemId);
                if (linkedTag.getBoolean(NBT_LINKED_ENTRY_BACKPACK_DETACHED)) {
                    session.linkedStorageInfo.markDetached(ref);
                }
            }
        }
    }

    /** 判断物品 ID 是否已在物品注册表中注册 */
    private static boolean isRegisteredItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key);
    }
}
