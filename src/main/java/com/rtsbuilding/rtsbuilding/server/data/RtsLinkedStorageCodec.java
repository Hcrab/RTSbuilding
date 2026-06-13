package com.rtsbuilding.rtsbuilding.server.data;

import com.rtsbuilding.rtsbuilding.server.storage.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * NBT codec for the linked-storage portion of an {@link RtsStorageSession}.
 *
 * <p>This class serialises and deserialises the session's linked storage
 * references, modes, priorities, backpack UUIDs, and detached-backpack state.
 * It handles both the modern {@code linked_entries} compound-list format and
 * the legacy {@code linked_positions} + {@code linked_dimension} format.
 *
 * <p>Extracted from {@link RtsStorageSessionCodec} during the Phase 1.3
 * architecture refactor. This class deliberately does not handle internal
 * fluids, recent entries, quick slots, GUI bindings, or browser state.
 */
public final class RtsLinkedStorageCodec {

    // ---- NBT keys for linked storage (modern format) ----

    /** Top-level compound list of linked storage entries. */
    public static final String NBT_LINKED_ENTRIES = "linked_entries";
    private static final String NBT_LINKED_ENTRY_POS = "pos";
    private static final String NBT_LINKED_ENTRY_DIMENSION = "dimension";
    private static final String NBT_LINKED_ENTRY_MODE = "mode";
    private static final String NBT_LINKED_ENTRY_PRIORITY = "priority";
    private static final String NBT_LINKED_ENTRY_BACKPACK_UUID = "bpUuid";
    private static final String NBT_LINKED_ENTRY_BACKPACK_ITEM = "bpItem";
    private static final String NBT_LINKED_ENTRY_BACKPACK_DETACHED = "bpDetached";

    // ---- NBT keys for linked storage (legacy format) ----

    private static final String NBT_LINKED_POSITIONS = "linked_positions";
    private static final String NBT_LINKED_MODES = "linked_modes";
    private static final String NBT_LINKED_PRIORITIES = "linked_priorities";
    private static final String NBT_LINKED_DIMENSION = "linked_dimension";

    private RtsLinkedStorageCodec() {
    }

    // ======================================================================
    //  Deserialise
    // ======================================================================

