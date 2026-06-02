package com.rtsbuilding.rtsbuilding.server;

import com.rtsbuilding.rtsbuilding.network.S2CRtsStoragePagePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Maintains the player's recent item/fluid history for the RTS storage UI.
 *
 * <p>This class owns only the short "recently seen or used" history stored in
 * {@link RtsStorageSession#recentEntries}. Recent entries are UI memory, not
 * authoritative inventory quantities, and must never be used as storage counts.
 *
 * <p>It deliberately does not serialize NBT, search storage, build storage page
 * payloads, execute crafting, transfer items or fluids, or absorb drops. Those
 * systems can read or record recent entries, but this class only mutates the
 * recent deque.
 *
 * <p>The original dedupe, ordering, amount merge, capacity merge, and limit
 * behavior must stay stable: equivalent item/fluid entries merge, the newest
 * entry appears first, and the history is trimmed to the storage UI limit.
 */
final class RtsStorageRecentEntries {
    private static final long EFFECTIVELY_INFINITE_COUNT = Long.MAX_VALUE / 2L;

    private RtsStorageRecentEntries() {
    }

    static void recordCraftedOutput(RtsStorageSession session, ItemStack crafted) {
        if (crafted == null || crafted.isEmpty()) {
            return;
        }
        recordRecentItem(
                session,
                crafted,
                S2CRtsStoragePagePayload.RECENT_ITEM_CRAFTED,
                crafted.getCount());
    }

    /**
     * Records an item by resolving its registry key. If the key cannot be
     * resolved, the item is skipped; display names are never used because they
     * change with language and resource packs.
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
     * Records a pre-resolved item registry key. A missing key is skipped, and
     * callers must pass the stable registry id rather than a translated display
     * name so recent history survives language changes.
     */
    static void recordRecentItem(RtsStorageSession session, String itemId, byte kind, long amount) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(itemId, amount, 0L, kind));
    }

    /**
     * Records a fluid by resolving its registry key. If the key cannot be
     * resolved, the fluid is skipped; display names are never used because they
     * change with language and resource packs.
     */
    static void recordRecentFluid(RtsStorageSession session, FluidStack stack, byte kind, long amount, long capacity) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (id == null) {
            return;
        }
        recordRecentFluid(session, id.toString(), kind, amount, capacity);
    }

    /**
     * Records a pre-resolved fluid registry key. A missing key is skipped, and
     * callers must pass the stable registry id rather than a translated display
     * name so recent history survives language changes.
     */
    static void recordRecentFluid(RtsStorageSession session, String fluidId, byte kind, long amount, long capacity) {
        if (fluidId == null || fluidId.isBlank()) {
            return;
        }
        pushRecentEntry(session, new RecentEntry(fluidId, amount, Math.max(0L, capacity), kind));
    }

    /**
     * Pushes a recent entry using the existing UI history rules: entries dedupe
     * by registry id plus item/fluid category, the newest or merged entry is
     * inserted at the front, and older entries past the UI limit are trimmed
     * from the end. Non-positive amounts are ignored because recent history
     * represents something the player actually saw or used; zero/negative
     * amounts would create empty UI rows that are not real storage counts.
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
        for (RecentEntry existing : session.recentEntries) {
            if (!sameRecentKey(existing, normalized)) {
                continue;
            }
            long mergedAmount = Math.max(1L, saturatedAdd(existing.amount(), normalized.amount()));
            long mergedCapacity = Math.max(Math.max(existing.capacity(), normalized.capacity()), mergedAmount);
            merged = new RecentEntry(normalized.id(), mergedAmount, mergedCapacity, normalized.kind());
            break;
        }
        final RecentEntry mergedEntry = merged;
        session.recentEntries.removeIf(existing -> sameRecentKey(existing, mergedEntry));
        session.recentEntries.addFirst(mergedEntry);
        while (session.recentEntries.size() > RtsStorageManager.RECENT_ENTRY_LIMIT) {
            session.recentEntries.removeLast();
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

    private static long saturatedAdd(long a, long b) {
        long left = sanitizeCount(a);
        long right = sanitizeCount(b);
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long sanitizeCount(long value) {
        if (value <= 0L) {
            return 0L;
        }
        return value >= EFFECTIVELY_INFINITE_COUNT ? Long.MAX_VALUE : value;
    }
}
