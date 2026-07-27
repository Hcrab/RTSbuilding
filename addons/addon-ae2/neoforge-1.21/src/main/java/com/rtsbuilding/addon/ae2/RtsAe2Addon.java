package com.rtsbuilding.addon.ae2;

import com.rtsbuilding.rtsbuilding.api.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

@Mod("rtsbuilding_addon_ae2")
public class RtsAe2Addon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/AE2");
    private final IEventBus modEventBus;
    private final ModContainer modContainer;

    public RtsAe2Addon(IEventBus modEventBus, ModContainer modContainer) {
        this.modEventBus = modEventBus;
        this.modContainer = modContainer;
        if (FMLLoader.getLoadingModList().getModFileById("ae2") == null) {
            LOGGER.info("AE2 not detected — addon will not register providers");
            return;
        }
        var reflection = new Ae2Reflection();
        if (!reflection.loaded) {
            LOGGER.warn("AE2 found but reflection load failed — skipping registration");
            return;
        }
        RtsCompatRegistry.register(new Ae2StorageProvider(reflection));
        RtsCompatRegistry.register(new Ae2FluidProvider(reflection));
        RtsCompatRegistry.register(new Ae2IconResolverProvider(reflection));
        LOGGER.info("AE2 storage/fluid/icon providers registered");
    }

    public ModContainer getModContainer() {
        return modContainer;
    }

    public IEventBus getModEventBus() {
        return modEventBus;
    }

    // ─── Storage Network Provider ──────────────────────────────────────────

    private static final class Ae2StorageProvider implements RtsStorageNetworkProvider {
        private final Ae2Reflection ref;

        Ae2StorageProvider(Ae2Reflection ref) { this.ref = ref; }

        @Override public String getModId() { return "ae2"; }
        @Override public boolean isAvailable() { return ref.loaded; }

        @Override @Nullable
        public IItemHandler createItemHandler(ServerPlayer player, BlockPos pos) {
            if (!ref.loaded) return null;
            try {
                var storageService = ref.findStorageService(player.serverLevel(), pos);
                if (storageService == null) return null;
                return new Ae2NetworkItemHandler(ref, storageService);
            } catch (Throwable e) {
                return null;
            }
        }

        @Override
        public void releaseItemHandler(IItemHandler handler) {
            if (handler instanceof Ae2NetworkItemHandler ae2) {
                ae2.release();
            }
        }

        @Override
        public boolean isNetworkNode(ServerPlayer player, BlockPos pos) {
            return createItemHandler(player, pos) != null;
        }

        @Override
        public @NotNull String getNetworkDisplayName(ServerPlayer player) {
            return "AE2 Network";
        }
    }

    // ─── Fluid Network Provider ────────────────────────────────────────────

    private static final class Ae2FluidProvider implements RtsFluidNetworkProvider {
        private final Ae2Reflection ref;

        Ae2FluidProvider(Ae2Reflection ref) { this.ref = ref; }

        @Override public String getModId() { return "ae2"; }
        @Override public boolean isAvailable() { return ref.loaded; }

        @Override @Nullable
        public IFluidHandler createFluidHandler(ServerPlayer player) {
            return null;
        }

        @Override
        public List<FluidStack> collectFluids(ServerPlayer player, @Nullable BlockPos pos, @Nullable BlockEntity blockEntity) {
            if (!ref.loaded || pos == null) return List.of();
            try {
                var storageService = ref.findStorageService(player.serverLevel(), pos);
                if (storageService == null) return List.of();
                return ref.collectFluidsFromStorage(storageService);
            } catch (Throwable e) {
                return List.of();
            }
        }
    }

    // ─── Icon Resolver Provider ────────────────────────────────────────────

    private static final class Ae2IconResolverProvider implements RtsIconResolver {
        private final Ae2Reflection ref;

        Ae2IconResolverProvider(Ae2Reflection ref) { this.ref = ref; }

        @Override public String getModId() { return "ae2"; }

        @Override @Nullable
        public String resolveIconId(Level level, BlockPos pos, @Nullable Direction face, String label) {
            if (!ref.loaded) return null;
            return ref.resolveIcon(level, pos, face, label);
        }
    }

    // ─── Network Item Handler ──────────────────────────────────────────────

    private static final class Ae2NetworkItemHandler
            implements IItemHandler, ReportedCountItemHandler, AnySlotInsertItemHandler, RefreshableSnapshotHandler {

        private static final int REFRESH_THROTTLE = 10;

        private final Ae2Reflection ref;
        private Object storageService;
        private Object cachedSnapshot;
        private List<SlotView> slots = List.of();
        private int tickSinceRefresh = 0;

        Ae2NetworkItemHandler(Ae2Reflection ref, Object storageService) {
            this.ref = ref;
            this.storageService = storageService;
            ensureFreshSnapshot();
        }

        @Override
        public void ensureFreshSnapshot() {
            if (tickSinceRefresh < REFRESH_THROTTLE && cachedSnapshot != null) {
                tickSinceRefresh++;
                return;
            }
            if (storageService == null) return;
            try {
                cachedSnapshot = ref.snapshotCached(storageService);
                if (cachedSnapshot == null) {
                    cachedSnapshot = ref.snapshot(storageService);
                }
            } catch (Throwable e) {
                cachedSnapshot = null;
            }
            slots = buildSlots();
            tickSinceRefresh = 0;
        }

        private List<SlotView> buildSlots() {
            if (cachedSnapshot == null) return List.of();
            try {
                return ref.fillFromKeyCounter(cachedSnapshot);
            } catch (Throwable e) {
                return List.of();
            }
        }

        void release() {
            storageService = null;
            cachedSnapshot = null;
            slots = List.of();
        }

        @Override public int getSlots() { ensureFreshSnapshot(); return slots.size(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { ensureFreshSnapshot(); return slot < slots.size() ? slots.get(slot).displayStack() : ItemStack.EMPTY; }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (storageService == null || stack.isEmpty()) return stack;
            ensureFreshSnapshot();
            try {
                var key = ref.toItemKey(stack);
                if (key == null) return stack;
                long inserted = ref.insert(storageService, key, stack.getCount(), simulate ? null : java.lang.Boolean.TRUE);
                if (inserted <= 0) return stack;
                if (!simulate) { cachedSnapshot = null; }
                ItemStack remaining = stack.copy();
                remaining.setCount((int) (stack.getCount() - inserted));
                return remaining;
            } catch (Throwable e) {
                return stack;
            }
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (storageService == null || amount <= 0) return ItemStack.EMPTY;
            ensureFreshSnapshot();
            if (slot >= slots.size()) return ItemStack.EMPTY;
            var view = slots.get(slot);
            try {
                var key = ref.toItemKey(view.displayStack());
                if (key == null) return ItemStack.EMPTY;
                long extracted = ref.extract(storageService, key, amount, simulate ? null : java.lang.Boolean.TRUE);
                if (extracted <= 0) return ItemStack.EMPTY;
                if (!simulate) { cachedSnapshot = null; }
                ItemStack result = view.displayStack().copy();
                result.setCount((int) extracted);
                return result;
            } catch (Throwable e) {
                return ItemStack.EMPTY;
            }
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            return insertItem(0, stack, simulate);
        }

        @Override
        public long getReportedCount(int slot) {
            ensureFreshSnapshot();
            if (slot >= slots.size()) return 0;
            return slots.get(slot).amount();
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
    }

    // ─── Slot View ─────────────────────────────────────────────────────────

    private record SlotView(Object key, ItemStack displayStack, long amount) {}

    // ─── AE2 Reflection Helper ─────────────────────────────────────────────

    private static final class Ae2Reflection {
        boolean loaded = false;
        private Class<?> clKeyCounter, clMEStorage, clStorageService, clItemKey, clFluidKey, clGridHost, clGridNode, clGrid;
        private MethodHandle mhGetGridNode, mhGetGrid, mhGetStorageService, mhGetCachedInventory, mhGetAvailableStacks;
        private MethodHandle mhKeyCounterIterator, mhKeyGetDisplayStack, mhKeyGetAmount;
        private MethodHandle mhToItemKey, mhToStack, mhInsert, mhExtract;
        private MethodHandle mhGetPartByName, mhGetDisplayName;
        private Object aeKeyMapsInstance;

        Ae2Reflection() {
            try {
                var lookup = MethodHandles.publicLookup();
                var classLoader = getClass().getClassLoader();

                clGridHost = Class.forName("appeng.api.networking.IGridNode", false, classLoader);
                clGridNode = Class.forName("appeng.api.networking.IGridNode", false, classLoader);
                clGrid = Class.forName("appeng.api.networking.IGrid", false, classLoader);
                clStorageService = Class.forName("appeng.api.networking.storage.IStorageService", false, classLoader);
                clKeyCounter = Class.forName("appeng.api.storage.IKeyCounter", false, classLoader);
                clMEStorage = Class.forName("appeng.api.storage.MEStorage", false, classLoader);
                clItemKey = Class.forName("appeng.api.stacks.AEItemKey", false, classLoader);
                clFluidKey = Class.forName("appeng.api.stacks.AEFluidKey", false, classLoader);

                var clKey = Class.forName("appeng.api.stacks.AEKey", false, classLoader);
                var clCachedInventory = Class.forName("appeng.api.storage.CachedInventory", false, classLoader);
                var clGridLookup = Class.forName("appeng.api.networking.GridHelper", false, classLoader);
                var clAEKeyMaps = Class.forName("appeng.api.stacks.AEKeyMaps", false, classLoader);

                // GridHelper.getNode
                var mhGetNode = lookup.findStatic(clGridLookup, "getNode",
                        MethodType.methodType(clGridNode, BlockPos.class, Level.class));
                mhGetGridNode = mhGetNode;

                // IGridNode.getGrid
                mhGetGrid = lookup.findVirtual(clGridNode, "getGrid", MethodType.methodType(clGrid));

                // IGrid.getStorageService
                mhGetStorageService = lookup.findVirtual(clGrid, "getStorageService",
                        MethodType.methodType(clStorageService));

                // CachedInventory
                mhGetCachedInventory = lookup.findVirtual(clCachedInventory, "getCachedInventory",
                        MethodType.methodType(clKeyCounter));

                // MEStorage.getAvailableStacks
                mhGetAvailableStacks = lookup.findVirtual(clMEStorage, "getAvailableStacks",
                        MethodType.methodType(clKeyCounter));

                // KeyCounter iteration
                mhKeyCounterIterator = lookup.findVirtual(clKeyCounter, "iterator",
                        MethodType.methodType(Iterator.class));

                // AEKey methods
                mhKeyGetDisplayStack = lookup.findVirtual(clKey, "getDisplayStack",
                        MethodType.methodType(ItemStack.class));

                var clKeyCount = Class.forName("appeng.api.stacks.KeyCount", false, classLoader);
                mhKeyGetAmount = lookup.findVirtual(clKeyCount, "getAmount",
                        MethodType.methodType(long.class));
                mhKeyGetDisplayStack = lookup.findVirtual(clKeyCount, "getKey",
                        MethodType.methodType(clKey))
                        .asType(MethodType.methodType(Object.class, Object.class));

                // AEItemKey methods
                mhToItemKey = lookup.findStatic(clItemKey, "of",
                        MethodType.methodType(clItemKey, ItemStack.class));

                mhToStack = lookup.findVirtual(clItemKey, "toStack",
                        MethodType.methodType(ItemStack.class, int.class));

                // Insert/extract
                mhInsert = lookup.findVirtual(clMEStorage, "insert",
                        MethodType.methodType(long.class, clKey, long.class, Object.class));
                mhExtract = lookup.findVirtual(clMEStorage, "extract",
                        MethodType.methodType(long.class, clKey, long.class, Object.class));

                // AEKeyMaps for icon resolution
                aeKeyMapsInstance = lookup.findStatic(clAEKeyMaps, "instance",
                        MethodType.methodType(clAEKeyMaps)).invoke();

                loaded = true;
            } catch (Throwable e) {
                LOGGER.warn("AE2 reflection load failed: {}", e.getMessage());
            }
        }

        @Nullable Object findStorageService(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
            try {
                var node = mhGetGridNode.invoke(pos, level);
                if (node == null) return null;
                var grid = mhGetGrid.invoke(node);
                if (grid == null) return null;
                return mhGetStorageService.invoke(grid);
            } catch (Throwable e) {
                return null;
            }
        }

        @Nullable Object snapshot(Object storageService) throws Throwable {
            return mhGetAvailableStacks.invoke(storageService);
        }

        @Nullable Object snapshotCached(Object storageService) throws Throwable {
            if (storageService instanceof Object cached) {
                return mhGetCachedInventory.invoke(cached);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        List<SlotView> fillFromKeyCounter(Object keyCounter) throws Throwable {
            List<SlotView> result = new ArrayList<>();
            if (keyCounter instanceof Iterable<?> iterable) {
                for (var entry : iterable) {
                    var key = mhKeyGetDisplayStack.invoke(entry);
                    long amount = (long) mhKeyGetAmount.invoke(entry);
                    if (clItemKey.isInstance(key)) {
                        Object stackObj = mhToStack.invoke(key, 1);
                        if (stackObj instanceof ItemStack stack) {
                            result.add(new SlotView(key, stack, amount));
                        }
                    }
                }
            }
            return result;
        }

        List<FluidStack> collectFluidsFromStorage(Object storageService) throws Throwable {
            List<FluidStack> result = new ArrayList<>();
            var snapshot = mhGetAvailableStacks.invoke(storageService);
            if (snapshot instanceof Iterable<?> iterable) {
                for (var entry : iterable) {
                    var key = mhKeyGetDisplayStack.invoke(entry);
                    long amount = (long) mhKeyGetAmount.invoke(entry);
                    if (clFluidKey.isInstance(key)) {
                        try {
                            var fluidKeyClass = clFluidKey;
                            var mhToFluidStack = MethodHandles.publicLookup()
                                    .findVirtual(fluidKeyClass, "toStack",
                                            MethodType.methodType(FluidStack.class, long.class));
                            FluidStack fs = (FluidStack) mhToFluidStack.invoke(key, amount);
                            result.add(fs);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return result;
        }

        @Nullable Object toItemKey(ItemStack stack) throws Throwable {
            return mhToItemKey.invoke(stack);
        }

        long insert(Object storageService, Object key, long amount, Object simulate) throws Throwable {
            return (long) mhInsert.invoke(storageService, key, amount, simulate);
        }

        long extract(Object storageService, Object key, long amount, Object simulate) throws Throwable {
            return (long) mhExtract.invoke(storageService, key, amount, simulate);
        }

        @Nullable
        String resolveIcon(Level level, BlockPos pos, @Nullable Direction face, String label) {
            try {
                var blockEntity = level.getBlockEntity(pos);
                if (blockEntity == null) return null;

                var parts = label.toLowerCase().replaceAll("[^a-z0-9_]", " ").trim().split("\\s+");
                for (var part : parts) {
                    var itemId = "ae2:" + part;
                    var item = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getOptional(net.minecraft.resources.ResourceLocation.parse(itemId));
                    if (item.isPresent()) return itemId;
                }

                var block = level.getBlockState(pos).getBlock();
                var item = block.asItem();
                if (item != null && item != net.minecraft.world.level.block.Blocks.AIR.asItem()) {
                    var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                    if (id != null) return id.toString();
                }
            } catch (Exception ignored) {
            }
            return null;
        }
    }
}
