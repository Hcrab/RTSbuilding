package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.server.storage.GuiBinding;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.RtsStorageSessionCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds read-only client packet/UI snapshots from an RTS storage session.
 *
 * <p>This helper only converts already-owned {@link RtsStorageSession} state
 * into the ordered lists consumed by client storage packets and UI widgets. It
 * does not serialize or deserialize NBT; saved session format belongs to
 * {@link RtsStorageSessionCodec}. It does not perform capability lookup; linked
 * storage resolution belongs to the linked resolver/manager path. It also does
 * not open external GUIs, prove a GUI binding is still usable, or mutate quick
 * slots.
 *
 * <p>The packet contract is intentionally strict: quick slots and GUI bindings
 * must keep their server-side order, missing/null values must be padded as empty
 * strings, and the emitted slot count must remain exactly the manager-owned
 * constants. Client layout and packet decoding depend on those counts rather
 * than on compacted lists.
 */
public final class RtsStorageUiPayloads {
    private RtsStorageUiPayloads() {
    }

    /**
     * Emits exactly {@code quickSlotCount} item-id entries in quick-slot order.
     * A missing session, null backing array, null entry, or blank entry occupies
     * its original slot as an empty string so client hotbar-style indexes stay
     * stable.
     */
    public static List<String> buildQuickSlotPayload(RtsStorageSession session, int quickSlotCount) {
        List<String> quickSlotItemIds = new ArrayList<>(quickSlotCount);
        String[] source = session == null ? null : session.quickSlotItemIds;
        for (int i = 0; i < quickSlotCount; i++) {
            String itemId = source == null || i >= source.length ? "" : source[i];
            quickSlotItemIds.add(itemId == null || itemId.isEmpty() ? "" : itemId);
        }
        return quickSlotItemIds;
    }

    /**
     * Emits exactly {@code quickSlotCount} preview stacks in quick-slot order.
     * Component-heavy modded tools, such as Silent Gear gear items, need the
     * stored stack rather than a fresh item-id-only stack to render correctly.
     */
    public static List<ItemStack> buildQuickSlotPreviewPayload(RtsStorageSession session, int quickSlotCount) {
        List<ItemStack> previews = new ArrayList<>(quickSlotCount);
        String[] itemIds = session == null ? null : session.quickSlotItemIds;
        ItemStack[] source = session == null ? null : session.quickSlotPreviews;
        for (int i = 0; i < quickSlotCount; i++) {
            String itemId = itemIds == null || i >= itemIds.length ? "" : itemIds[i];
            ItemStack preview = source == null || i >= source.length || source[i] == null ? ItemStack.EMPTY : source[i];
            previews.add(sanitizeQuickSlotPreview(itemId, preview));
        }
        return previews;
    }

    private static ItemStack sanitizeQuickSlotPreview(String itemId, ItemStack preview) {
        if (itemId == null || itemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        if (preview != null && !preview.isEmpty() && preview.is(BuiltInRegistries.ITEM.get(key))) {
            return preview.copyWithCount(1);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    /**
     * Emits exactly {@code guiBindingSlotCount} labels in GUI binding slot
     * order. A missing session, missing binding, null label, or empty label is
     * represented as an empty string so the client keeps one label cell per
     * binding slot.
     */
    public static List<String> buildGuiBindingLabelPayload(RtsStorageSession session, int guiBindingSlotCount) {
        List<String> guiBindingLabels = new ArrayList<>(guiBindingSlotCount);
        GuiBinding[] source = session == null ? null : session.guiBindings;
        for (int i = 0; i < guiBindingSlotCount; i++) {
            GuiBinding guiBinding = source == null || i >= source.length ? null : source[i];
            String label = guiBinding == null ? "" : guiBinding.label();
            guiBindingLabels.add(label == null || label.isEmpty() ? "" : label);
        }
        return guiBindingLabels;
    }

    /**
     * Emits exactly {@code guiBindingSlotCount} item ids in GUI binding slot
     * order. A missing session, missing binding, null item id, or empty item id
     * is represented as an empty string so icon lookup stays aligned with the
     * label payload and the fixed GUI binding slots.
     */
    public static List<String> buildGuiBindingItemIdPayload(RtsStorageSession session, int guiBindingSlotCount) {
        List<String> guiBindingItemIds = new ArrayList<>(guiBindingSlotCount);
        GuiBinding[] source = session == null ? null : session.guiBindings;
        for (int i = 0; i < guiBindingSlotCount; i++) {
            GuiBinding guiBinding = source == null || i >= source.length ? null : source[i];
            String itemId = guiBinding == null ? "" : guiBinding.itemId();
            guiBindingItemIds.add(itemId == null || itemId.isEmpty() ? "" : itemId);
        }
        return guiBindingItemIds;
    }
}