    /**
     * Loads linked-storage state from the given NBT root into the session.
     * Clears existing linked-storage fields before loading.
     */
    public static void load(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.linkedStorages.clear();
        session.linkedNames.clear();
        session.linkedModes.clear();
        session.linkedPriorities.clear();
        session.linkedBackpackUuids.clear();
        session.linkedBackpackItemIds.clear();
        session.detachedBackpackRefs.clear();

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

        ResourceKey<Level> dimension = legacyDimension == null ? player.serverLevel().dimension() : legacyDimension;
        long[] linkedPackedPositions = root.getLongArray(NBT_LINKED_POSITIONS);
        for (int i = 0; i < linkedPackedPositions.length; i++) {
            LinkedStorageRef ref = new LinkedStorageRef(
                    dimension,
                    BlockPos.of(linkedPackedPositions[i]).immutable());
            if (!session.linkedStorages.contains(ref)) {
                session.linkedStorages.add(ref);
                byte linkMode = i < linkedModes.length ? linkedModes[i] : RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL;
                session.linkedModes.put(ref, RtsLinkedStorageResolver.sanitizeLinkMode(linkMode));
                int priority = i < linkedPriorities.length ? linkedPriorities[i] : 0;
                session.linkedPriorities.put(ref, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
            }
        }
    }

    // ======================================================================
    //  Serialise
    // ======================================================================

    /**
     * Serialises linked-storage state from the session into the given NBT root.
     * Writes both the modern compound-list format and legacy flat arrays for
     * backward compatibility.
     */
    public static void save(RtsStorageSession session, CompoundTag root) {
        ListTag linkedEntries = new ListTag();
        long[] linkedPacked = new long[session.linkedStorages.size()];
        byte[] linkedModes = new byte[session.linkedStorages.size()];
        int[] linkedPriorities = new int[session.linkedStorages.size()];
        for (int i = 0; i < session.linkedStorages.size(); i++) {
            LinkedStorageRef ref = session.linkedStorages.get(i);
            if (ref == null || ref.pos() == null || ref.dimension() == null) {
                continue;
            }
            byte linkMode = RtsLinkedStorageResolver.sanitizeLinkMode(
                    session.linkedModes.getOrDefault(ref, RtsLinkedStorageResolver.LINK_MODE_BIDIRECTIONAL));
            int priority = RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(
                    session.linkedPriorities.getOrDefault(ref, 0));
            linkedPacked[i] = ref.pos().asLong();
            linkedModes[i] = linkMode;
            linkedPriorities[i] = priority;

            CompoundTag linkedTag = new CompoundTag();
            linkedTag.putLong(NBT_LINKED_ENTRY_POS, ref.pos().asLong());
            linkedTag.putString(NBT_LINKED_ENTRY_DIMENSION, ref.dimension().location().toString());
            linkedTag.putByte(NBT_LINKED_ENTRY_MODE, linkMode);
            linkedTag.putInt(NBT_LINKED_ENTRY_PRIORITY, priority);
            UUID backpackUuid = session.linkedBackpackUuids.get(ref);
            if (backpackUuid != null) {
                linkedTag.putUUID(NBT_LINKED_ENTRY_BACKPACK_UUID, backpackUuid);
            }
            String backpackItemId = session.linkedBackpackItemIds.get(ref);
            if (isRegisteredItemId(backpackItemId)) {
                linkedTag.putString(NBT_LINKED_ENTRY_BACKPACK_ITEM, backpackItemId);
            }
            if (session.detachedBackpackRefs.contains(ref)) {
                linkedTag.putBoolean(NBT_LINKED_ENTRY_BACKPACK_DETACHED, true);
            }
            linkedEntries.add(linkedTag);
        }
        root.put(NBT_LINKED_ENTRIES, linkedEntries);
        root.putLongArray(NBT_LINKED_POSITIONS, linkedPacked);
        root.putByteArray(NBT_LINKED_MODES, linkedModes);
        root.putIntArray(NBT_LINKED_PRIORITIES, linkedPriorities);

        if (!session.linkedStorages.isEmpty()) {
            LinkedStorageRef first = session.linkedStorages.get(0);
            if (first != null && first.dimension() != null) {
                root.putString(NBT_LINKED_DIMENSION, first.dimension().location().toString());
            }
        }
    }

    // ======================================================================
    //  Utilities
    // ======================================================================

    /**
     * Parses a dimension ID string into a {@link ResourceKey<Level>}.
     *
     * @param dimensionId the dimension ID string (e.g. "minecraft:overworld")
     * @return the dimension key, or {@code null} if the input is blank or invalid
     */
    public static ResourceKey<Level> parseDimensionKey(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return null;
        }
        ResourceLocation key = ResourceLocation.tryParse(dimensionId);
        return key == null ? null : ResourceKey.create(Registries.DIMENSION, key);
    }

    // ======================================================================
    //  Internals
    // ======================================================================

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
            if (!session.linkedStorages.contains(ref)) {
                session.linkedStorages.add(ref);
                session.linkedModes.put(ref, RtsLinkedStorageResolver.sanitizeLinkMode(
                        linkedTag.getByte(NBT_LINKED_ENTRY_MODE)));
                int priority = linkedTag.contains(NBT_LINKED_ENTRY_PRIORITY, Tag.TAG_INT)
                        ? linkedTag.getInt(NBT_LINKED_ENTRY_PRIORITY)
                        : 0;
                session.linkedPriorities.put(ref, RtsLinkedStorageResolver.sanitizeLinkedStoragePriority(priority));
                if (linkedTag.contains(NBT_LINKED_ENTRY_BACKPACK_UUID, Tag.TAG_INT_ARRAY)) {
                    session.linkedBackpackUuids.put(ref, linkedTag.getUUID(NBT_LINKED_ENTRY_BACKPACK_UUID));
                }
                String backpackItemId = linkedTag.getString(NBT_LINKED_ENTRY_BACKPACK_ITEM);
                if (isRegisteredItemId(backpackItemId)) {
                    session.linkedBackpackItemIds.put(ref, backpackItemId);
                }
                if (linkedTag.getBoolean(NBT_LINKED_ENTRY_BACKPACK_DETACHED)) {
                    session.detachedBackpackRefs.add(ref);
                }
            }
        }
    }

    private static boolean isRegisteredItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        return key != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key);
    }
}
