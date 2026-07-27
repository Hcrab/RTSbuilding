package com.rtsbuilding.rtsbuilding.compat.ae2;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * AE2 rv6（Minecraft 1.12.2）网络库存桥。
 *
 * <p>生产类没有任何 AE2 链接期引用：未安装 AE2 时，这个类及核心模组仍可安全加载。
 * 安装后反射签名严格对应 rv6 的 IGridHost/IStorageGrid/IMEMonitor/IAEItemStack API。
 */
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

    public static IItemHandler createNetworkItemHandler(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) {
            return null;
        }
        World world = player.world;
        if (world == null || !world.isBlockLoaded(pos)) {
            return null;
        }
        Object inventory = REFLECTION.findNetworkInventory(world, pos);
        return inventory == null ? null : new Ae2NetworkItemHandler(player, inventory, REFLECTION);
    }

    public static long getReportedCount(IItemHandler handler, int slot, ItemStack fallbackStack) {
        if (handler instanceof ReportedCountItemHandler) {
            return Math.max(0L, ((ReportedCountItemHandler) handler).getReportedCount(slot));
        }
        return fallbackStack == null || fallbackStack.isEmpty() ? 0L : Math.max(0L, fallbackStack.getCount());
    }

    public static void releaseNetworkHandler(IItemHandler handler) {
        if (handler instanceof Ae2NetworkItemHandler) {
            ((Ae2NetworkItemHandler) handler).release();
        }
    }

    public static String resolveGuiBindingIconItemId(World world, BlockPos pos, EnumFacing face, String labelHint) {
        return RtsAe2IconResolver.resolveGuiBindingIconItemId(world, pos, face, labelHint);
    }

    private static final class Ae2NetworkItemHandler implements IItemHandler, ReportedCountItemHandler,
            AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private EntityPlayerMP player;
        private Object inventory;
        private final Ae2Reflection reflection;
        private final List<SlotView> slots = new ArrayList<>();
        private int refreshCounter;
        private boolean snapshotStale;
        private boolean released;

        private Ae2NetworkItemHandler(EntityPlayerMP player, Object inventory, Ae2Reflection reflection) {
            this.player = player;
            this.inventory = inventory;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
            return this.released ? 0 : this.slots.size();
        }

        @Override
        public void ensureFreshSnapshot() {
            if (this.released || this.inventory == null) {
                return;
            }
            if (this.snapshotStale || ++this.refreshCounter >= Config.ae2NetworkRefreshThrottle()) {
                refreshSnapshot();
            }
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (this.released || slot < 0 || slot >= this.slots.size()) {
                return ItemStack.EMPTY;
            }
            SlotView view = this.slots.get(slot);
            return view.amount > 0L ? view.displayStack.copy() : ItemStack.EMPTY;
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
            if (this.released || this.player == null || this.inventory == null) {
                return stack.copy();
            }
            ItemStack remainder = this.reflection.insert(this.inventory, stack, this.player, simulate);
            if (!simulate && remainder.getCount() < stack.getCount()) {
                this.snapshotStale = true;
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (this.released || this.player == null || this.inventory == null
                    || slot < 0 || slot >= this.slots.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            SlotView view = this.slots.get(slot);
            ItemStack extracted = this.reflection.extract(this.inventory, view.key, amount, this.player, simulate);
            if (!simulate && !extracted.isEmpty()) {
                long next = Math.max(0L, view.amount - extracted.getCount());
                this.slots.set(slot, new SlotView(view.key, view.displayStack, next));
                this.snapshotStale = true;
            }
            return extracted;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (this.released || this.player == null || this.inventory == null
                    || targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < this.slots.size(); slot++) {
                SlotView view = this.slots.get(slot);
                if (view.amount <= 0L || view.displayStack.getItem() != targetItem) {
                    continue;
                }
                ItemStack extracted = this.reflection.extract(this.inventory, view.key, amount, this.player, simulate);
                if (!simulate && !extracted.isEmpty()) {
                    long next = Math.max(0L, view.amount - extracted.getCount());
                    this.slots.set(slot, new SlotView(view.key, view.displayStack, next));
                    this.snapshotStale = true;
                }
                return extracted;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !this.released && this.inventory != null && this.reflection.toAeStack(stack) != null;
        }

        @Override
        public long getReportedCount(int slot) {
            return this.released || slot < 0 || slot >= this.slots.size() ? 0L : this.slots.get(slot).amount;
        }

        private void refreshSnapshot() {
            if (this.released || this.inventory == null) {
                return;
            }
            List<SlotView> fresh = this.reflection.snapshot(this.inventory);
            this.slots.clear();
            this.slots.addAll(fresh);
            this.refreshCounter = 0;
            this.snapshotStale = false;
        }

        private void release() {
            if (this.released) {
                return;
            }
            this.released = true;
            this.slots.clear();
            this.player = null;
            this.inventory = null;
        }
    }

    private static final class SlotView {
        private final Object key;
        private final ItemStack displayStack;
        private final long amount;

        private SlotView(Object key, ItemStack displayStack, long amount) {
            this.key = key;
            this.displayStack = displayStack;
            this.amount = amount;
        }
    }

    private static final class Ae2Reflection {
        private final Class<?> gridHostClass;
        private final Class<?> gridNodeClass;
        private final Class<?> partHostClass;
        private final Class<?> aeStackClass;
        private final Object internalLocation;
        private final Method partLocationFromFacing;
        private final Method partHostGetPart;
        private final Method partGetGridNode;
        private final Method gridHostGetGridNode;
        private final Method gridNodeGetGrid;
        private final Method gridGetCache;
        private final Class<?> storageGridClass;
        private final Object itemChannel;
        private final Method storageGridGetInventory;
        private final Method monitorGetStorageList;
        private final Method channelCreateStack;
        private final Method aeStackCopy;
        private final Method aeStackGetSize;
        private final Method aeStackSetSize;
        private final Method aeItemCreateStack;
        private final Method inventoryInject;
        private final Method inventoryExtract;
        private final Object simulateAction;
        private final Object modulateAction;
        private final Constructor<?> playerSourceConstructor;

        private Ae2Reflection(Class<?> gridHostClass, Class<?> gridNodeClass, Class<?> partHostClass,
                Class<?> aeStackClass, Object internalLocation, Method partLocationFromFacing,
                Method partHostGetPart, Method partGetGridNode, Method gridHostGetGridNode,
                Method gridNodeGetGrid, Method gridGetCache, Class<?> storageGridClass,
                Object itemChannel, Method storageGridGetInventory, Method monitorGetStorageList,
                Method channelCreateStack, Method aeStackCopy, Method aeStackGetSize,
                Method aeStackSetSize, Method aeItemCreateStack, Method inventoryInject,
                Method inventoryExtract, Object simulateAction, Object modulateAction,
                Constructor<?> playerSourceConstructor) {
            this.gridHostClass = gridHostClass;
            this.gridNodeClass = gridNodeClass;
            this.partHostClass = partHostClass;
            this.aeStackClass = aeStackClass;
            this.internalLocation = internalLocation;
            this.partLocationFromFacing = partLocationFromFacing;
            this.partHostGetPart = partHostGetPart;
            this.partGetGridNode = partGetGridNode;
            this.gridHostGetGridNode = gridHostGetGridNode;
            this.gridNodeGetGrid = gridNodeGetGrid;
            this.gridGetCache = gridGetCache;
            this.storageGridClass = storageGridClass;
            this.itemChannel = itemChannel;
            this.storageGridGetInventory = storageGridGetInventory;
            this.monitorGetStorageList = monitorGetStorageList;
            this.channelCreateStack = channelCreateStack;
            this.aeStackCopy = aeStackCopy;
            this.aeStackGetSize = aeStackGetSize;
            this.aeStackSetSize = aeStackSetSize;
            this.aeItemCreateStack = aeItemCreateStack;
            this.inventoryInject = inventoryInject;
            this.inventoryExtract = inventoryExtract;
            this.simulateAction = simulateAction;
            this.modulateAction = modulateAction;
            this.playerSourceConstructor = playerSourceConstructor;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static Ae2Reflection tryLoad() {
            try {
                if (!Loader.isModLoaded("appliedenergistics2")) {
                    return null;
                }
            } catch (RuntimeException | LinkageError loaderNotReady) {
                return null;
            }
            try {
                Class<?> aeApiClass = Class.forName("appeng.api.AEApi");
                Object api = aeApiClass.getMethod("instance").invoke(null);
                Class<?> appEngApiClass = Class.forName("appeng.api.IAppEngApi");
                Object storageHelper = appEngApiClass.getMethod("storage").invoke(api);
                Class<?> storageHelperClass = Class.forName("appeng.api.storage.IStorageHelper");
                Class<?> itemChannelClass = Class.forName("appeng.api.storage.channels.IItemStorageChannel");
                Object itemChannel = storageHelperClass.getMethod("getStorageChannel", Class.class)
                        .invoke(storageHelper, itemChannelClass);

                Class<?> gridHostClass = Class.forName("appeng.api.networking.IGridHost");
                Class<?> gridNodeClass = Class.forName("appeng.api.networking.IGridNode");
                Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
                Class<?> storageGridClass = Class.forName("appeng.api.networking.storage.IStorageGrid");
                Class<?> storageChannelClass = Class.forName("appeng.api.storage.IStorageChannel");
                Class<?> monitorClass = Class.forName("appeng.api.storage.IMEMonitor");
                Class<?> inventoryClass = Class.forName("appeng.api.storage.IMEInventory");
                Class<?> aeStackClass = Class.forName("appeng.api.storage.data.IAEStack");
                Class<?> aeItemStackClass = Class.forName("appeng.api.storage.data.IAEItemStack");
                Class<?> actionableClass = Class.forName("appeng.api.config.Actionable");
                Class<?> actionSourceClass = Class.forName("appeng.api.networking.security.IActionSource");
                Class<?> actionHostClass = Class.forName("appeng.api.networking.security.IActionHost");
                Class<?> partLocationClass = Class.forName("appeng.api.util.AEPartLocation");
                Class<?> partHostClass = Class.forName("appeng.api.parts.IPartHost");
                Class<?> partClass = Class.forName("appeng.api.parts.IPart");
                Class<?> playerSourceClass = Class.forName("appeng.me.helpers.PlayerSource");

                Object internalLocation = Enum.valueOf((Class<? extends Enum>) partLocationClass.asSubclass(Enum.class), "INTERNAL");
                Object simulate = Enum.valueOf((Class<? extends Enum>) actionableClass.asSubclass(Enum.class), "SIMULATE");
                Object modulate = Enum.valueOf((Class<? extends Enum>) actionableClass.asSubclass(Enum.class), "MODULATE");

                return new Ae2Reflection(
                        gridHostClass,
                        gridNodeClass,
                        partHostClass,
                        aeStackClass,
                        internalLocation,
                        partLocationClass.getMethod("fromFacing", EnumFacing.class),
                        partHostClass.getMethod("getPart", EnumFacing.class),
                        partClass.getMethod("getGridNode"),
                        gridHostClass.getMethod("getGridNode", partLocationClass),
                        gridNodeClass.getMethod("getGrid"),
                        gridClass.getMethod("getCache", Class.class),
                        storageGridClass,
                        itemChannel,
                        storageGridClass.getMethod("getInventory", storageChannelClass),
                        monitorClass.getMethod("getStorageList"),
                        itemChannelClass.getMethod("createStack", Object.class),
                        aeStackClass.getMethod("copy"),
                        aeStackClass.getMethod("getStackSize"),
                        aeStackClass.getMethod("setStackSize", long.class),
                        aeItemStackClass.getMethod("createItemStack"),
                        inventoryClass.getMethod("injectItems", aeStackClass, actionableClass, actionSourceClass),
                        inventoryClass.getMethod("extractItems", aeStackClass, actionableClass, actionSourceClass),
                        simulate,
                        modulate,
                        playerSourceClass.getConstructor(EntityPlayer.class, actionHostClass));
            } catch (ReflectiveOperationException | LinkageError failure) {
                RtsbuildingMod.LOGGER.warn("AE2 rv6 API 探测失败，已禁用 AE2 网络桥", failure);
                return null;
            }
        }

        private Object findNetworkInventory(World world, BlockPos pos) {
            Object tile = world.getTileEntity(pos);
            if (tile == null) {
                return null;
            }
            List<Object> nodes = new ArrayList<>();
            if (this.partHostClass.isInstance(tile)) {
                for (EnumFacing face : EnumFacing.values()) {
                    Object part = invoke(this.partHostGetPart, tile, face);
                    addNode(nodes, invoke(this.partGetGridNode, part));
                }
            }
            if (this.gridHostClass.isInstance(tile)) {
                for (EnumFacing face : EnumFacing.values()) {
                    Object location = invoke(this.partLocationFromFacing, null, face);
                    addNode(nodes, invoke(this.gridHostGetGridNode, tile, location));
                }
                addNode(nodes, invoke(this.gridHostGetGridNode, tile, this.internalLocation));
            }
            for (Object node : nodes) {
                Object grid = invoke(this.gridNodeGetGrid, node);
                Object storageGrid = invoke(this.gridGetCache, grid, this.storageGridClass);
                if (!this.storageGridClass.isInstance(storageGrid)) {
                    continue;
                }
                Object inventory = invoke(this.storageGridGetInventory, storageGrid, this.itemChannel);
                if (inventory != null) {
                    return inventory;
                }
            }
            return null;
        }

        private void addNode(List<Object> nodes, Object node) {
            if (this.gridNodeClass.isInstance(node) && !nodes.contains(node)) {
                nodes.add(node);
            }
        }

        private List<SlotView> snapshot(Object inventory) {
            List<SlotView> out = new ArrayList<>();
            Object list = invoke(this.monitorGetStorageList, inventory);
            if (!(list instanceof Iterable<?>)) {
                return out;
            }
            Iterator<?> iterator = ((Iterable<?>) list).iterator();
            while (iterator.hasNext()) {
                Object entry = iterator.next();
                if (!this.aeStackClass.isInstance(entry)) {
                    continue;
                }
                long amount = stackSize(entry);
                ItemStack display = toItemStack(entry, 1);
                if (amount > 0L && !display.isEmpty()) {
                    out.add(new SlotView(copyAeStack(entry), display, amount));
                }
            }
            return out;
        }

        private Object toAeStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            Object aeStack = invoke(this.channelCreateStack, this.itemChannel, stack.copy());
            return this.aeStackClass.isInstance(aeStack) ? aeStack : null;
        }

        private ItemStack insert(Object inventory, ItemStack stack, EntityPlayerMP player, boolean simulate) {
            Object input = toAeStack(stack);
            if (input == null) {
                return stack.copy();
            }
            Object source = actionSource(player);
            if (source == null) {
                return stack.copy();
            }
            Object remainder;
            try {
                this.aeStackSetSize.invoke(input, (long) stack.getCount());
                remainder = this.inventoryInject.invoke(inventory, input,
                        simulate ? this.simulateAction : this.modulateAction, source);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException failure) {
                return stack.copy();
            }
            if (remainder == null) {
                return ItemStack.EMPTY;
            }
            long remaining = Math.max(0L, Math.min((long) stack.getCount(), stackSize(remainder)));
            return remaining <= 0L ? ItemStack.EMPTY : toItemStack(remainder, (int) remaining);
        }

        private ItemStack extract(Object inventory, Object key, int amount, EntityPlayerMP player, boolean simulate) {
            if (key == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            Object request = copyAeStack(key);
            if (request == null) {
                return ItemStack.EMPTY;
            }
            Object source = actionSource(player);
            if (source == null) {
                return ItemStack.EMPTY;
            }
            Object extracted;
            try {
                this.aeStackSetSize.invoke(request, (long) amount);
                extracted = this.inventoryExtract.invoke(inventory, request,
                        simulate ? this.simulateAction : this.modulateAction, source);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException failure) {
                return ItemStack.EMPTY;
            }
            long extractedAmount = stackSize(extracted);
            return extractedAmount <= 0L ? ItemStack.EMPTY
                    : toItemStack(extracted, (int) Math.min(Integer.MAX_VALUE, extractedAmount));
        }

        private Object actionSource(EntityPlayerMP player) {
            try {
                return this.playerSourceConstructor.newInstance(player, null);
            } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                return null;
            }
        }

        private Object copyAeStack(Object stack) {
            Object copy = invoke(this.aeStackCopy, stack);
            return this.aeStackClass.isInstance(copy) ? copy : null;
        }

        private long stackSize(Object stack) {
            Object value = invoke(this.aeStackGetSize, stack);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        }

        private ItemStack toItemStack(Object aeStack, int count) {
            if (aeStack == null || count <= 0) {
                return ItemStack.EMPTY;
            }
            Object value = invoke(this.aeItemCreateStack, aeStack);
            ItemStack stack = value instanceof ItemStack ? (ItemStack) value : ItemStack.EMPTY;
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            stack.setCount(count);
            return stack;
        }

        private static Object invoke(Method method, Object target, Object... arguments) {
            if (method == null || (target == null && !Modifier.isStatic(method.getModifiers()))) {
                return null;
            }
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
