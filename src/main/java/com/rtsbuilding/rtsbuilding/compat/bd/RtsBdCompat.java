package com.rtsbuilding.rtsbuilding.compat.bd;

import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Forge 1.20.1 的 Beyond Dimensions 反射兼容桥。
 *
 * <p>这个分支不把 BD API 作为编译依赖，但运行环境装有 BD 时仍然要走真实
 * {@code DimensionsNet}/{@code UnifiedStorage}。因此这里仅在反射成功时启用，
 * 不直接构造假库存，也不读取 BD 的存档 NBT。</p>
 */
public final class RtsBdCompat {
    public interface DirectExtractHandler {
        ItemStack tryExtractItem(Item target, int amount, boolean simulate);
    }

    private static final BdReflection REFLECTION = BdReflection.tryLoad();
    private static final long SNAPSHOT_REFRESH_INTERVAL_NANOS = 250_000_000L;

    private RtsBdCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static boolean hasPrimaryNetwork(ServerPlayer player) {
        return REFLECTION != null && REFLECTION.primaryNetwork(player) != null;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player) {
        if (REFLECTION == null) {
            return null;
        }
        Object network = REFLECTION.primaryNetwork(player);
        Object storage = REFLECTION.unifiedStorage(network);
        return storage == null ? null : new BdDirectItemHandler(storage, REFLECTION);
    }

    public static IFluidHandler createNetworkFluidHandler(ServerPlayer player) {
        if (REFLECTION == null) {
            return null;
        }
        Object network = REFLECTION.primaryNetwork(player);
        Object storage = REFLECTION.unifiedStorage(network);
        return storage == null ? null : REFLECTION.createFluidHandler(storage);
    }

