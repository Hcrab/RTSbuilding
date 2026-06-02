package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * NBT codec for {@link RtsStorageSession}.
 *
 * <p>This class owns the saved field names, default values, validation, and old
 * save migration for the RTS storage session. It deliberately does not resolve
 * block entities, query capabilities, refresh storage pages, send packets, or
 * decide whether a player may use a linked block. Those runtime decisions still
 * belong to {@link RtsStorageManager} and the future resolver/service modules.
 *
 * <p>Keep backward compatibility here. The modern format stores each linked
 * storage as a dimension+position compound in {@code linked_entries}; older
 * saves used {@code linked_positions} plus one {@code linked_dimension}. Both
 * must keep loading until a deliberate save-format migration says otherwise.
 *
 * <p>The Forge 1.20.1 line intentionally omits NeoForge-only BD network cache
 * fields. Do not add loader-specific fields here unless that loader branch has
 * the matching runtime integration.
 */
final class RtsStorageSessionCodec {
    static final String ROOT_KEY = "rtsbuilding_storage_session";

    private static final String NBT_LINKED_ENTRIES = "linked_entries";
    private static final String NBT_LINKED_ENTRY_POS = "pos";
    private static final String NBT_LINKED_ENTRY_DIMENSION = "dimension";
    private static final String NBT_LINKED_ENTRY_MODE = "mode";
    private static final String NBT_LINKED_POSITIONS = "linked_positions";
    private static final String NBT_LINKED_MODES = "linked_modes";
    private static final String NBT_LINKED_DIMENSION = "linked_dimension";
    private static final String NBT_INTERNAL_FLUIDS = "internal_fluids";
    private static final String NBT_FLUID_ID = "id";
    private static final String NBT_FLUID_AMOUNT = "amount";
    private static final String NBT_RECENT_ENTRIES = "recent_entries";
    private static final String NBT_RECENT_ENTRY_ID = "id";
    private static final String NBT_RECENT_ENTRY_AMOUNT = "amount";
    private static final String NBT_RECENT_ENTRY_CAPACITY = "capacity";
    private static final String NBT_RECENT_ENTRY_KIND = "kind";
    private static final String NBT_QUICK_SLOTS = "quick_slots";
    private static final String NBT_QUICK_SLOT_INDEX = "slot";
    private static final String NBT_QUICK_SLOT_ITEM_ID = "item_id";
    private static final String NBT_GUI_BINDINGS = "gui_bindings";
    private static final String NBT_GUI_BINDING_SLOT = "slot";
    private static final String NBT_GUI_BINDING_POS = "pos";
    private static final String NBT_GUI_BINDING_DIMENSION = "dimension";
    private static final String NBT_GUI_BINDING_FACE = "face";
    private static final String NBT_GUI_BINDING_LABEL = "label";
    private static final String NBT_GUI_BINDING_ITEM_ID = "item_id";
    private static final String NBT_PAGE = "page";
    private static final String NBT_SEARCH = "search";
    private static final String NBT_CATEGORY = "category";
    private static final String NBT_SORT = "sort";
    private static final String NBT_ASCENDING = "ascending";
    private static final String NBT_AUTO_STORE_MINED_DROPS = "auto_store_mined_drops";
    private static final String NBT_CRAFT_SEARCH = "craft_search";
    private static final String NBT_CRAFT_SHOW_UNAVAILABLE = "craft_show_unavailable";
    private static final String NBT_CRAFT_REQUESTED_COUNT = "craft_requested_count";

    private RtsStorageSessionCodec() {
    }

    static void load(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        session.linkedStorages.clear();
        session.linkedNames.clear();
        session.linkedModes.clear();

        session.page = root.contains(NBT_PAGE, Tag.TAG_INT) ? Math.max(0, root.getInt(NBT_PAGE)) : 0;
        session.search = sanitizeSavedText(root.getString(NBT_SEARCH), 128);
        session.category = RtsStorageManager.normalizeCategory(root.getString(NBT_CATEGORY));
        session.sort = parseSavedSort(root.getInt(NBT_SORT));
        session.ascending = root.contains(NBT_ASCENDING, Tag.TAG_BYTE) && root.getBoolean(NBT_ASCENDING);
        session.autoStoreMinedDrops = !root.contains(NBT_AUTO_STORE_MINED_DROPS, Tag.TAG_BYTE)
                || root.getBoolean(NBT_AUTO_STORE_MINED_DROPS);
        session.craftSearch = sanitizeSavedText(root.getString(NBT_CRAFT_SEARCH), 128);
        session.craftShowUnavailable = root.contains(NBT_CRAFT_SHOW_UNAVAILABLE, Tag.TAG_BYTE)
                && root.getBoolean(NBT_CRAFT_SHOW_UNAVAILABLE);
        session.craftRequestedCount = root.contains(NBT_CRAFT_REQUESTED_COUNT, Tag.TAG_INT)
                ? Math.max(RtsStorageManager.CRAFTABLE_BATCH_SIZE,
                        Math.min(999, root.getInt(NBT_CRAFT_REQUESTED_COUNT)))
                : RtsStorageManager.CRAFTABLE_BATCH_SIZE;

        loadLinkedStorages(player, session, root);
        loadInternalFluids(session, root);
        loadRecentEntries(session, root);
        loadQuickSlots(session, root);
        loadGuiBindings(session, root);
    }

