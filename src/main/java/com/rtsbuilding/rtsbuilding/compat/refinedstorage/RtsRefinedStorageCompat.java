package com.rtsbuilding.rtsbuilding.compat.refinedstorage;

import com.rtsbuilding.rtsbuilding.platform.RtsItemStacks;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.NetworkSnapshotRefreshGate;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

/**
 * Refined Storage 的反射兼容桥。
 *
 * <p>这个类不硬依赖 RS jar。运行环境有 RS 时，它从公开的 network node
 * 入口拿到 {@code INetwork}，再把网络的 item storage cache 包成 RTS 可用的
 * 虚拟 {@link IItemHandler}。它不直接读写磁盘 NBT，也不假造储存内容。</p>
 */
public final class RtsRefinedStorageCompat {
    private static final RsReflection REFLECTION = RsReflection.tryLoad();

    private RtsRefinedStorageCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player, BlockPos pos) {
        return player == null ? null : createNetworkItemHandler(player, player.getLevel(), pos);
    }

    /**
     * 在指定维度解析 Refined Storage 网络端点。调用方负责链接权限与区块唤醒。
     */
    public static IItemHandler createNetworkItemHandler(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!isAvailable() || player == null || level == null || pos == null) {
            return null;
        }
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        Object network = REFLECTION.findNetwork(level, pos);
        if (network == null) {
            return null;
        }
        return new RsNetworkItemHandler(network, REFLECTION);
    }

    /**
     * 返回端点所属 RS 网络的瞬态身份，仅供一次已加载区块扫描按引用去重。
     * 调用方不得持久化或发送这个第三方运行时对象。
     */
    public static Object batchNetworkIdentity(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || REFLECTION == null || !level.hasChunkAt(pos)) {
            return null;
        }
        return REFLECTION.findNetwork(level, pos);
    }

    private static final class RsNetworkItemHandler implements IItemHandler,
            ReportedCountItemHandler, AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private final Object network;
        private final RsReflection reflection;
        private final List<ItemStack> stacks = new ArrayList<>();
        private final NetworkSnapshotRefreshGate refreshGate = new NetworkSnapshotRefreshGate();

        private RsNetworkItemHandler(Object network, RsReflection reflection) {
            this.network = network;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
            return this.stacks.size();
        }

        @Override
        public void ensureFreshSnapshot() {
            if (this.refreshGate.shouldRefresh(Config.refinedStorageNetworkRefreshThrottle())) {
                refreshSnapshot();
            }
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= this.stacks.size()) {
                return ItemStack.EMPTY;
            }
            return this.stacks.get(slot).copy();
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
            ItemStack remainder = this.reflection.insertItem(this.network, stack, simulate);
            if (!simulate) {
                this.refreshGate.markStale();
            }
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= this.stacks.size() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack prototype = RtsItemStacks.copyWithCount(this.stacks.get(slot), 1);
            ItemStack extracted = this.reflection.extractItem(this.network, prototype, amount, simulate);
            if (!simulate) {
                updateCachedAmount(slot, extracted.getCount());
            }
            return extracted;
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            for (int slot = 0; slot < this.stacks.size(); slot++) {
                ItemStack stack = this.stacks.get(slot);
                if (stack.isEmpty() || stack.getItem() != targetItem) {
                    continue;
                }
                ItemStack extracted = this.reflection.extractItem(
                        this.network,
                        RtsItemStacks.copyWithCount(stack, 1),
                        amount,
                        simulate);
                if (!simulate) {
                    updateCachedAmount(slot, extracted.getCount());
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
            if (slot < 0 || slot >= this.stacks.size()) {
                return 0L;
            }
            return Math.max(0, this.stacks.get(slot).getCount());
        }

        private void refreshSnapshot() {
            this.stacks.clear();
            this.stacks.addAll(this.reflection.snapshot(this.network));
            this.refreshGate.markRefreshed();
        }

        private void updateCachedAmount(int slot, int extracted) {
            if (slot < 0 || slot >= this.stacks.size() || extracted <= 0) {
                return;
            }
            ItemStack cached = this.stacks.get(slot).copy();
            cached.shrink(extracted);
            this.stacks.set(slot, cached);
        }
    }

    private static final class RsReflection {
        private final Method blockEntityGetNode;
        private final Method nodeGetNetwork;
        private final Method networkCanRun;
        private final Method networkGetItemStorageCache;
        private final Method networkInsertItem;
        private final Method networkExtractItem;
        private final Object actionPerform;
        private final Object actionSimulate;
        private final Method storageCacheGetList;
        private final Method stackListGetStacks;
        private final Method stackEntryGetStack;

        private RsReflection(Method blockEntityGetNode, Method nodeGetNetwork, Method networkCanRun,
                Method networkGetItemStorageCache, Method networkInsertItem, Method networkExtractItem,
                Object actionPerform, Object actionSimulate, Method storageCacheGetList,
                Method stackListGetStacks, Method stackEntryGetStack) {
            this.blockEntityGetNode = blockEntityGetNode;
            this.nodeGetNetwork = nodeGetNetwork;
            this.networkCanRun = networkCanRun;
            this.networkGetItemStorageCache = networkGetItemStorageCache;
            this.networkInsertItem = networkInsertItem;
            this.networkExtractItem = networkExtractItem;
            this.actionPerform = actionPerform;
            this.actionSimulate = actionSimulate;
            this.storageCacheGetList = storageCacheGetList;
            this.stackListGetStacks = stackListGetStacks;
            this.stackEntryGetStack = stackEntryGetStack;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static RsReflection tryLoad() {
            if (!ModList.get().isLoaded("refinedstorage")) {
                return null;
            }
            try {
                Class<?> nodeBlockEntityClass = Class.forName(
                        "com.refinedmods.refinedstorage.blockentity.NetworkNodeBlockEntity");
                Method blockEntityGetNode = nodeBlockEntityClass.getMethod("getNode");

                Class<?> networkNodeClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.node.INetworkNode");
                Method nodeGetNetwork = networkNodeClass.getMethod("getNetwork");

                Class<?> networkClass = Class.forName("com.refinedmods.refinedstorage.api.network.INetwork");
                Method networkCanRun = networkClass.getMethod("canRun");
                Method networkGetItemStorageCache = networkClass.getMethod("getItemStorageCache");

                Class<?> actionClass = Class.forName("com.refinedmods.refinedstorage.api.util.Action");
                Object actionPerform = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class),
                        "PERFORM");
                Object actionSimulate = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class),
                        "SIMULATE");
                Method networkInsertItem = networkClass.getMethod("insertItem",
                        ItemStack.class, int.class, actionClass);
                Method networkExtractItem = networkClass.getMethod("extractItem",
                        ItemStack.class, int.class, actionClass);

                Class<?> storageCacheClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.storage.cache.IStorageCache");
                Method storageCacheGetList = storageCacheClass.getMethod("getList");
                Class<?> stackListClass = Class.forName("com.refinedmods.refinedstorage.api.util.IStackList");
                Method stackListGetStacks = stackListClass.getMethod("getStacks");
                Class<?> stackEntryClass = Class.forName("com.refinedmods.refinedstorage.api.util.StackListEntry");
                Method stackEntryGetStack = stackEntryClass.getMethod("getStack");

                return new RsReflection(blockEntityGetNode, nodeGetNetwork, networkCanRun,
                        networkGetItemStorageCache, networkInsertItem, networkExtractItem,
                        actionPerform, actionSimulate, storageCacheGetList, stackListGetStacks,
                        stackEntryGetStack);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private Object findNetwork(ServerLevel level, BlockPos pos) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null || !this.blockEntityGetNode.getDeclaringClass().isInstance(blockEntity)) {
                return null;
            }
            Object node = invoke(this.blockEntityGetNode, blockEntity);
            if (node == null) {
                return null;
            }
            Object network = invoke(this.nodeGetNetwork, node);
            if (network == null) {
                return null;
            }
            Object canRun = invoke(this.networkCanRun, network);
            return Boolean.FALSE.equals(canRun) ? null : network;
        }

        private List<ItemStack> snapshot(Object network) {
            Object cache = invoke(this.networkGetItemStorageCache, network);
            Object list = invoke(this.storageCacheGetList, cache);
            Object entries = invoke(this.stackListGetStacks, list);
            if (!(entries instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<ItemStack> out = new ArrayList<>();
            for (Object entry : iterable) {
                Object rawStack = invoke(this.stackEntryGetStack, entry);
                if (!(rawStack instanceof ItemStack stack) || stack.isEmpty()) {
                    continue;
                }
                out.add(stack.copy());
            }
            return out;
        }

        private ItemStack insertItem(Object network, ItemStack stack, boolean simulate) {
            Object result = invoke(this.networkInsertItem, network, stack.copy(), stack.getCount(),
                    simulate ? this.actionSimulate : this.actionPerform);
            return result instanceof ItemStack itemStack ? itemStack : stack.copy();
        }

        private ItemStack extractItem(Object network, ItemStack prototype, int amount, boolean simulate) {
            Object result = invoke(this.networkExtractItem, network, RtsItemStacks.copyWithCount(prototype, 1), amount,
                    simulate ? this.actionSimulate : this.actionPerform);
            return result instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        }

        private static Object invoke(Method method, Object target, Object... args) {
            if (method == null || target == null && !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
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
