package com.rtsbuilding.addon.beyonddimensions;

import com.rtsbuilding.rtsbuilding.api.compat.*;
import com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.AbstractUnorderedStackHandler;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod("rtsbuilding_addon_beyonddimensions")
public class RtsBeyondDimensionsAddon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/BD");

    public RtsBeyondDimensionsAddon(IEventBus modEventBus, ModContainer modContainer) {
        
        if (!ModList.get().isLoaded("beyonddimensions")) {
            LOGGER.info("Beyond Dimensions not detected — addon will not register");
            return;
        }
        RtsCompatRegistry.register(new BdStorageProvider());
        RtsCompatRegistry.register(new BdFluidProvider());
        LOGGER.info("Beyond Dimensions storage/fluid providers registered");
    }

    private static final class BdStorageProvider implements RtsStorageNetworkProvider {
        @Override public String getModId() { return "beyonddimensions"; }
        @Override public boolean isAvailable() { return true; }

        @Override @Nullable
        public IItemHandler createItemHandler(ServerPlayer player, BlockPos pos) {
            if (player == null || player.getServer() == null) return null;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return null;
            return new BdDirectItemHandler(net.getUnifiedStorage());
        }

        @Override
        public void releaseItemHandler(IItemHandler handler) {
            if (handler instanceof BdDirectItemHandler bd) bd.release();
        }

        @Override
        public boolean isNetworkNode(ServerPlayer player, BlockPos pos) {
            return hasPrimaryNetwork(player);
        }

        @Override @Nullable
        public String getNetworkDisplayName(ServerPlayer player) {
            if (player == null || player.getServer() == null) return "Beyond Dimensions Network";
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return "Beyond Dimensions Network";
            try {
                String name = net.getCustomName();
                return (name != null && !name.isEmpty()) ? name : "Beyond Dimensions Network";
            } catch (NoSuchMethodError e) {
                return "Beyond Dimensions Network";
            }
        }

        public static boolean hasPrimaryNetwork(ServerPlayer player) {
            if (player == null || player.getServer() == null) return false;
            return DimensionsNet.getPrimaryNetFromPlayer(player) != null;
        }
    }

    private static final class BdFluidProvider implements RtsFluidNetworkProvider {
        @Override public String getModId() { return "beyonddimensions"; }
        @Override public boolean isAvailable() { return true; }

        @Override @Nullable
        public IFluidHandler createFluidHandler(ServerPlayer player) {
            if (player == null || player.getServer() == null) return null;
            DimensionsNet net = DimensionsNet.getPrimaryNetFromPlayer(player);
            if (net == null) return null;
            return new FluidUnifiedStorageHandler(net.getUnifiedStorage());
        }

        @Override
        public List<FluidStack> collectFluids(ServerPlayer player, @Nullable BlockPos pos, @Nullable BlockEntity blockEntity) {
            return List.of();
        }
    }

    private static final class BdDirectItemHandler
            implements IItemHandler, ReportedCountItemHandler, DirectExtractHandler, AnySlotInsertItemHandler {

        private final UnifiedStorage storage;
        private final Map<Item, ItemStackKey> itemToKey = new HashMap<>();
        private final List<ItemStackKey> keys = new ArrayList<>();
        private final List<ItemStack> displayStacks = new ArrayList<>();
        private final List<Long> counts = new ArrayList<>();

        BdDirectItemHandler(UnifiedStorage storage) {
            this.storage = storage;
            rebuildCache();
        }

        private void rebuildCache() {
            itemToKey.clear();
            keys.clear();
            displayStacks.clear();
            counts.clear();
            var bucket = storage.<AbstractUnorderedStackHandler.TypeBucket>getBucket(ItemStackKey.ID);
            if (bucket.isEmpty()) return;
            var tb = bucket.get();
            for (int i = 0; i < tb.size(); i++) {
                if (!(tb.get(i) instanceof ItemStackKey key)) continue;
                KeyAmount entry = storage.getStackByKey(key);
                long amount = entry.amount();
                if (amount <= 0L) continue;
                if (!(storage.getOutStackByKey(key) instanceof ItemStack itemStack) || itemStack.isEmpty()) continue;
                itemToKey.put(itemStack.getItem(), key);
                keys.add(key);
                displayStacks.add(itemStack.copyWithCount(1));
                counts.add(amount);
            }
        }

        @Override public int getSlots() { return keys.size(); }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= keys.size()) return ItemStack.EMPTY;
            long amount = counts.get(slot);
            if (amount <= 0L) return ItemStack.EMPTY;
            ItemStack result = displayStacks.get(slot).copy();
            result.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return result;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            KeyAmount remainder = storage.insert(new ItemStackKey(stack), stack.getCount(), simulate);
            if (!simulate && remainder.isEmpty()) rebuildCache();
            return remainder.isEmpty() ? ItemStack.EMPTY :
                    remainder.toStack() instanceof ItemStack result ? result : stack.copy();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= keys.size() || amount <= 0) return ItemStack.EMPTY;
            ItemStackKey key = keys.get(slot);
            if (key == null) return ItemStack.EMPTY;
            KeyAmount extracted = storage.extract(key, amount, simulate, false);
            if (!simulate) rebuildCache();
            return extracted.isEmpty() ? ItemStack.EMPTY :
                    extracted.toStack() instanceof ItemStack result ? result : ItemStack.EMPTY;
        }

        @Override
        public ItemStack tryExtractItem(Item target, int amount, boolean simulate) {
            if (target == null || amount <= 0) return ItemStack.EMPTY;
            ItemStackKey key = itemToKey.get(target);
            if (key == null) return ItemStack.EMPTY;
            KeyAmount result = storage.extract(key, amount, simulate, false);
            if (!simulate) rebuildCache();
            return result.isEmpty() ? ItemStack.EMPTY :
                    result.toStack() instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            return insertItem(0, stack, simulate);
        }

        @Override
        public long getReportedCount(int slot) {
            return (slot >= 0 && slot < counts.size()) ? Math.max(0L, counts.get(slot)) : 0L;
        }

        void release() {
            itemToKey.clear();
            keys.clear();
            displayStacks.clear();
            counts.clear();
        }

        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}