    static CompoundTag serialize(RtsStorageSession session) {
        CompoundTag root = new CompoundTag();

        root.putInt(NBT_PAGE, Math.max(0, session.page));
        root.putString(NBT_SEARCH, sanitizeSavedText(session.search, 128));
        root.putString(NBT_CATEGORY, RtsStorageManager.normalizeCategory(session.category));
        root.putInt(NBT_SORT, (session.sort == null ? RtsStorageSort.QUANTITY : session.sort).ordinal());
        root.putBoolean(NBT_ASCENDING, session.ascending);
        root.putBoolean(NBT_AUTO_STORE_MINED_DROPS, session.autoStoreMinedDrops);
        root.putString(NBT_CRAFT_SEARCH, sanitizeSavedText(session.craftSearch, 128));
        root.putBoolean(NBT_CRAFT_SHOW_UNAVAILABLE, session.craftShowUnavailable);
        root.putInt(NBT_CRAFT_REQUESTED_COUNT,
                Math.max(RtsStorageManager.CRAFTABLE_BATCH_SIZE, Math.min(999, session.craftRequestedCount)));

        saveLinkedStorages(session, root);
        saveInternalFluids(session, root);
        saveRecentEntries(session, root);
        saveQuickSlots(session, root);
        saveGuiBindings(session, root);

        return root;
    }

