package com.rtsbuilding.rtsbuilding.compat.bd;

import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RtsBdCompat {
    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }
    private RtsBdCompat() {
    }

    public static boolean isAvailable() {
        return ModList.get().isLoaded("beyonddimensions");
    }

    public static boolean hasPrimaryNetwork(ServerPlayer player) {
        if (!isAvailable() || player == null || player.getServer() == null) {
            return false;
        }
        return DimensionsNet.getPrimaryNetFromPlayer(player) != null;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player) {
        if (!isAvailable() || player == null || player.getServer() == null) {
            return null;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) {
            return null;
        }
        return new BdDirectItemHandler(net.getUnifiedStorage());
    }

    public static IFluidHandler createNetworkFluidHandler(ServerPlayer player) {
        if (!isAvailable() || player == null || player.getServer() == null) {
            return null;
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) {
            return null;
        }
        return new FluidUnifiedStorageHandler(net.getUnifiedStorage());
    }

    /**
     * Releases the internal caches held by a BD network handler so the GC
     * can reclaim memory immediately. Safe to call on non-BD handlers —
     * the instanceof check will simply skip them.
     */
    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdDirectItemHandler bd) {
            bd.release();
        }
    }

    /**
     * Refreshes the internal cache of a BD network handler, re-reading the
     * current BD network state without creating a new handler object.
     * <p>
     * This avoids the unmount/mount cycle that would happen if the handler
     * were replaced entirely, letting the tick service reuse its existing
     * slot cache.
     */
    public static void refreshNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdDirectItemHandler bd) {
            bd.refreshCache();
        }
    }

    public static String getNetworkDisplayName(ServerPlayer player) {
        if (!isAvailable() || player == null || player.getServer() == null) {
            return "Beyond Dimensions Network";
        }
        DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
        if (net == null) {
            return "Beyond Dimensions Network";
        }
        String customName = getCustomNameOrDefault(net);
        if (customName != null && !customName.isEmpty()) {
            return customName;
        }
        return "Beyond Dimensions Network";
    }

    private static String getCustomNameOrDefault(DimensionsNet net) {
        try {
            return net.getCustomName();
        } catch (NoSuchMethodError ignored) {
            return null;
        }
    }

    private static final class BdDirectItemHandler implements IItemHandler, ReportedCountItemHandler, DirectExtractHandler,
            com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler {
        private final UnifiedStorage storage;
        private final Map<Item, ItemStackKey> itemToKey;
        private final List<ItemStackKey> keys;
        private final List<ItemStack> displayStacks;
        private final List<Long> counts;

        private BdDirectItemHandler(UnifiedStorage storage) {
            this.storage = storage;
            this.itemToKey = new HashMap<>();
            this.keys = new ArrayList<>();
            this.displayStacks = new ArrayList<>();
            this.counts = new ArrayList<>();
            rebuildCache();
        }

        private void rebuildCache() {
            this.itemToKey.clear();
            this.keys.clear();
            this.displayStacks.clear();
            this.counts.clear();
            var bucket = storage.<AbstractUnorderedStackHandler.TypeBucket>getBucket(ItemStackKey.ID);
            if (bucket.isEmpty()) {
                return;
            }
            AbstractUnorderedStackHandler.TypeBucket tb = bucket.get();
            for (int i = 0; i < tb.size(); i++) {
                var rawKey = tb.get(i);
                if (!(rawKey instanceof ItemStackKey key)) {
                    continue;
                }
                KeyAmount entry = storage.getStackByKey(key);
                long amount = entry.amount();
                if (amount <= 0L) {
                    continue;
                }
                Object outStack = storage.getOutStackByKey(key);
                if (!(outStack instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                    continue;
                }
                this.itemToKey.put(itemStack.getItem(), key);
                this.keys.add(key);
                this.displayStacks.add(itemStack.copyWithCount(1));
                this.counts.add(amount);
            }
        }

        @Override
        public int getSlots() {
            return this.keys.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.keys.size()) {
                return ItemStack.EMPTY;
            }
            long amount = this.counts.get(slot);
            if (amount <= 0L) {
                return ItemStack.EMPTY;
            }
            ItemStack display = this.displayStacks.get(slot);
            ItemStack result = display.copy();
            result.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return result;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            KeyAmount remainder = storage.insert(new ItemStackKey(stack), stack.getCount(), simulate);
            if (!simulate && remainder.isEmpty()) {
                rebuildCache();
            }
            if (remainder.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object rawStack = remainder.toStack();
            if (rawStack instanceof ItemStack result) {
                return result;
            }
            return stack.copy();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.keys.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStackKey key = this.keys.get(slot);
            if (key == null) {
                return ItemStack.EMPTY;
            }
            KeyAmount extracted = storage.extract(key, amount, simulate, false);
            if (!simulate) {
                rebuildCache();
            }
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object rawStack = extracted.toStack();
            if (rawStack instanceof ItemStack result) {
                return result;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack tryExtractItem(Item target, int amount, boolean simulate) {
            if (target == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStackKey key = this.itemToKey.get(target);
            if (key == null) {
                return ItemStack.EMPTY;
            }
            KeyAmount result = storage.extract(key, amount, simulate, false);
            if (!simulate) {
                rebuildCache();
            }
            if (result.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object rawStack = result.toStack();
            if (rawStack instanceof ItemStack stack) {
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            // BD's storage.insert() is slot-independent — just delegate to slot 0
            return insertItem(0, stack, simulate);
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            // Use the existing O(1) item-to-key lookup
            return tryExtractItem(targetItem, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.counts.size()) {
                return 0L;
            }
            return Math.max(0L, this.counts.get(slot));
        }

        /**
         * Refreshes the internal cache by clearing and re-reading the
         * current BD network state. Can be called on an existing handler
         * to update its slot list without changing object identity.
         */
        void refreshCache() {
            rebuildCache();
        }

        /**
         * Clears internal caches and drops the storage reference so the GC
         * can reclaim memory immediately. After this call the handler must
         * NOT be used again.
         */
        void release() {
            this.itemToKey.clear();
            this.keys.clear();
            this.displayStacks.clear();
            this.counts.clear();
        }
    }
}
