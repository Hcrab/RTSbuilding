package com.rtsbuilding.rtsbuilding.compat.ae2;


import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import net.minecraftforge.items.IItemHandler;

public final class RtsAe2Compat {
    public interface ReportedCountItemHandler {
        long getReportedCount(int slot);
    }

    public interface AnySlotInsertItemHandler {
        ItemStack insertItemAnywhere(ItemStack stack, boolean simulate);
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

    public static long getReportedCount(IItemHandler handler, int slot, ItemStack fallbackStack) {
        if (handler instanceof com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler reported) {
            return Math.max(0L, reported.getReportedCount(slot));
        }
        if (handler instanceof ReportedCountItemHandler reported) {
            return Math.max(0L, reported.getReportedCount(slot));
        }
        return fallbackStack == null || fallbackStack.isEmpty() ? 0L : Math.max(0L, fallbackStack.getCount());
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof Ae2NetworkItemHandler networkHandler) {
            networkHandler.release();
        }
    }

    public static String resolveGuiBindingIconItemId(Level level, BlockPos pos, Direction face, String labelHint) {
        if (level == null || pos == null || !ModList.get().isLoaded("ae2") || !level.hasChunkAt(pos)) {
            return "";
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return "";
        }

        String namespace = resolveItemNamespace(state);
        if (namespace.isBlank()) {
            return "";
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        MenuProvider provider = state.getMenuProvider(level, pos);
        if (provider == null && blockEntity instanceof MenuProvider menuProvider) {
            provider = menuProvider;
        }

        LinkedHashSet<String> candidatePaths = new LinkedHashSet<>();
        addIconCandidates(candidatePaths, labelHint);
        addIconCandidates(candidatePaths, provider == null || provider.getDisplayName() == null ? "" : provider.getDisplayName().getString());
        addIconCandidates(candidatePaths, provider == null ? "" : provider.getClass().getName());
        addIconCandidates(candidatePaths, blockEntity == null ? "" : blockEntity.getClass().getName());

        Object part = resolveDirectionalPart(blockEntity, face);
        addIconCandidates(candidatePaths, part == null ? "" : part.getClass().getName());
        if (part instanceof MenuProvider partProvider && partProvider.getDisplayName() != null) {
            addIconCandidates(candidatePaths, partProvider.getDisplayName().getString());
        }

        return resolveRegisteredItemId(namespace, candidatePaths);
    }

    private static String resolveItemNamespace(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blockId == null) {
            return "";
        }
        return "ae2".equals(blockId.getNamespace()) ? blockId.getNamespace() : "";
    }