    private static void loadLinkedStorages(ServerPlayer player, RtsStorageSession session, CompoundTag root) {
        byte[] linkedModes = root.getByteArray(NBT_LINKED_MODES);

        ResourceKey<Level> legacyDimension = null;
        String legacyDimensionId = root.getString(NBT_LINKED_DIMENSION);
        if (!legacyDimensionId.isBlank()) {
            legacyDimension = RtsStorageManager.parseDimensionKey(legacyDimensionId);
        }

        ListTag linkedEntries = root.getList(NBT_LINKED_ENTRIES, Tag.TAG_COMPOUND);
        if (!linkedEntries.isEmpty()) {
            for (int i = 0; i < linkedEntries.size(); i++) {
                CompoundTag linkedTag = linkedEntries.getCompound(i);
                if (!linkedTag.contains(NBT_LINKED_ENTRY_POS, Tag.TAG_LONG)) {
                    continue;
                }
                ResourceKey<Level> dimension = RtsStorageManager.parseDimensionKey(
                        linkedTag.getString(NBT_LINKED_ENTRY_DIMENSION));
                if (dimension == null) {
                    continue;
                }
                LinkedStorageRef ref = new LinkedStorageRef(
                        dimension,
                        BlockPos.of(linkedTag.getLong(NBT_LINKED_ENTRY_POS)).immutable());
                if (!session.linkedStorages.contains(ref)) {
                    session.linkedStorages.add(ref);
                    session.linkedModes.put(ref, RtsStorageManager.sanitizeLinkMode(
                            linkedTag.getByte(NBT_LINKED_ENTRY_MODE)));
                }
            }
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
                byte linkMode = i < linkedModes.length ? linkedModes[i] : RtsStorageManager.LINK_MODE_BIDIRECTIONAL;
                session.linkedModes.put(ref, RtsStorageManager.sanitizeLinkMode(linkMode));
            }
        }
    }

    private static void loadInternalFluids(RtsStorageSession session, CompoundTag root) {
        session.internalFluidMb.clear();
        ListTag fluidEntries = root.getList(NBT_INTERNAL_FLUIDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < fluidEntries.size(); i++) {
            CompoundTag fluidTag = fluidEntries.getCompound(i);
            String fluidId = fluidTag.getString(NBT_FLUID_ID);
            long amount = fluidTag.getLong(NBT_FLUID_AMOUNT);
            if (fluidId == null || fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(fluidId);
            if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
                continue;
            }
            session.internalFluidMb.put(fluidId, amount);
        }
    }

    private static void loadRecentEntries(RtsStorageSession session, CompoundTag root) {
        session.recentEntries.clear();
        ListTag recentEntries = root.getList(NBT_RECENT_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < recentEntries.size(); i++) {
            CompoundTag recentTag = recentEntries.getCompound(i);
            String entryId = recentTag.getString(NBT_RECENT_ENTRY_ID);
            long amount = recentTag.getLong(NBT_RECENT_ENTRY_AMOUNT);
            long capacity = recentTag.getLong(NBT_RECENT_ENTRY_CAPACITY);
            byte kind = recentTag.getByte(NBT_RECENT_ENTRY_KIND);
            if (entryId == null || entryId.isBlank() || amount <= 0L) {
                continue;
            }
            session.recentEntries.addLast(new RecentEntry(entryId, amount, Math.max(0L, capacity), kind));
            if (session.recentEntries.size() >= RtsStorageManager.RECENT_ENTRY_LIMIT) {
                break;
            }
        }
    }

    private static void loadQuickSlots(RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.quickSlotItemIds, "");
        ListTag quickSlots = root.getList(NBT_QUICK_SLOTS, Tag.TAG_COMPOUND);
        for (int i = 0; i < quickSlots.size(); i++) {
            CompoundTag quickSlotTag = quickSlots.getCompound(i);
            int slot = quickSlotTag.getInt(NBT_QUICK_SLOT_INDEX);
            String itemId = quickSlotTag.getString(NBT_QUICK_SLOT_ITEM_ID);
            if (slot < 0 || slot >= RtsStorageManager.QUICK_SLOT_COUNT || itemId == null || itemId.isBlank()) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(itemId);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            session.quickSlotItemIds[slot] = itemId;
        }
    }

    private static void loadGuiBindings(RtsStorageSession session, CompoundTag root) {
        Arrays.fill(session.guiBindings, null);
        ListTag guiBindings = root.getList(NBT_GUI_BINDINGS, Tag.TAG_COMPOUND);
        for (int i = 0; i < guiBindings.size(); i++) {
            CompoundTag bindingTag = guiBindings.getCompound(i);
            int slot = bindingTag.getInt(NBT_GUI_BINDING_SLOT);
            if (slot < 0 || slot >= RtsStorageManager.GUI_BINDING_SLOT_COUNT
                    || !bindingTag.contains(NBT_GUI_BINDING_POS, Tag.TAG_LONG)) {
                continue;
            }
            String bindingDimensionId = bindingTag.getString(NBT_GUI_BINDING_DIMENSION);
            ResourceLocation key = ResourceLocation.tryParse(bindingDimensionId);
            if (key == null) {
                continue;
            }
            String label = bindingTag.getString(NBT_GUI_BINDING_LABEL);
            String itemId = bindingTag.getString(NBT_GUI_BINDING_ITEM_ID);
            ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
            String normalizedItemId = itemKey != null && BuiltInRegistries.ITEM.containsKey(itemKey) ? itemId : "";
            Direction face = null;
            if (bindingTag.contains(NBT_GUI_BINDING_FACE, Tag.TAG_BYTE)) {
                int faceId = bindingTag.getByte(NBT_GUI_BINDING_FACE);
                if (faceId >= 0 && faceId < Direction.values().length) {
                    face = Direction.from3DDataValue(faceId);
                }
            }
            session.guiBindings[slot] = new GuiBinding(
                    BlockPos.of(bindingTag.getLong(NBT_GUI_BINDING_POS)).immutable(),
                    ResourceKey.create(Registries.DIMENSION, key),
                    label == null ? "" : label,
                    normalizedItemId,
                    face);
        }
    }

    private static void saveLinkedStorages(RtsStorageSession session, CompoundTag root) {
        ListTag linkedEntries = new ListTag();
        long[] linkedPacked = new long[session.linkedStorages.size()];
        byte[] linkedModes = new byte[session.linkedStorages.size()];
        for (int i = 0; i < session.linkedStorages.size(); i++) {
            LinkedStorageRef ref = session.linkedStorages.get(i);
            if (ref == null || ref.pos() == null || ref.dimension() == null) {
                continue;
            }
            byte linkMode = RtsStorageManager.sanitizeLinkMode(
                    session.linkedModes.getOrDefault(ref, RtsStorageManager.LINK_MODE_BIDIRECTIONAL));
            linkedPacked[i] = ref.pos().asLong();
            linkedModes[i] = linkMode;

            CompoundTag linkedTag = new CompoundTag();
            linkedTag.putLong(NBT_LINKED_ENTRY_POS, ref.pos().asLong());
            linkedTag.putString(NBT_LINKED_ENTRY_DIMENSION, ref.dimension().location().toString());
            linkedTag.putByte(NBT_LINKED_ENTRY_MODE, linkMode);
            linkedEntries.add(linkedTag);
        }
        root.put(NBT_LINKED_ENTRIES, linkedEntries);
        root.putLongArray(NBT_LINKED_POSITIONS, linkedPacked);
        root.putByteArray(NBT_LINKED_MODES, linkedModes);

        if (!session.linkedStorages.isEmpty()) {
            LinkedStorageRef first = session.linkedStorages.get(0);
            if (first != null && first.dimension() != null) {
                root.putString(NBT_LINKED_DIMENSION, first.dimension().location().toString());
            }
        }
    }

    private static void saveInternalFluids(RtsStorageSession session, CompoundTag root) {
        ListTag fluidEntries = new ListTag();
        for (var entry : session.internalFluidMb.entrySet()) {
            String fluidId = entry.getKey();
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (fluidId == null || fluidId.isBlank() || amount <= 0L) {
                continue;
            }
            CompoundTag fluidTag = new CompoundTag();
            fluidTag.putString(NBT_FLUID_ID, fluidId);
            fluidTag.putLong(NBT_FLUID_AMOUNT, amount);
            fluidEntries.add(fluidTag);
        }
        root.put(NBT_INTERNAL_FLUIDS, fluidEntries);
    }

    private static void saveRecentEntries(RtsStorageSession session, CompoundTag root) {
        ListTag recentEntries = new ListTag();
        for (RecentEntry recentEntry : session.recentEntries) {
            if (recentEntry == null || recentEntry.id() == null || recentEntry.id().isBlank()) {
                continue;
            }
            CompoundTag recentTag = new CompoundTag();
            recentTag.putString(NBT_RECENT_ENTRY_ID, recentEntry.id());
            recentTag.putLong(NBT_RECENT_ENTRY_AMOUNT, Math.max(0L, recentEntry.amount()));
            recentTag.putLong(NBT_RECENT_ENTRY_CAPACITY, Math.max(0L, recentEntry.capacity()));
            recentTag.putByte(NBT_RECENT_ENTRY_KIND, recentEntry.kind());
            recentEntries.add(recentTag);
        }
        root.put(NBT_RECENT_ENTRIES, recentEntries);
    }

    private static void saveQuickSlots(RtsStorageSession session, CompoundTag root) {
        ListTag quickSlots = new ListTag();
        for (int i = 0; i < session.quickSlotItemIds.length; i++) {
            String itemId = session.quickSlotItemIds[i];
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            CompoundTag quickSlotTag = new CompoundTag();
            quickSlotTag.putInt(NBT_QUICK_SLOT_INDEX, i);
            quickSlotTag.putString(NBT_QUICK_SLOT_ITEM_ID, itemId);
            quickSlots.add(quickSlotTag);
        }
        root.put(NBT_QUICK_SLOTS, quickSlots);
    }

    private static void saveGuiBindings(RtsStorageSession session, CompoundTag root) {
        ListTag guiBindings = new ListTag();
        for (int i = 0; i < session.guiBindings.length; i++) {
            GuiBinding binding = session.guiBindings[i];
            if (binding == null || binding.pos() == null || binding.dimension() == null) {
                continue;
            }
            CompoundTag bindingTag = new CompoundTag();
            bindingTag.putInt(NBT_GUI_BINDING_SLOT, i);
            bindingTag.putLong(NBT_GUI_BINDING_POS, binding.pos().asLong());
            bindingTag.putString(NBT_GUI_BINDING_DIMENSION, binding.dimension().location().toString());
            if (binding.face() != null) {
                bindingTag.putByte(NBT_GUI_BINDING_FACE, (byte) binding.face().get3DDataValue());
            }
            bindingTag.putString(NBT_GUI_BINDING_LABEL, binding.label() == null ? "" : binding.label());
            bindingTag.putString(NBT_GUI_BINDING_ITEM_ID, binding.itemId() == null ? "" : binding.itemId());
            guiBindings.add(bindingTag);
        }
        root.put(NBT_GUI_BINDINGS, guiBindings);
    }

    private static String sanitizeSavedText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String clean = value.trim();
        int limit = Math.max(0, maxLength);
        return clean.length() <= limit ? clean : clean.substring(0, limit);
    }

    private static RtsStorageSort parseSavedSort(int ordinal) {
        RtsStorageSort[] values = RtsStorageSort.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return RtsStorageSort.QUANTITY;
        }
        return values[ordinal];
    }
}
