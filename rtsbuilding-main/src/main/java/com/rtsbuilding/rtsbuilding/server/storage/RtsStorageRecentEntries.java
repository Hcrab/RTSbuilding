package com.rtsbuilding.rtsbuilding.server.storage;

import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.server.storage.model.RecentEntry;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.util.RtsCountUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Maintains the player's recent item/fluid history for the RTS storage UI.
 *
 * <p>This class only holds the short-term "recently seen or used" history stored in {@link RtsUiMemory#getRecentEntries()}.
 * Recent entries are UI memory, not authoritative inventory counts, and must never be used as storage quantities.
 *
 * <p>It deliberately does not serialize NBT, search storage, build storage page payloads,
 * execute crafting, transfer items or fluids, or absorb drops.
 * These systems may read or record recent entries, but this class only modifies the recent entry deque.
 *
 * <p>The original deduplication, ordering, quantity merging, capacity merging, and limit behavior must remain stable:
 * equivalent item/fluid entries are merged, the most recent entry appears at the front,
 * and history is trimmed to the storage UI limit.
 */
public final class RtsStorageRecentEntries {
    public static final int RECENT_ENTRY_LIMIT = 24;

    private RtsStorageRecentEntries() {
    }

    /**
     * Records an item by resolving its registry key. If the key cannot be resolved,
     * the item is skipped; display names are never used as they change with language and resource packs.
     */
    static void recordRecentItem(RtsStorageSession session, ItemStack stack, byte kind, long amount) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return;
        }
        recordRecentItem(session, id.toString(), kind, amount);
    }

    /**
     * Records a pre-resolved item registry key. Missing keys are skipped,
     * callers must pass stable registry IDs rather than translated display names
     * to ensure recent history remains valid after language changes.
     */
    public static void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(itemId, amount, 0L, kind));
    }

    /**
     * Records a pre-resolved fluid registry key. Missing keys are skipped,
     * callers must pass stable registry IDs rather than translated display names
     * to ensure recent history remains valid after language changes.
     */
    static void recordRecentFluid(RtsStorageSession session, String fluidId, byte kind, long amount, long capacity) {
        if (fluidId == null || fluidId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(fluidId, amount, Math.max(0L, capacity), kind));
    }

    /**
     * Pushes a recent entry using existing UI history rules: deduplication by registry ID plus item/fluid category,
     * newest or merged entry inserted at the front,
     * old entries beyond the UI limit trimmed from the end.
     * Non-positive amounts are ignored because recent history represents what the player actually saw or used;
     * zero/negative amounts would create empty UI rows, which are not real storage counts.
     */
    static void pushRecentEntry(RtsStorageSession session, RecentEntry entry) {
        if (session == null
                || entry == null
                || entry.id() == null
                || entry.id().isBlank()
                || entry.amount() <= 0L) {
            return;
        }
        RecentEntry normalized = new RecentEntry(
                entry.id(),
                Math.max(1L, entry.amount()),
                Math.max(0L, entry.capacity()),
                entry.kind());
        RecentEntry merged = normalized;
        var recentEntries = session.uiMemory.getRecentEntries();
        for (RecentEntry existing : recentEntries) {
            if (!sameRecentKey(existing, normalized)) {
                continue;
            }
            long mergedAmount = Math.max(1L, RtsCountUtil.saturatedAdd(existing.amount(), normalized.amount()));
            long mergedCapacity = Math.max(Math.max(existing.capacity(), normalized.capacity()), mergedAmount);
            merged = new RecentEntry(normalized.id(), mergedAmount, mergedCapacity, normalized.kind());
            break;
        }
        final RecentEntry mergedEntry = merged;
        recentEntries.removeIf(existing -> sameRecentKey(existing, mergedEntry));
        recentEntries.addFirst(mergedEntry);
        while (recentEntries.size() > RECENT_ENTRY_LIMIT) {
            recentEntries.removeLast();
        }
    }

    private static boolean sameRecentKey(RecentEntry a, RecentEntry b) {
        if (a == null || b == null) {
            return false;
        }
        return a.id().equals(b.id()) && isRecentFluidKind(a.kind()) == isRecentFluidKind(b.kind());
    }

    private static boolean isRecentFluidKind(byte kind) {
        return kind == S2CRtsStoragePagePayload.RECENT_FLUID_PLACED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_USED
                || kind == S2CRtsStoragePagePayload.RECENT_FLUID_CRAFTED;
    }

}
