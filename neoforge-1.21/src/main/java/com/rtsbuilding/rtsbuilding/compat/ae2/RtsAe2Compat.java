package com.rtsbuilding.rtsbuilding.compat.ae2;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public final class RtsAe2Compat {
    public interface ReportedCountItemHandler extends com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler {
    }

    public interface AnySlotInsertItemHandler extends com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler {
    }

    private static final Ae2Reflection REFLECTION = Ae2Reflection.tryLoad();

    private RtsAe2Compat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return null;
        }

        Object storageService = REFLECTION.findStorageService(level, pos);
        if (storageService == null) {
            return null;
        }
        return new Ae2NetworkItemHandler(player, storageService, REFLECTION);
    }

    /**
     * 检测指定方块位置是否为 AE2 网络节点（控制器、终端、线缆等）。
     */
    public static boolean isPositionAE2Node(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return false;
        }
        return REFLECTION.findStorageService(level, pos) != null;
    }

    /**
     * 从 AE2 网络中指定方块位置收集所有流体条目，追加到 amounts / capacities 映射中。
     * <p>通过 {@code skipFluidIds} 跳过已被常规流体处理器计数的流体 ID，避免双倍计数。</p>
     *
     * @param player      玩家
     * @param pos         方块位置（通常是 AE2 控制器/终端/线缆等网络节点）
     * @param amounts     输出：流体 ID → 总量（mB）
     * @param capacities  输出：流体 ID → 总容量（mB）
     * @param skipFluidIds 已通过常规 handler 计数的流体 ID 集合，AE2 收集时会跳过这些 ID
     */
    public static void collectNetworkFluids(ServerPlayer player, BlockPos pos,
                                             Map<String, Long> amounts, Map<String, Long> capacities,
                                             java.util.Set<String> skipFluidIds) {
        if (player == null || pos == null || REFLECTION == null || amounts == null || capacities == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return;
        }
        Object storageService = REFLECTION.findStorageService(level, pos);
        if (storageService == null) {
            return;
        }
        REFLECTION.collectFluidsFromStorage(storageService, amounts, capacities, skipFluidIds);
    }

    public static long getReportedCount(IItemHandler handler, int slot, ItemStack fallbackStack) {
        if (handler instanceof ReportedCountItemHandler reported) {
            return Math.max(0L, reported.getReportedCount(slot));
        }
        return fallbackStack == null || fallbackStack.isEmpty() ? 0L : Math.max(0L, fallbackStack.getCount());
    }

    /**
     * Releases AE2-specific handler resources (clears the slot list and nulls
     * the player / storage service references) so the GC can reclaim memory
     * immediately. Safe to call on non-AE2 handlers — the instanceof check
     * will simply skip them.
     * <p>
     * Called from the tick service's {@code unregisterPlayer} during
     * player logout or RTS disable.
     */
    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof Ae2NetworkItemHandler ae2) {
            ae2.release();
        }
    }

    public static String resolveGuiBindingIconItemId(Level level, BlockPos pos, Direction face, String labelHint) {
        return RtsAe2IconResolver.resolveGuiBindingIconItemId(level, pos, face, labelHint);
    }

    private static final class Ae2NetworkItemHandler implements IItemHandler, ReportedCountItemHandler,
            AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private ServerPlayer player;
        private Object storageService;
        private final Ae2Reflection reflection;
        private final List<SlotView> slots = new ArrayList<>();

        /**
         * Throttle counter for {@link #ensureFreshSnapshot()}.
         * <p>
         * The {@link RtsHandlerCache} update loop calls {@link #ensureFreshSnapshot()}
         * once per refresh cycle, and we only perform the expensive
         * {@code MEStorage.getAvailableStacks()} scan every N calls. This
         * keeps the effective rate proportional to the adaptive tick rate:
         * <ul>
         *   <li>Active use (1 tick rate): refresh every ~10 ticks (500ms)</li>
         *   <li>Normal (8 tick rate): refresh every ~80 ticks (4s)</li>
         *   <li>Idle (60 tick rate): refresh every ~600 ticks (30s)</li>
         * </ul>
         */
        private static final int REFRESH_THROTTLE = 10;
        private int refreshCounter = 0;
        private boolean snapshotStale;

        private Ae2NetworkItemHandler(ServerPlayer player, Object storageService, Ae2Reflection reflection) {
            this.player = player;
            this.storageService = storageService;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
            // No refresh here — that is delegated to {@link #ensureFreshSnapshot()}
            // which is called once per update cycle by the cache layer.
            //
            // Must return exactly slots.size(), not size()+1 or any other
            // constant, so RtsHandlerCache iteration and getStackInSlot /
            // extractItem bounds checks are consistent. The extra +1 that
            // existed historically created a dead ghost slot that wasted
            // one readSlot() call per update cycle for no benefit.
            return this.slots.size();
        }

        @Override
        public void ensureFreshSnapshot() {
            boolean shouldRefresh = this.snapshotStale;
            if (!shouldRefresh) {
                this.refreshCounter++;
                shouldRefresh = this.refreshCounter >= REFRESH_THROTTLE;
            }
            if (shouldRefresh) {
                // Use cached inventory — O(1) retrieval, no full network scan.
                refreshSnapshotCached();
            }
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.slots.size()) {
                return ItemStack.EMPTY;
            }
            SlotView view = this.slots.get(slot);
            return view.amount() > 0L ? view.displayStack().copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (slot < 0 || slot >= getSlots()) {
                return stack.copy();
            }
            return insertItemAnywhere(stack, simulate);
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            Object key = this.reflection.toItemKey(stack);
            if (key == null) {
                return stack.copy();
            }

            long inserted = this.reflection.insert(this.storageService, key, stack.getCount(), this.player, simulate);

            if (inserted <= 0L) {
                return stack.copy();
            }

            if (!simulate) {
                this.snapshotStale = true;
            }

            ItemStack remain = stack.copy();
            remain.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
            return remain;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.slots.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            SlotView view = this.slots.get(slot);
            if (view.amount() <= 0L) {
                return ItemStack.EMPTY;
            }

            long extracted = this.reflection.extract(this.storageService, view.key(), amount, this.player, simulate);

            if (extracted <= 0L) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                long nextAmount = Math.max(0L, view.amount() - extracted);
                this.slots.set(slot, new SlotView(view.key(), view.displayStack(), nextAmount));
            }

            return this.reflection.toStack(view.key(), (int) Math.min(Integer.MAX_VALUE, extracted));
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            // Scan internal slots directly (avoids getStackInSlot() copy overhead)
            // and perform a single bulk extract via AE2's MEStorage.extract().
            for (int i = 0; i < this.slots.size(); i++) {
                SlotView view = this.slots.get(i);
                if (view.amount() <= 0L || view.displayStack().getItem() != targetItem) {
                    continue;
                }
                long extracted = this.reflection.extract(
                        this.storageService, view.key(), amount, this.player, simulate);
                if (extracted <= 0L) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    long nextAmount = Math.max(0L, view.amount() - extracted);
                    this.slots.set(i, new SlotView(view.key(), view.displayStack(), nextAmount));
                }
                return this.reflection.toStack(view.key(), (int) Math.min(Integer.MAX_VALUE, extracted));
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.reflection.toItemKey(stack) != null;
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.slots.size()) {
                return 0L;
            }
            return this.slots.get(slot).amount();
        }

        /**
         * Steady-state refresh using AE2's pre-built cached inventory.
         * <p>
         * Unlike {@link #refreshSnapshot()} which calls the expensive
         * {@code MEStorage.getAvailableStacks()} (full network scan),
         * this uses {@code IStorageService.getCachedInventory()} which
         * returns an already-computed snapshot built at the end of each
         * server tick by AE2's {@code StorageService.onServerEndTick()}.
         * <p>
         * Retrieving the cached inventory is O(1) — no network scan,
         * no iterating cells. The data may be up to 1 tick stale, which
         * is perfectly acceptable for a cache refresh loop.
         */
        private void refreshSnapshotCached() {
            this.slots.clear();
            for (SlotView slot : this.reflection.snapshotCached(this.storageService)) {
                if (slot != null && slot.amount() > 0L && !slot.displayStack().isEmpty()) {
                    this.slots.add(slot);
                }
            }
            this.refreshCounter = 0;
            this.snapshotStale = false;
        }

        private void refreshSnapshot() {
            this.slots.clear();
            for (SlotView slot : this.reflection.snapshot(this.storageService)) {
                if (slot != null && slot.amount() > 0L && !slot.displayStack().isEmpty()) {
                    this.slots.add(slot);
                }
            }
            this.refreshCounter = 0;
            this.snapshotStale = false;
        }

        /**
         * Releases all resource-heavy references held by this handler so the
         * GC can reclaim memory immediately instead of waiting for the handler
         * object itself to become unreachable.
         * <p>
         * Called from {@link RtsAe2Compat#releaseNetworkHandler(IItemHandler)}
         * when the handler is unmounted from the player's aggregate storage.
         * After this call the handler must NOT be used again.
         */
        void release() {
            this.slots.clear();
            // Null out the heavy references so they can be GC'd even if
            // something accidentally retains a dangling handler reference.
            this.player = null;
            this.storageService = null;
        }
    }

    private record SlotView(Object key, ItemStack displayStack, long amount) {
    }

    private static final class Ae2Reflection {
        private final BlockCapability<?, ?> inWorldGridNodeHostCapability;
        private final Method hostGetGridNode;
        private final Method gridNodeGetGrid;
        private final Method gridGetService;
        private final Class<?> storageServiceClass;
        private final Method storageServiceGetCachedInventory;
        private final Method storageServiceGetInventory;
        private final Class<?> keyCounterClass;
        private final Constructor<?> keyCounterConstructor;
        private final Method meStorageGetAvailableStacks;
        private final Method keyCounterIterator;
        private final Method keyEntryGetKey;
        private final Method keyEntryGetLongValue;
        private final Class<?> aeItemKeyClass;
        private final Method aeItemKeyOfStack;
        private final Method aeItemKeyToStack;
        /** AEFluidKey 反射——用于从 AE2 网络收集流体条目 */
        private final Class<?> aeFluidKeyClass;
        private final Method aeFluidKeyGetFluid;
        private final Method meStorageInsert;
        private final Method meStorageExtract;
        private final Class<?> actionableClass;
        private final Object actionableSimulate;
        private final Object actionableModulate;
        private final Method actionSourceOfPlayer;

        private Ae2Reflection(
                BlockCapability<?, ?> inWorldGridNodeHostCapability,
                Method hostGetGridNode,
                Method gridNodeGetGrid,
                Method gridGetService,
                Class<?> storageServiceClass,
                Method storageServiceGetCachedInventory,
                Method storageServiceGetInventory,
                Class<?> keyCounterClass,
                Constructor<?> keyCounterConstructor,
                Method meStorageGetAvailableStacks,
                Method keyCounterIterator,
                Method keyEntryGetKey,
                Method keyEntryGetLongValue,
                Class<?> aeItemKeyClass,
                Method aeItemKeyOfStack,
                Method aeItemKeyToStack,
                Class<?> aeFluidKeyClass,
                Method aeFluidKeyGetFluid,
                Method meStorageInsert,
                Method meStorageExtract,
                Class<?> actionableClass,
                Object actionableSimulate,
                Object actionableModulate,
                Method actionSourceOfPlayer) {
            this.inWorldGridNodeHostCapability = inWorldGridNodeHostCapability;
            this.hostGetGridNode = hostGetGridNode;
            this.gridNodeGetGrid = gridNodeGetGrid;
            this.gridGetService = gridGetService;
            this.storageServiceClass = storageServiceClass;
            this.storageServiceGetCachedInventory = storageServiceGetCachedInventory;
            this.storageServiceGetInventory = storageServiceGetInventory;
            this.keyCounterClass = keyCounterClass;
            this.keyCounterConstructor = keyCounterConstructor;
            this.meStorageGetAvailableStacks = meStorageGetAvailableStacks;
            this.keyCounterIterator = keyCounterIterator;
            this.keyEntryGetKey = keyEntryGetKey;
            this.keyEntryGetLongValue = keyEntryGetLongValue;
            this.aeItemKeyClass = aeItemKeyClass;
            this.aeItemKeyOfStack = aeItemKeyOfStack;
            this.aeItemKeyToStack = aeItemKeyToStack;
            this.aeFluidKeyClass = aeFluidKeyClass;
            this.aeFluidKeyGetFluid = aeFluidKeyGetFluid;
            this.meStorageInsert = meStorageInsert;
            this.meStorageExtract = meStorageExtract;
            this.actionableClass = actionableClass;
            this.actionableSimulate = actionableSimulate;
            this.actionableModulate = actionableModulate;
            this.actionSourceOfPlayer = actionSourceOfPlayer;
        }

        private static Ae2Reflection tryLoad() {
            if (!ModList.get().isLoaded("ae2")) {
                return null;
            }

            try {
                Class<?> aeCapabilitiesClass = Class.forName("appeng.api.AECapabilities");
                Field inWorldField = aeCapabilitiesClass.getField("IN_WORLD_GRID_NODE_HOST");
                BlockCapability<?, ?> inWorldCapability = (BlockCapability<?, ?>) inWorldField.get(null);

                Class<?> hostClass = Class.forName("appeng.api.networking.IInWorldGridNodeHost");
                Method hostGetGridNode = hostClass.getMethod("getGridNode", Direction.class);

                Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
                Method gridNodeGetGrid = gridNodeClass.getMethod("getGrid");

                Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
                Class<?> storageServiceClass = Class.forName("appeng.api.networking.storage.IStorageService");
                Method gridGetService = gridClass.getMethod("getService", Class.class);

                Method storageServiceGetCachedInventory = storageServiceClass.getMethod("getCachedInventory");
                Method storageServiceGetInventory = storageServiceClass.getMethod("getInventory");

                Class<?> keyCounterClass = Class.forName("appeng.api.stacks.KeyCounter");
                Constructor<?> keyCounterConstructor = keyCounterClass.getConstructor();
                Method keyCounterIterator = keyCounterClass.getMethod("iterator");

                Class<?> keyEntryClass = Class.forName("it.unimi.dsi.fastutil.objects.Object2LongMap$Entry");
                Method keyEntryGetKey = keyEntryClass.getMethod("getKey");
                Method keyEntryGetLongValue = keyEntryClass.getMethod("getLongValue");

                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                Method aeItemKeyOfStack = aeItemKeyClass.getMethod("of", ItemStack.class);
                Method aeItemKeyToStack = aeItemKeyClass.getMethod("toStack", int.class);

                Class<?> aeFluidKeyClass = Class.forName("appeng.api.stacks.AEFluidKey");
                Method aeFluidKeyGetFluid = aeFluidKeyClass.getMethod("getFluid");

                Class<?> meStorageClass = Class.forName("appeng.api.storage.MEStorage");
                Method meStorageGetAvailableStacks = meStorageClass.getMethod("getAvailableStacks", keyCounterClass);
                Class<?> aeKeyClass = Class.forName("appeng.api.stacks.AEKey");
                Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
                Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
                Method meStorageInsert = meStorageClass.getMethod("insert", aeKeyClass, long.class, actionableClass, actionSourceClass);
                Method meStorageExtract = meStorageClass.getMethod("extract", aeKeyClass, long.class, actionableClass, actionSourceClass);

                Object actionableSimulate = Enum.valueOf((Class<? extends Enum>) actionableClass.asSubclass(Enum.class), "SIMULATE");
                Object actionableModulate = Enum.valueOf((Class<? extends Enum>) actionableClass.asSubclass(Enum.class), "MODULATE");

                Method actionSourceOfPlayer = actionSourceClass.getMethod(
                        "ofPlayer",
                        Class.forName("net.minecraft.world.entity.player.Player"));

                return new Ae2Reflection(
                        inWorldCapability,
                        hostGetGridNode,
                        gridNodeGetGrid,
                        gridGetService,
                        storageServiceClass,
                        storageServiceGetCachedInventory,
                        storageServiceGetInventory,
                        keyCounterClass,
                        keyCounterConstructor,
                        meStorageGetAvailableStacks,
                        keyCounterIterator,
                        keyEntryGetKey,
                        keyEntryGetLongValue,
                        aeItemKeyClass,
                        aeItemKeyOfStack,
                        aeItemKeyToStack,
                        aeFluidKeyClass,
                        aeFluidKeyGetFluid,
                        meStorageInsert,
                        meStorageExtract,
                        actionableClass,
                        actionableSimulate,
                        actionableModulate,
                        actionSourceOfPlayer);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private Object findStorageService(ServerLevel level, BlockPos pos) {
            Object host = level.getCapability((BlockCapability<Object, Void>) this.inWorldGridNodeHostCapability, pos, null);
            if (host == null) {
                return null;
            }

            for (Direction direction : Direction.values()) {
                Object node = invoke(this.hostGetGridNode, host, direction);
                Object storageService = resolveStorageService(node);
                if (storageService != null) {
                    return storageService;
                }
            }
            Object node = invoke(this.hostGetGridNode, host, new Object[]{null});
            return resolveStorageService(node);
        }

        private Object resolveStorageService(Object node) {
            if (node == null) {
                return null;
            }
            Object grid = invoke(this.gridNodeGetGrid, node);
            if (grid == null) {
                return null;
            }
            Object storageService = invoke(this.gridGetService, grid, this.storageServiceClass);
            return this.storageServiceClass.isInstance(storageService) ? storageService : null;
        }

        /**
         * Full live snapshot via {@code MEStorage.getAvailableStacks()}.
         * <p>
         * Expensive — triggers a complete AE2 network scan. Only used during
         * initial construction. Steady-state refresh uses {@link #snapshotCached}
         * instead.
         */
        private List<SlotView> snapshot(Object storageService) {
            List<SlotView> out = new ArrayList<>();
            try {
                Object meStorage = invoke(this.storageServiceGetInventory, storageService);
                if (meStorage == null) {
                    return out;
                }
                Object keyCounter = this.keyCounterConstructor.newInstance();
                if (keyCounter == null) {
                    return out;
                }
                invoke(this.meStorageGetAvailableStacks, meStorage, keyCounter);
                fillFromKeyCounter(out, keyCounter);
            } catch (ReflectiveOperationException ignored) {
            }
            return out;
        }

        /**
         * Lightweight snapshot using AE2's pre-built cached inventory.
         * <p>
         * {@code IStorageService.getCachedInventory()} returns a {@code KeyCounter}
         * that AE2 builds at the end of each server tick. Retrieving it is O(1) —
         * no network scan. Falls back to {@link #snapshot} if the cache hasn't
         * been built yet.
         */
        private List<SlotView> snapshotCached(Object storageService) {
            List<SlotView> out = new ArrayList<>();
            Object keyCounter = invoke(this.storageServiceGetCachedInventory, storageService);
            if (keyCounter == null) {
                // Cache not yet built — fall back to a live scan once
                return snapshot(storageService);
            }
            fillFromKeyCounter(out, keyCounter);
            return out;
        }

        /**
         * Iterates a {@code KeyCounter} (or any {@code Object2LongMap<AEKey>})
         * and fills the output list with AEItemKey-based {@link SlotView} entries.
         */
        private void fillFromKeyCounter(List<SlotView> out, Object keyCounter) {
            try {
                Iterator<?> iterator = (Iterator<?>) invoke(this.keyCounterIterator, keyCounter);
                if (iterator == null) {
                    return;
                }
                while (iterator.hasNext()) {
                    Object entry = iterator.next();
                    Object key = invoke(this.keyEntryGetKey, entry);
                    if (key == null || !this.aeItemKeyClass.isInstance(key)) {
                        // Non-item entries (fluid, etc.) are expected in a
                        // mixed-storage AE2 network — skip them silently.
                        continue;
                    }
                    long amount = asLong(invoke(this.keyEntryGetLongValue, entry));
                    if (amount <= 0L) {
                        continue;
                    }
                    ItemStack display = toStack(key, 1);
                    if (display.isEmpty()) {
                        // The key claims to be AEItemKey but toStack(1)
                        // returned empty — skip instead of crashing.
                        continue;
                    }
                    display.setCount(1);
                    out.add(new SlotView(key, display, amount));
                }
            } catch (Exception e) {
                // Log warning in release builds; the storage will be
                // incomplete on this refresh cycle but will retry on
                // the next automated refresh.
                RtsbuildingMod.LOGGER.warn("AE2 KeyCounter iteration failed", e);
            }
        }

        private Object toItemKey(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            Object key = invoke(this.aeItemKeyOfStack, null, stack);
            return this.aeItemKeyClass.isInstance(key) ? key : null;
        }

        /**
         * 从 AE2 存储中收集所有流体条目，填充到 amounts / capacities 映射中。
         * <p>使用缓存清单（{@code getCachedInventory}），避免全网络扫描。</p>
         */
        private void collectFluidsFromStorage(Object storageService,
                                               Map<String, Long> amounts, Map<String, Long> capacities,
                                               Set<String> skipFluidIds) {
            try {
                Object meStorage = invoke(this.storageServiceGetInventory, storageService);
                if (meStorage == null) return;

                Object keyCounter = invoke(this.storageServiceGetCachedInventory, storageService);
                if (keyCounter == null) {
                    keyCounter = this.keyCounterConstructor.newInstance();
                    if (keyCounter == null) return;
                    invoke(this.meStorageGetAvailableStacks, meStorage, keyCounter);
                }

                Iterator<?> iterator = (Iterator<?>) invoke(this.keyCounterIterator, keyCounter);
                if (iterator == null) return;

                while (iterator.hasNext()) {
                    Object entry = iterator.next();
                    Object key = invoke(this.keyEntryGetKey, entry);
                    if (key == null || !this.aeFluidKeyClass.isInstance(key)) continue;

                    long amount = asLong(invoke(this.keyEntryGetLongValue, entry));
                    if (amount <= 0L) continue;

                    Object fluidObj = invoke(this.aeFluidKeyGetFluid, key);
                    if (!(fluidObj instanceof Fluid fluid)) continue;

                    ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
                    if (fluidId == null) continue;

                    String id = fluidId.toString();
                    // 跳过已被常规流体处理器计数的流体 ID，避免存储总线连接容器的双倍计数
                    if (skipFluidIds != null && skipFluidIds.contains(id)) continue;

                    amounts.merge(id, amount, Long::sum);
                    capacities.merge(id, amount, Long::sum);
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        private ItemStack toStack(Object key, int count) {
            if (key == null || !this.aeItemKeyClass.isInstance(key) || count <= 0) {
                return ItemStack.EMPTY;
            }
            Object stack = invoke(this.aeItemKeyToStack, key, count);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        }

        private long insert(Object storageService, Object key, long amount, ServerPlayer player, boolean simulate) {
            if (storageService == null || key == null || amount <= 0L) {
                return 0L;
            }
            Object meStorage = invoke(this.storageServiceGetInventory, storageService);
            if (meStorage == null) {
                return 0L;
            }
            Object source = invoke(this.actionSourceOfPlayer, null, player);
            return asLong(invoke(
                    this.meStorageInsert,
                    meStorage,
                    key,
                    amount,
                    simulate ? this.actionableSimulate : this.actionableModulate,
                    source));
        }

        private long extract(Object storageService, Object key, long amount, ServerPlayer player, boolean simulate) {
            if (storageService == null || key == null || amount <= 0L) {
                return 0L;
            }
            Object meStorage = invoke(this.storageServiceGetInventory, storageService);
            if (meStorage == null) {
                return 0L;
            }
            Object source = invoke(this.actionSourceOfPlayer, null, player);
            return asLong(invoke(
                    this.meStorageExtract,
                    meStorage,
                    key,
                    amount,
                    simulate ? this.actionableSimulate : this.actionableModulate,
                    source));
        }

        private boolean keysEqual(Object left, Object right) {
            return left == right || (left != null && left.equals(right));
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