    private static Object resolveDirectionalPart(BlockEntity blockEntity, Direction face) {
        if (blockEntity == null || face == null) {
            return null;
        }
        Method method = findMethod(blockEntity.getClass(), "getPart", Direction.class);
        return method == null ? null : invokeReflectively(method, blockEntity, face);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        if (owner == null) {
            return null;
        }

        try {
            Method method = owner.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
        }

        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object invokeReflectively(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String resolveRegisteredItemId(String preferredNamespace, LinkedHashSet<String> candidatePaths) {
        for (String path : candidatePaths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            if (!preferredNamespace.isBlank()) {
                ResourceLocation preferred = ResourceLocation.tryParse(preferredNamespace + ":" + path);
                if (preferred != null && BuiltInRegistries.ITEM.containsKey(preferred)) {
                    return preferred.toString();
                }
            }

            ResourceLocation ae2 = ResourceLocation.tryParse("ae2:" + path);
            if (ae2 != null && BuiltInRegistries.ITEM.containsKey(ae2)) {
                return ae2.toString();
            }

            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                if (path.equals(key.getPath())) {
                    return key.toString();
                }
            }
        }
        return "";
    }

    private static void addIconCandidates(LinkedHashSet<String> out, String text) {
        String normalized = normalizeToItemPath(text);
        if (normalized.isBlank()) {
            return;
        }

        addCandidate(out, normalized);

        String stripped = stripGuiNoise(normalized);
        if (!stripped.equals(normalized)) {
            addCandidate(out, stripped);
        }
        addAliasCandidates(out, stripped);
    }

    private static void addAliasCandidates(LinkedHashSet<String> out, String normalized) {
        boolean terminal = normalized.contains("terminal") || normalized.contains("term");
        boolean crafting = normalized.contains("crafting");
        boolean pattern = normalized.contains("pattern");
        boolean encoding = normalized.contains("encoding");
        boolean access = normalized.contains("access");
        boolean provider = normalized.contains("provider");

        if (crafting && terminal) {
            addCandidate(out, "crafting_terminal");
        }
        if (pattern && terminal && (encoding || normalized.equals("pattern_terminal"))) {
            addCandidate(out, "pattern_encoding_terminal");
            addCandidate(out, "pattern_terminal");
        }
        if (pattern && terminal && provider) {
            addCandidate(out, "pattern_provider_terminal");
            addCandidate(out, "pattern_access_terminal");
        }
        if (pattern && terminal && access) {
            addCandidate(out, "pattern_access_terminal");
        }
        if (pattern && provider && !terminal) {
            addCandidate(out, "pattern_provider");
        }
        if (normalized.equals("terminal")) {
            addCandidate(out, "terminal");
        }
    }

    private static void addCandidate(LinkedHashSet<String> out, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        out.add(path);
    }

    private static String stripGuiNoise(String normalized) {
        String stripped = normalized;
        if (stripped.startsWith("me_")) {
            stripped = stripped.substring(3);
        }

        String previous;
        do {
            previous = stripped;
            stripped = trimSuffix(stripped, "_menu_provider");
            stripped = trimSuffix(stripped, "_menuprovider");
            stripped = trimSuffix(stripped, "_menu_host");
            stripped = trimSuffix(stripped, "_menuhost");
            stripped = trimSuffix(stripped, "_menu");
            stripped = trimSuffix(stripped, "_screen");
            stripped = trimSuffix(stripped, "_part");
            stripped = trimSuffix(stripped, "_host");
            stripped = trimSuffix(stripped, "_block_entity");
            stripped = trimSuffix(stripped, "_blockentity");
            stripped = trimSuffix(stripped, "_block");
        } while (!previous.equals(stripped));

        return stripped;
    }

    private static String trimSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private static String normalizeToItemPath(String text) {
        if (text == null) {
            return "";
        }

        String simple = text.strip();
        if (simple.isEmpty()) {
            return "";
        }
        int dot = simple.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < simple.length()) {
            simple = simple.substring(dot + 1);
        }

        String normalized = simple
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_");
        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("_")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static final class Ae2NetworkItemHandler implements IItemHandler, ReportedCountItemHandler,
            com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler, AnySlotInsertItemHandler,
            com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler {
        private final ServerPlayer player;
        private final Object storageService;
        private final Ae2Reflection reflection;
        private final List<SlotView> slots = new ArrayList<>();
        private boolean released;

        private Ae2NetworkItemHandler(ServerPlayer player, Object storageService, Ae2Reflection reflection) {
            this.player = player;
            this.storageService = storageService;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
            return this.released ? 0 : this.slots.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (this.released || slot < 0 || slot >= this.slots.size()) {
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
            if (this.released || this.player == null || this.storageService == null
                    || stack == null || stack.isEmpty()) {
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
                this.reflection.invalidateCache(this.storageService);
                refreshSnapshot();
            }

            ItemStack remain = stack.copy();
            remain.shrink((int) Math.min(Integer.MAX_VALUE, inserted));
            return remain;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (this.released || this.player == null || this.storageService == null
                    || slot < 0 || slot >= this.slots.size() || amount <= 0) {
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
                this.reflection.invalidateCache(this.storageService);
                long nextAmount = Math.max(0L, view.amount() - extracted);
                this.slots.set(slot, new SlotView(view.key(), view.displayStack(), nextAmount));
            }

            return this.reflection.toStack(view.key(), (int) Math.min(Integer.MAX_VALUE, extracted));
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (this.released || this.player == null || this.storageService == null
                    || targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < this.slots.size(); slot++) {
                SlotView view = this.slots.get(slot);
                if (view.amount() <= 0L || view.displayStack().getItem() != targetItem) {
                    continue;
                }
                long extracted = this.reflection.extract(this.storageService, view.key(), amount, this.player, simulate);
                if (extracted <= 0L) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    this.reflection.invalidateCache(this.storageService);
                    long nextAmount = Math.max(0L, view.amount() - extracted);
                    this.slots.set(slot, new SlotView(view.key(), view.displayStack(), nextAmount));
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
            return !this.released && this.storageService != null
                    && this.reflection.toItemKey(stack) != null;
        }

        @Override
        public long getReportedCount(int slot) {
            if (this.released || slot < 0 || slot >= this.slots.size()) {
                return 0L;
            }
            return this.slots.get(slot).amount();
        }

        private void refreshSnapshot() {
            if (this.released || this.storageService == null) return;
            this.slots.clear();
            for (SlotView slot : this.reflection.snapshot(this.storageService)) {
                if (slot != null && slot.amount() > 0L && !slot.displayStack().isEmpty()) {
                    this.slots.add(slot);
                }
            }
        }

        private void release() {
            this.released = true;
            this.slots.clear();
        }

    }

    private record SlotView(Object key, ItemStack displayStack, long amount) {
    }

    private static final class Ae2Reflection {
        private final Method gridHelperGetNodeHost;
        private final Method hostGetGridNode;
        private final Method gridNodeGetGrid;
        private final Method gridGetService;
        private final Class<?> storageServiceClass;
        private final Method storageServiceGetCachedInventory;
        private final Method storageServiceGetInventory;
        private final Method storageServiceInvalidateCache;
        private final Method keyCounterIterator;
        private final Method keyEntryGetKey;
        private final Method keyEntryGetLongValue;
        private final Class<?> aeItemKeyClass;
        private final Method aeItemKeyOfStack;
        private final Method aeItemKeyToStack;
        private final Method meStorageInsert;
        private final Method meStorageExtract;
        private final Class<?> actionableClass;
        private final Object actionableSimulate;
        private final Object actionableModulate;
        private final Method actionSourceOfPlayer;

        private Ae2Reflection(
                Method gridHelperGetNodeHost,
                Method hostGetGridNode,
                Method gridNodeGetGrid,
                Method gridGetService,
                Class<?> storageServiceClass,
                Method storageServiceGetCachedInventory,
                Method storageServiceGetInventory,
                Method storageServiceInvalidateCache,
                Method keyCounterIterator,
                Method keyEntryGetKey,
                Method keyEntryGetLongValue,
                Class<?> aeItemKeyClass,
                Method aeItemKeyOfStack,
                Method aeItemKeyToStack,
                Method meStorageInsert,
                Method meStorageExtract,
                Class<?> actionableClass,
                Object actionableSimulate,
                Object actionableModulate,
                Method actionSourceOfPlayer) {
            this.gridHelperGetNodeHost = gridHelperGetNodeHost;
            this.hostGetGridNode = hostGetGridNode;
            this.gridNodeGetGrid = gridNodeGetGrid;
            this.gridGetService = gridGetService;
            this.storageServiceClass = storageServiceClass;
            this.storageServiceGetCachedInventory = storageServiceGetCachedInventory;
            this.storageServiceGetInventory = storageServiceGetInventory;
            this.storageServiceInvalidateCache = storageServiceInvalidateCache;
            this.keyCounterIterator = keyCounterIterator;
            this.keyEntryGetKey = keyEntryGetKey;
            this.keyEntryGetLongValue = keyEntryGetLongValue;
            this.aeItemKeyClass = aeItemKeyClass;
            this.aeItemKeyOfStack = aeItemKeyOfStack;
            this.aeItemKeyToStack = aeItemKeyToStack;
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
                Class<?> gridHelperClass = Class.forName("appeng.api.networking.GridHelper");
                Method gridHelperGetNodeHost = gridHelperClass.getMethod("getNodeHost", Level.class, BlockPos.class);
                Class<?> hostClass = Class.forName("appeng.api.networking.IInWorldGridNodeHost");
                Method hostGetGridNode = hostClass.getMethod("getGridNode", Direction.class);

                Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
                Method gridNodeGetGrid = gridNodeClass.getMethod("getGrid");

                Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
                Class<?> storageServiceClass = Class.forName("appeng.api.networking.storage.IStorageService");
                Method gridGetService = gridClass.getMethod("getService", Class.class);

                Method storageServiceGetCachedInventory = storageServiceClass.getMethod("getCachedInventory");
                Method storageServiceGetInventory = storageServiceClass.getMethod("getInventory");
                Method storageServiceInvalidateCache = storageServiceClass.getMethod("invalidateCache");

                Class<?> keyCounterClass = Class.forName("appeng.api.stacks.KeyCounter");
                Method keyCounterIterator = keyCounterClass.getMethod("iterator");

                Class<?> keyEntryClass = Class.forName("it.unimi.dsi.fastutil.objects.Object2LongMap$Entry");
                Method keyEntryGetKey = keyEntryClass.getMethod("getKey");
                Method keyEntryGetLongValue = keyEntryClass.getMethod("getLongValue");

                Class<?> aeItemKeyClass = Class.forName("appeng.api.stacks.AEItemKey");
                Method aeItemKeyOfStack = aeItemKeyClass.getMethod("of", ItemStack.class);
                Method aeItemKeyToStack = aeItemKeyClass.getMethod("toStack", int.class);

                Class<?> meStorageClass = Class.forName("appeng.api.storage.MEStorage");
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
                        gridHelperGetNodeHost,
                        hostGetGridNode,
                        gridNodeGetGrid,
                        gridGetService,
                        storageServiceClass,
                        storageServiceGetCachedInventory,
                        storageServiceGetInventory,
                        storageServiceInvalidateCache,
                        keyCounterIterator,
                        keyEntryGetKey,
                        keyEntryGetLongValue,
                        aeItemKeyClass,
                        aeItemKeyOfStack,
                        aeItemKeyToStack,
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
            Object host = invoke(this.gridHelperGetNodeHost, null, level, pos);
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
            Object node = invoke(this.hostGetGridNode, host, new Object[] { null });
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

        private List<SlotView> snapshot(Object storageService) {
            List<SlotView> out = new ArrayList<>();
            Object keyCounter = invoke(this.storageServiceGetCachedInventory, storageService);
            if (keyCounter == null) {
                return out;
            }

            Iterator<?> iterator = (Iterator<?>) invoke(this.keyCounterIterator, keyCounter);
            if (iterator == null) {
                return out;
            }

            while (iterator.hasNext()) {
                Object entry = iterator.next();
                Object key = invoke(this.keyEntryGetKey, entry);
                if (key == null || !this.aeItemKeyClass.isInstance(key)) {
                    continue;
                }
                long amount = asLong(invoke(this.keyEntryGetLongValue, entry));
                if (amount <= 0L) {
                    continue;
                }
                ItemStack display = toStack(key, 1);
                if (display.isEmpty()) {
                    continue;
                }
                display.setCount(1);
                out.add(new SlotView(key, display, amount));
            }
            return out;
        }

        private Object toItemKey(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            Object key = invoke(this.aeItemKeyOfStack, null, stack);
            return this.aeItemKeyClass.isInstance(key) ? key : null;
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

        private void invalidateCache(Object storageService) {
            invoke(this.storageServiceInvalidateCache, storageService);
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
            if (target == null && !Modifier.isStatic(method.getModifiers())) {
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