    /**
     * 自动化测试用的真实 BD API 入口：只负责确保玩家有主网络，不参与生产逻辑。
     */
    public static boolean ensurePrimaryNetworkForTesting(ServerPlayer player, long slotCapacity, int slotMaxSize) {
        if (REFLECTION == null || player == null) {
            return false;
        }
        if (REFLECTION.primaryNetwork(player) != null) {
            return true;
        }
        return REFLECTION.createPrimaryNetwork(player, slotCapacity, slotMaxSize) != null;
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdDirectItemHandler bd) {
            bd.release();
        }
    }

    public static void refreshNetworkHandler(IItemHandler handler) {
        if (handler instanceof BdDirectItemHandler bd) {
            bd.refreshCache();
        }
    }

    public static String getNetworkDisplayName(ServerPlayer player) {
        if (REFLECTION == null) {
            return "Beyond Dimensions Network";
        }
        Object network = REFLECTION.primaryNetwork(player);
        String customName = REFLECTION.customName(network);
        return customName == null || customName.isBlank() ? "Beyond Dimensions Network" : customName;
    }

    private static final class BdDirectItemHandler implements IItemHandler, ReportedCountItemHandler,
            DirectExtractHandler, AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private final Object storage;
        private final BdReflection reflection;
        private final Map<Item, Object> itemToKey = new HashMap<>();
        private final List<Object> keys = new ArrayList<>();
        private final List<ItemStack> displayStacks = new ArrayList<>();
        private final List<Long> counts = new ArrayList<>();
        private long lastSnapshotRefreshNanos;

        private BdDirectItemHandler(Object storage, BdReflection reflection) {
            this.storage = storage;
            this.reflection = reflection;
            refreshCache();
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
            ItemStack result = this.displayStacks.get(slot).copy();
            result.setCount((int) Math.min(Integer.MAX_VALUE, amount));
            return result;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty() || slot < 0 || slot >= getSlots()) {
                return stack == null ? ItemStack.EMPTY : stack.copy();
            }
            return insertItemAnywhere(stack, simulate);
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object key = this.reflection.itemStackKey(stack);
            if (key == null) {
                return stack.copy();
            }
            ItemStack remainder = this.reflection.insert(this.storage, key, stack.getCount(), simulate);
            if (!simulate) {
                refreshCache();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.keys.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            Object key = this.keys.get(slot);
            ItemStack extracted = this.reflection.extract(this.storage, key, amount, simulate);
            if (!simulate) {
                refreshCache();
            }
            return extracted;
        }

        @Override
        public ItemStack tryExtractItem(Item target, int amount, boolean simulate) {
            if (target == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            Object key = this.itemToKey.get(target);
            if (key == null) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = this.reflection.extract(this.storage, key, amount, simulate);
            if (!simulate) {
                refreshCache();
            }
            return extracted;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            return tryExtractItem(targetItem, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.reflection.itemStackKey(stack) != null;
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.counts.size()) {
                return 0L;
            }
            return Math.max(0L, this.counts.get(slot));
        }

        @Override
        public void ensureFreshSnapshot() {
            long now = System.nanoTime();
            if (this.lastSnapshotRefreshNanos == 0L
                    || now < this.lastSnapshotRefreshNanos
                    || now - this.lastSnapshotRefreshNanos >= SNAPSHOT_REFRESH_INTERVAL_NANOS) {
                rebuildCache();
                this.lastSnapshotRefreshNanos = now;
            }
        }

        void refreshCache() {
            rebuildCache();
            this.lastSnapshotRefreshNanos = System.nanoTime();
        }

        void release() {
            this.itemToKey.clear();
            this.keys.clear();
            this.displayStacks.clear();
            this.counts.clear();
        }

        private void rebuildCache() {
            this.itemToKey.clear();
            this.keys.clear();
            this.displayStacks.clear();
            this.counts.clear();
            for (SlotView slot : this.reflection.snapshot(this.storage)) {
                if (slot.amount() <= 0L || slot.displayStack().isEmpty()) {
                    continue;
                }
                this.itemToKey.putIfAbsent(slot.displayStack().getItem(), slot.key());
                this.keys.add(slot.key());
                this.displayStacks.add(slot.displayStack().copyWithCount(1));
                this.counts.add(slot.amount());
            }
        }
    }

    private record SlotView(Object key, ItemStack displayStack, long amount) {
    }

    private static final class BdReflection {
        private final Method getPrimaryNetwork;
        private final Method createNewNetwork;
        private final Method getUnifiedStorage;
        private final Method getCustomName;
        private final Method getStorage;
        private final Method getOutStackByKey;
        private final Method insert;
        private final Method extract;
        private final Method keyAmountKey;
        private final Method keyAmountAmount;
        private final Method keyAmountIsEmpty;
        private final Method keyAmountToStack;
        private final Constructor<?> itemStackKeyConstructor;
        private final Constructor<?> fluidHandlerConstructor;

        private BdReflection(
                Method getPrimaryNetwork,
                Method createNewNetwork,
                Method getUnifiedStorage,
                Method getCustomName,
                Method getStorage,
                Method getOutStackByKey,
                Method insert,
                Method extract,
                Method keyAmountKey,
                Method keyAmountAmount,
                Method keyAmountIsEmpty,
                Method keyAmountToStack,
                Constructor<?> itemStackKeyConstructor,
                Constructor<?> fluidHandlerConstructor) {
            this.getPrimaryNetwork = getPrimaryNetwork;
            this.createNewNetwork = createNewNetwork;
            this.getUnifiedStorage = getUnifiedStorage;
            this.getCustomName = getCustomName;
            this.getStorage = getStorage;
            this.getOutStackByKey = getOutStackByKey;
            this.insert = insert;
            this.extract = extract;
            this.keyAmountKey = keyAmountKey;
            this.keyAmountAmount = keyAmountAmount;
            this.keyAmountIsEmpty = keyAmountIsEmpty;
            this.keyAmountToStack = keyAmountToStack;
            this.itemStackKeyConstructor = itemStackKeyConstructor;
            this.fluidHandlerConstructor = fluidHandlerConstructor;
        }

        private static BdReflection tryLoad() {
            if (!ModList.get().isLoaded("beyonddimensions")) {
                return null;
            }
            try {
                Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
                Class<?> dimensionsNetClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet");
                Class<?> unifiedStorageClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.dimensionnet.UnifiedStorage");
                Class<?> stackKeyClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.storage.key.IStackKey");
                Class<?> itemStackKeyClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey");
                Class<?> keyAmountClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.storage.key.KeyAmount");

                Method getPrimaryNetwork = dimensionsNetClass.getMethod("getPrimaryNetFromPlayer", playerClass);
                Method createNewNetwork = dimensionsNetClass.getMethod("createNewNetForPlayer",
                        playerClass, long.class, int.class);
                Method getUnifiedStorage = dimensionsNetClass.getMethod("getUnifiedStorage");
                Method getCustomName = dimensionsNetClass.getMethod("getCustomName");

                Method getStorage = unifiedStorageClass.getMethod("getStorage");
                Method getOutStackByKey = unifiedStorageClass.getMethod("getOutStackByKey", stackKeyClass);
                Method insert = unifiedStorageClass.getMethod("insert", stackKeyClass, long.class, boolean.class);
                Method extract = unifiedStorageClass.getMethod("extract",
                        stackKeyClass, long.class, boolean.class, boolean.class);

                Method keyAmountKey = keyAmountClass.getMethod("key");
                Method keyAmountAmount = keyAmountClass.getMethod("amount");
                Method keyAmountIsEmpty = keyAmountClass.getMethod("isEmpty");
                Method keyAmountToStack = keyAmountClass.getMethod("toStack");

                Constructor<?> itemStackKeyConstructor = itemStackKeyClass.getConstructor(ItemStack.class);
                Constructor<?> fluidHandlerConstructor = tryFluidHandlerConstructor(unifiedStorageClass);

                return new BdReflection(
                        getPrimaryNetwork,
                        createNewNetwork,
                        getUnifiedStorage,
                        getCustomName,
                        getStorage,
                        getOutStackByKey,
                        insert,
                        extract,
                        keyAmountKey,
                        keyAmountAmount,
                        keyAmountIsEmpty,
                        keyAmountToStack,
                        itemStackKeyConstructor,
                        fluidHandlerConstructor);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private static Constructor<?> tryFluidHandlerConstructor(Class<?> unifiedStorageClass) {
            try {
                Class<?> fluidHandlerClass = Class.forName(
                        "com.wintercogs.beyonddimensions.api.capability.helper.unordered.FluidUnifiedStorageHandler");
                return fluidHandlerClass.getConstructor(unifiedStorageClass);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private Object primaryNetwork(ServerPlayer player) {
            if (player == null || player.getServer() == null) {
                return null;
            }
            return invoke(this.getPrimaryNetwork, null, player);
        }

        private Object createPrimaryNetwork(ServerPlayer player, long slotCapacity, int slotMaxSize) {
            if (player == null || player.getServer() == null) {
                return null;
            }
            long safeCapacity = Math.max(64L, slotCapacity);
            int safeSlotMax = Math.max(64, slotMaxSize);
            return invoke(this.createNewNetwork, null, player, safeCapacity, safeSlotMax);
        }

        private Object unifiedStorage(Object network) {
            return invoke(this.getUnifiedStorage, network);
        }

        private String customName(Object network) {
            Object name = invoke(this.getCustomName, network);
            return name instanceof String value ? value : null;
        }

        private IFluidHandler createFluidHandler(Object storage) {
            if (this.fluidHandlerConstructor == null || storage == null) {
                return null;
            }
            try {
                Object handler = this.fluidHandlerConstructor.newInstance(storage);
                return handler instanceof IFluidHandler fluidHandler ? fluidHandler : null;
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | IllegalArgumentException ignored) {
                return null;
            }
        }

        private Object itemStackKey(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            try {
                return this.itemStackKeyConstructor.newInstance(stack.copy());
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException
                    | IllegalArgumentException ignored) {
                return null;
            }
        }

        private List<SlotView> snapshot(Object storage) {
            Object rawEntries = invoke(this.getStorage, storage);
            if (!(rawEntries instanceof Iterable<?> entries)) {
                return List.of();
            }
            List<SlotView> out = new ArrayList<>();
            for (Object entry : entries) {
                if (Boolean.TRUE.equals(invoke(this.keyAmountIsEmpty, entry))) {
                    continue;
                }
                Object key = invoke(this.keyAmountKey, entry);
                long amount = asLong(invoke(this.keyAmountAmount, entry));
                if (key == null || amount <= 0L) {
                    continue;
                }
                ItemStack display = outStack(storage, key, entry);
                if (!display.isEmpty()) {
                    out.add(new SlotView(key, display.copyWithCount(1), amount));
                }
            }
            return out;
        }

        private ItemStack insert(Object storage, Object key, long amount, boolean simulate) {
            Object result = invoke(this.insert, storage, key, amount, simulate);
            return keyAmountToStackOrEmpty(result);
        }

        private ItemStack extract(Object storage, Object key, long amount, boolean simulate) {
            Object result = invoke(this.extract, storage, key, amount, simulate, false);
            return keyAmountToStackOrEmpty(result);
        }

        private ItemStack outStack(Object storage, Object key, Object keyAmount) {
            Object rawStack = invoke(this.getOutStackByKey, storage, key);
            if (rawStack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                return itemStack;
            }
            return keyAmountToStackOrEmpty(keyAmount);
        }

        private ItemStack keyAmountToStackOrEmpty(Object keyAmount) {
            if (keyAmount == null || Boolean.TRUE.equals(invoke(this.keyAmountIsEmpty, keyAmount))) {
                return ItemStack.EMPTY;
            }
            Object rawStack = invoke(this.keyAmountToStack, keyAmount);
            return rawStack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        }

        private static long asLong(Object value) {
            return value instanceof Number number ? number.longValue() : 0L;
        }

        private static Object invoke(Method method, Object target, Object... args) {
            if (method == null) {
                return null;
            }
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
