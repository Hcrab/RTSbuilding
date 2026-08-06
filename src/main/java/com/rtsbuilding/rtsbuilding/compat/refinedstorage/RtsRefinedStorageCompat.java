package com.rtsbuilding.rtsbuilding.compat.refinedstorage;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Refined Storage 1.6.x 的可选网络库存桥。
 *
 * <p>1.12.2 的磁盘驱动普通物品能力只暴露磁盘本身，因此这里从方块实体的网络节点能力取得
 * {@code INetwork}，再把网络库存缓存映射为 RTSBuilding 使用的虚拟 {@link IItemHandler}。
 * 本类只负责 RS 反射与物品视图，不负责链接生命周期和页面缓存。
 */
public final class RtsRefinedStorageCompat {
    private static final RsReflection REFLECTION = RsReflection.tryLoad();

    private RtsRefinedStorageCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static boolean isNetworkNodePosition(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) return false;
        WorldServer world = player.getServerWorld();
        return world != null && world.isBlockLoaded(pos) && REFLECTION.hasNetworkNodeProxy(world, pos);
    }

    public static IItemHandler createNetworkItemHandler(EntityPlayerMP player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) return null;
        WorldServer world = player.getServerWorld();
        if (world == null || !world.isBlockLoaded(pos)) return null;

        RsNetworkRef network = REFLECTION.findNetwork(world, pos);
        if (network == null || network.storageCache() == null) return null;
        if (!REFLECTION.isAllowed(player, network.network(), "MODIFY")) return null;
        return new RsNetworkItemHandler(player, network.network(), network.storageCache(), REFLECTION);
    }

    /**
     * 返回 Refined Storage 网络对象的引用身份，避免批量链接把同一网络的多个终端重复加入。
     */
    public static BatchNetworkProbe probeBatchNetwork(WorldServer world, BlockPos pos) {
        if (world == null || pos == null || REFLECTION == null || !world.isBlockLoaded(pos)) {
            return null;
        }
        RsNetworkRef network = REFLECTION.findNetwork(world, pos);
        return network == null || network.network() == null ? null
                : new BatchNetworkProbe(network.network());
    }

    public static final class BatchNetworkProbe {
        private final Object identity;

        private BatchNetworkProbe(Object identity) {
            this.identity = identity;
        }

        public Object identity() {
            return this.identity;
        }
    }

    private static final class RsNetworkItemHandler implements IItemHandler, ReportedCountItemHandler,
            AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private final EntityPlayerMP player;
        private final Object network;
        private final Object storageCache;
        private final RsReflection reflection;
        private final List<SlotView> slots = new ArrayList<SlotView>();
        private int refreshCounter;
        private boolean snapshotStale;

        private RsNetworkItemHandler(EntityPlayerMP player, Object network, Object storageCache,
                RsReflection reflection) {
            this.player = player;
            this.network = network;
            this.storageCache = storageCache;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
            return this.slots.size();
        }

        /** 昂贵的网络扫描由 {@link RtsHandlerCache} 的刷新周期驱动，而不是由 getSlots() 驱动。 */
        @Override
        public void ensureFreshSnapshot() {
            boolean shouldRefresh = this.snapshotStale;
            if (!shouldRefresh) {
                this.refreshCounter++;
                shouldRefresh = this.refreshCounter >= Config.refinedStorageNetworkRefreshThrottle();
            }
            if (shouldRefresh) refreshSnapshot();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.slots.size()) return ItemStack.EMPTY;
            SlotView view = this.slots.get(slot);
            return view.amount() > 0L ? view.displayStack().copy() : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            if (slot < 0 || slot >= getSlots()) return stack.copy();
            return insertItemAnywhere(stack, simulate);
        }

        @Override
        public ItemStack insertItemAnywhere(ItemStack stack, boolean simulate) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            if (!this.reflection.isAllowed(this.player, this.network, "INSERT")) return stack.copy();

            InsertResult result = this.reflection.insert(this.network, stack, simulate);
            if (!result.succeeded()) return stack.copy();
            if (!simulate && result.inserted() > 0) this.snapshotStale = true;
            return result.remainder();
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.slots.size() || amount <= 0) return ItemStack.EMPTY;
            if (!this.reflection.isAllowed(this.player, this.network, "EXTRACT")) return ItemStack.EMPTY;

            SlotView view = this.slots.get(slot);
            if (view.amount() <= 0L) return ItemStack.EMPTY;
            ItemStack extracted = this.reflection.extract(this.network, view.prototype(), amount, simulate);
            if (extracted.isEmpty()) return ItemStack.EMPTY;
            if (!simulate) {
                long nextAmount = Math.max(0L, view.amount() - extracted.getCount());
                this.slots.set(slot, new SlotView(view.prototype(), nextAmount));
            }
            return extracted;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (targetItem == null || amount <= 0) return ItemStack.EMPTY;
            if (!this.reflection.isAllowed(this.player, this.network, "EXTRACT")) return ItemStack.EMPTY;
            for (int slot = 0; slot < this.slots.size(); slot++) {
                SlotView view = this.slots.get(slot);
                if (view.amount() <= 0L || view.displayStack().getItem() != targetItem) continue;
                ItemStack extracted = this.reflection.extract(this.network, view.prototype(), amount, simulate);
                if (extracted.isEmpty()) return ItemStack.EMPTY;
                if (!simulate) {
                    long nextAmount = Math.max(0L, view.amount() - extracted.getCount());
                    this.slots.set(slot, new SlotView(view.prototype(), nextAmount));
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
            return stack != null && !stack.isEmpty();
        }

        @Override
        public long getReportedCount(int slot) {
            return slot < 0 || slot >= this.slots.size() ? 0L : this.slots.get(slot).amount();
        }

        private void refreshSnapshot() {
            this.slots.clear();
            this.slots.addAll(this.reflection.snapshot(this.storageCache));
            this.refreshCounter = 0;
            this.snapshotStale = false;
        }
    }

    private static final class RsNetworkRef {
        private final Object network;
        private final Object storageCache;

        private RsNetworkRef(Object network, Object storageCache) {
            this.network = network;
            this.storageCache = storageCache;
        }

        private Object network() {
            return this.network;
        }

        private Object storageCache() {
            return this.storageCache;
        }
    }

    private static final class SlotView {
        private final ItemStack prototype;
        private final ItemStack displayStack;
        private final long amount;

        private SlotView(ItemStack prototype, long amount) {
            this.prototype = prototype.copy();
            this.prototype.setCount(1);
            this.displayStack = this.prototype.copy();
            this.amount = amount;
        }

        private ItemStack prototype() {
            return this.prototype;
        }

        private ItemStack displayStack() {
            return this.displayStack;
        }

        private long amount() {
            return this.amount;
        }
    }

    private static final class InsertResult {
        private final boolean succeeded;
        private final int inserted;
        private final ItemStack remainder;

        private InsertResult(boolean succeeded, int inserted, ItemStack remainder) {
            this.succeeded = succeeded;
            this.inserted = inserted;
            this.remainder = remainder;
        }

        private boolean succeeded() {
            return this.succeeded;
        }

        private int inserted() {
            return this.inserted;
        }

        private ItemStack remainder() {
            return this.remainder;
        }
    }

    private static final class InvocationResult {
        private final boolean succeeded;
        private final Object value;

        private InvocationResult(boolean succeeded, Object value) {
            this.succeeded = succeeded;
            this.value = value;
        }
    }

    /** 仅解析 RS 1.6.x 的公开能力/API；缺少模组时不会链接任何 RS 类型。 */
    private static final class RsReflection {
        private final Capability<?> networkNodeProxyCapability;
        private final Method proxyGetNode;
        private final Method nodeGetNetwork;
        private final Method networkGetItemStorageCache;
        private final Method storageCacheGetList;
        private final Method stackListGetStacks;
        private final Method networkInsertItem;
        private final Method networkExtractItem;
        private final Method networkGetSecurityManager;
        private final Method securityHasPermission;
        private final Class<?> permissionClass;
        private final Object actionSimulate;
        private final Object actionPerform;
        private final int compareFlags;

        private RsReflection(Capability<?> networkNodeProxyCapability, Method proxyGetNode, Method nodeGetNetwork,
                Method networkGetItemStorageCache, Method storageCacheGetList, Method stackListGetStacks,
                Method networkInsertItem, Method networkExtractItem, Method networkGetSecurityManager,
                Method securityHasPermission, Class<?> permissionClass, Object actionSimulate, Object actionPerform,
                int compareFlags) {
            this.networkNodeProxyCapability = networkNodeProxyCapability;
            this.proxyGetNode = proxyGetNode;
            this.nodeGetNetwork = nodeGetNetwork;
            this.networkGetItemStorageCache = networkGetItemStorageCache;
            this.storageCacheGetList = storageCacheGetList;
            this.stackListGetStacks = stackListGetStacks;
            this.networkInsertItem = networkInsertItem;
            this.networkExtractItem = networkExtractItem;
            this.networkGetSecurityManager = networkGetSecurityManager;
            this.securityHasPermission = securityHasPermission;
            this.permissionClass = permissionClass;
            this.actionSimulate = actionSimulate;
            this.actionPerform = actionPerform;
            this.compareFlags = compareFlags;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static RsReflection tryLoad() {
            if (!Loader.isModLoaded("refinedstorage")) return null;
            try {
                Class<?> capabilityHolder = Class.forName(
                        "com.raoulvdberge.refinedstorage.capability.CapabilityNetworkNodeProxy");
                Capability<?> capability = (Capability<?>) capabilityHolder
                        .getField("NETWORK_NODE_PROXY_CAPABILITY").get(null);
                if (capability == null) return null;

                Class<?> proxyClass = Class.forName(
                        "com.raoulvdberge.refinedstorage.api.network.node.INetworkNodeProxy");
                Class<?> nodeClass = Class.forName(
                        "com.raoulvdberge.refinedstorage.api.network.node.INetworkNode");
                Class<?> networkClass = Class.forName("com.raoulvdberge.refinedstorage.api.network.INetwork");
                Class<?> storageCacheClass = Class.forName(
                        "com.raoulvdberge.refinedstorage.api.storage.IStorageCache");
                Class<?> stackListClass = Class.forName("com.raoulvdberge.refinedstorage.api.util.IStackList");
                Class<?> actionClass = Class.forName("com.raoulvdberge.refinedstorage.api.util.Action");
                Class<?> comparerClass = Class.forName("com.raoulvdberge.refinedstorage.api.util.IComparer");
                Class<?> securityClass = Class.forName(
                        "com.raoulvdberge.refinedstorage.api.network.security.ISecurityManager");
                Class<?> permissionClass = Class.forName(
                        "com.raoulvdberge.refinedstorage.api.network.security.Permission");

                Method proxyGetNode = proxyClass.getMethod("getNode");
                Method nodeGetNetwork = nodeClass.getMethod("getNetwork");
                Method getCache = networkClass.getMethod("getItemStorageCache");
                Method cacheGetList = storageCacheClass.getMethod("getList");
                Method listGetStacks = stackListClass.getMethod("getStacks");
                Method insertItem = networkClass.getMethod(
                        "insertItem", ItemStack.class, int.class, actionClass);
                Method extractItem = networkClass.getMethod(
                        "extractItem", ItemStack.class, int.class, int.class, actionClass);
                Method getSecurityManager = networkClass.getMethod("getSecurityManager");
                Method hasPermission = securityClass.getMethod(
                        "hasPermission", permissionClass, EntityPlayer.class);
                Object simulate = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "SIMULATE");
                Object perform = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "PERFORM");
                int flags = comparerClass.getField("COMPARE_DAMAGE").getInt(null)
                        | comparerClass.getField("COMPARE_NBT").getInt(null);
                return new RsReflection(capability, proxyGetNode, nodeGetNetwork, getCache, cacheGetList,
                        listGetStacks, insertItem, extractItem, getSecurityManager, hasPermission, permissionClass,
                        simulate, perform, flags);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                return null;
            }
        }

        private RsNetworkRef findNetwork(WorldServer world, BlockPos pos) {
            Object proxy = findProxy(world, pos);
            Object node = invoke(this.proxyGetNode, proxy);
            Object network = invoke(this.nodeGetNetwork, node);
            Object storageCache = invoke(this.networkGetItemStorageCache, network);
            return network == null || storageCache == null ? null : new RsNetworkRef(network, storageCache);
        }

        private boolean hasNetworkNodeProxy(WorldServer world, BlockPos pos) {
            return findProxy(world, pos) != null;
        }

        private Object findProxy(WorldServer world, BlockPos pos) {
            if (world == null || pos == null || this.networkNodeProxyCapability == null) return null;
            TileEntity tile = world.getTileEntity(pos);
            if (tile == null) return null;
            Object proxy = getCapability(tile, null);
            if (proxy != null) return proxy;
            for (EnumFacing facing : EnumFacing.VALUES) {
                proxy = getCapability(tile, facing);
                if (proxy != null) return proxy;
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        private Object getCapability(TileEntity tile, EnumFacing facing) {
            try {
                Capability<Object> capability = (Capability<Object>) this.networkNodeProxyCapability;
                return tile.hasCapability(capability, facing) ? tile.getCapability(capability, facing) : null;
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private List<SlotView> snapshot(Object storageCache) {
            List<SlotView> result = new ArrayList<SlotView>();
            Object stackList = invoke(this.storageCacheGetList, storageCache);
            Object stacks = invoke(this.stackListGetStacks, stackList);
            if (!(stacks instanceof Collection<?>)) return result;
            for (Object value : (Collection<?>) stacks) {
                if (!(value instanceof ItemStack)) continue;
                ItemStack stack = (ItemStack) value;
                if (stack.isEmpty() || stack.getCount() <= 0) continue;
                result.add(new SlotView(stack, stack.getCount()));
            }
            return result;
        }

        private InsertResult insert(Object network, ItemStack stack, boolean simulate) {
            int requested = stack.getCount();
            InvocationResult call = invokeResult(this.networkInsertItem, network, stack, requested,
                    simulate ? this.actionSimulate : this.actionPerform);
            if (!call.succeeded) return new InsertResult(false, 0, stack.copy());
            ItemStack remainder = call.value instanceof ItemStack ? ((ItemStack) call.value).copy() : ItemStack.EMPTY;
            int remainderCount = remainder.isEmpty() ? 0 : Math.max(0, Math.min(requested, remainder.getCount()));
            return new InsertResult(true, requested - remainderCount, remainder);
        }

        private ItemStack extract(Object network, ItemStack prototype, int amount, boolean simulate) {
            InvocationResult call = invokeResult(this.networkExtractItem, network, prototype, amount,
                    this.compareFlags, simulate ? this.actionSimulate : this.actionPerform);
            return call.succeeded && call.value instanceof ItemStack
                    ? ((ItemStack) call.value).copy() : ItemStack.EMPTY;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean isAllowed(EntityPlayerMP player, Object network, String permissionName) {
            if (player == null || network == null) return false;
            Object manager = invoke(this.networkGetSecurityManager, network);
            if (manager == null) return true;
            try {
                Object permission = Enum.valueOf(
                        (Class<? extends Enum>) this.permissionClass.asSubclass(Enum.class), permissionName);
                InvocationResult call = invokeResult(this.securityHasPermission, manager, permission, player);
                return call.succeeded && Boolean.TRUE.equals(call.value);
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private static Object invoke(Method method, Object target, Object... args) {
            InvocationResult result = invokeResult(method, target, args);
            return result.succeeded ? result.value : null;
        }

        private static InvocationResult invokeResult(Method method, Object target, Object... args) {
            if (method == null || (target == null && !Modifier.isStatic(method.getModifiers()))) {
                return new InvocationResult(false, null);
            }
            try {
                return new InvocationResult(true, method.invoke(target, args));
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException exception) {
                RtsbuildingMod.LOGGER.debug("Refined Storage 1.12 reflective call failed", exception);
                return new InvocationResult(false, null);
            }
        }
    }
}
