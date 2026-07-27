package com.rtsbuilding.rtsbuilding.compat.refinedstorage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.compat.AnySlotInsertItemHandler;
import com.rtsbuilding.rtsbuilding.compat.RefreshableSnapshotHandler;
import com.rtsbuilding.rtsbuilding.compat.ReportedCountItemHandler;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsHandlerCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.items.IItemHandler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Optional Refined Storage 2 integration for linked-storage binding.
 *
 * <p>RS disk drives expose their disk/card inventory through the normal
 * NeoForge item-handler capability. Binding that handler makes RTSBuilding see
 * only the storage media, not the network contents. This class deliberately
 * resolves RS's network-node capability first and exposes the network's item
 * resources as the same virtual {@link IItemHandler} shape used by AE2.
 *
 * <p>This class owns only RS reflection and the virtual item view. It does not
 * decide whether a linked row is extract-only, where cache updates are mounted,
 * or how RTS UI pages are built; those responsibilities stay in the storage
 * resolver/cache layers.
 */
public final class RtsRefinedStorageCompat {
    private static final RsReflection REFLECTION = RsReflection.tryLoad();

    private RtsRefinedStorageCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static boolean isNetworkNodePosition(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        return level != null && level.hasChunkAt(pos) && REFLECTION.hasNetworkNodeProvider(level, pos);
    }

    public static IItemHandler createNetworkItemHandler(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || REFLECTION == null) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        if (level == null || !level.hasChunkAt(pos)) {
            return null;
        }

        RsNetworkRef network = REFLECTION.findNetwork(level, pos);
        if (network == null || network.storageComponent() == null) {
            return null;
        }
        if (!REFLECTION.isAllowed(player, network.network(), "OPEN")) {
            return null;
        }
        return new RsNetworkItemHandler(player, network.network(), network.storageComponent(), REFLECTION);
    }

    private static final class RsNetworkItemHandler implements IItemHandler, ReportedCountItemHandler,
            AnySlotInsertItemHandler, RefreshableSnapshotHandler {
        private final ServerPlayer player;
        private final Object network;
        private final Object storageComponent;
        private final RsReflection reflection;
        private final List<SlotView> slots = new ArrayList<>();

        /**
         * Refreshed through {@link RtsHandlerCache}, not from getSlots().
         * With the current cache tick rates this avoids repeatedly walking a
         * large RS network during normal mining/building input.
         */
        private static final int REFRESH_THROTTLE = 10;
        private int refreshCounter = 0;
        private boolean snapshotStale;

        private RsNetworkItemHandler(ServerPlayer player, Object network, Object storageComponent,
                RsReflection reflection) {
            this.player = player;
            this.network = network;
            this.storageComponent = storageComponent;
            this.reflection = reflection;
            refreshSnapshot();
        }

        @Override
        public int getSlots() {
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
                refreshSnapshot();
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
            if (!this.reflection.isAllowed(this.player, this.network, "INSERT")) {
                return stack.copy();
            }
            Object resource = this.reflection.toItemResource(stack);
            if (resource == null) {
                return stack.copy();
            }

            long inserted = this.reflection.insert(this.storageComponent, resource, stack.getCount(), this.player,
                    simulate);
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
            if (!this.reflection.isAllowed(this.player, this.network, "EXTRACT")) {
                return ItemStack.EMPTY;
            }

            SlotView view = this.slots.get(slot);
            if (view.amount() <= 0L) {
                return ItemStack.EMPTY;
            }

            long extracted = this.reflection.extract(this.storageComponent, view.resource(), amount, this.player,
                    simulate);
            if (extracted <= 0L) {
                return ItemStack.EMPTY;
            }

            if (!simulate) {
                long nextAmount = Math.max(0L, view.amount() - extracted);
                this.slots.set(slot, new SlotView(view.resource(), view.displayStack(), nextAmount));
            }
            return this.reflection.toStack(view.resource(), extracted);
        }

        @Override
        public ItemStack extractItemAnywhere(Item targetItem, int amount, boolean simulate) {
            if (targetItem == null || amount <= 0) {
                return ItemStack.EMPTY;
            }
            if (!this.reflection.isAllowed(this.player, this.network, "EXTRACT")) {
                return ItemStack.EMPTY;
            }

            for (int i = 0; i < this.slots.size(); i++) {
                SlotView view = this.slots.get(i);
                if (view.amount() <= 0L || view.displayStack().getItem() != targetItem) {
                    continue;
                }
                long extracted = this.reflection.extract(this.storageComponent, view.resource(), amount, this.player,
                        simulate);
                if (extracted <= 0L) {
                    return ItemStack.EMPTY;
                }
                if (!simulate) {
                    long nextAmount = Math.max(0L, view.amount() - extracted);
                    this.slots.set(i, new SlotView(view.resource(), view.displayStack(), nextAmount));
                }
                return this.reflection.toStack(view.resource(), extracted);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return this.reflection.toItemResource(stack) != null;
        }

        @Override
        public long getReportedCount(int slot) {
            if (slot < 0 || slot >= this.slots.size()) {
                return 0L;
            }
            return this.slots.get(slot).amount();
        }

        private void refreshSnapshot() {
            this.slots.clear();
            for (SlotView slot : this.reflection.snapshot(this.storageComponent)) {
                if (slot != null && slot.amount() > 0L && !slot.displayStack().isEmpty()) {
                    this.slots.add(slot);
                }
            }
            this.refreshCounter = 0;
            this.snapshotStale = false;
        }
    }

    private record RsNetworkRef(Object network, Object storageComponent) {
    }

    private record SlotView(Object resource, ItemStack displayStack, long amount) {
    }

    private static final class RsReflection {
        private final BlockCapability<?, ?> networkNodeContainerProviderCapability;
        private final Method providerGetContainers;
        private final Method containerGetNode;
        private final Method nodeGetNetwork;
        private final Class<?> storageNetworkComponentClass;
        private final Method networkGetComponent;
        private final Method storageGetAll;
        private final Method storageInsert;
        private final Method storageExtract;
        private final Class<?> itemResourceClass;
        private final Method itemResourceOfItemStack;
        private final Method itemResourceToItemStack;
        private final Method resourceAmountResource;
        private final Method resourceAmountAmount;
        private final Object actionSimulate;
        private final Object actionExecute;
        private final Field actorEmptyField;
        private final Constructor<?> playerActorConstructor;
        private final Method securityIsAllowed;
        private final Class<?> builtinPermissionClass;

        private RsReflection(
                BlockCapability<?, ?> networkNodeContainerProviderCapability,
                Method providerGetContainers,
                Method containerGetNode,
                Method nodeGetNetwork,
                Class<?> storageNetworkComponentClass,
                Method networkGetComponent,
                Method storageGetAll,
                Method storageInsert,
                Method storageExtract,
                Class<?> itemResourceClass,
                Method itemResourceOfItemStack,
                Method itemResourceToItemStack,
                Method resourceAmountResource,
                Method resourceAmountAmount,
                Object actionSimulate,
                Object actionExecute,
                Field actorEmptyField,
                Constructor<?> playerActorConstructor,
                Method securityIsAllowed,
                Class<?> builtinPermissionClass) {
            this.networkNodeContainerProviderCapability = networkNodeContainerProviderCapability;
            this.providerGetContainers = providerGetContainers;
            this.containerGetNode = containerGetNode;
            this.nodeGetNetwork = nodeGetNetwork;
            this.storageNetworkComponentClass = storageNetworkComponentClass;
            this.networkGetComponent = networkGetComponent;
            this.storageGetAll = storageGetAll;
            this.storageInsert = storageInsert;
            this.storageExtract = storageExtract;
            this.itemResourceClass = itemResourceClass;
            this.itemResourceOfItemStack = itemResourceOfItemStack;
            this.itemResourceToItemStack = itemResourceToItemStack;
            this.resourceAmountResource = resourceAmountResource;
            this.resourceAmountAmount = resourceAmountAmount;
            this.actionSimulate = actionSimulate;
            this.actionExecute = actionExecute;
            this.actorEmptyField = actorEmptyField;
            this.playerActorConstructor = playerActorConstructor;
            this.securityIsAllowed = securityIsAllowed;
            this.builtinPermissionClass = builtinPermissionClass;
        }

        private static RsReflection tryLoad() {
            if (!ModList.get().isLoaded("refinedstorage")) {
                return null;
            }

            try {
                Class<?> apiClass = Class.forName("com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi");
                Field instanceField = apiClass.getField("INSTANCE");
                Object api = instanceField.get(null);
                Method getNetworkCapability = apiClass.getMethod("getNetworkNodeContainerProviderCapability");
                BlockCapability<?, ?> networkCapability = (BlockCapability<?, ?>) getNetworkCapability.invoke(api);

                Class<?> providerClass = Class.forName(
                        "com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider");
                Method providerGetContainers = providerClass.getMethod("getContainers");

                Class<?> containerClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.node.container.NetworkNodeContainer");
                Method containerGetNode = containerClass.getMethod("getNode");

                Class<?> nodeClass = Class.forName("com.refinedmods.refinedstorage.api.network.node.NetworkNode");
                Method nodeGetNetwork = nodeClass.getMethod("getNetwork");

                Class<?> networkClass = Class.forName("com.refinedmods.refinedstorage.api.network.Network");
                Method networkGetComponent = networkClass.getMethod("getComponent", Class.class);

                Class<?> storageNetworkComponentClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent");
                Class<?> storageViewClass = Class.forName("com.refinedmods.refinedstorage.api.storage.StorageView");
                Method storageGetAll = storageViewClass.getMethod("getAll");

                Class<?> resourceKeyClass = Class.forName("com.refinedmods.refinedstorage.api.resource.ResourceKey");
                Class<?> actionClass = Class.forName("com.refinedmods.refinedstorage.api.core.Action");
                Class<?> actorClass = Class.forName("com.refinedmods.refinedstorage.api.storage.Actor");
                Class<?> insertableStorageClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.storage.InsertableStorage");
                Class<?> extractableStorageClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.storage.ExtractableStorage");
                Method storageInsert = insertableStorageClass.getMethod("insert",
                        resourceKeyClass, long.class, actionClass, actorClass);
                Method storageExtract = extractableStorageClass.getMethod("extract",
                        resourceKeyClass, long.class, actionClass, actorClass);

                Object actionSimulate = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class),
                        "SIMULATE");
                Object actionExecute = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class),
                        "EXECUTE");

                Field actorEmptyField = actorClass.getField("EMPTY");
                Class<?> playerActorClass = Class.forName(
                        "com.refinedmods.refinedstorage.common.api.storage.PlayerActor");
                Constructor<?> playerActorConstructor = playerActorClass.getConstructor(Player.class);

                Class<?> itemResourceClass = Class.forName(
                        "com.refinedmods.refinedstorage.common.support.resource.ItemResource");
                Method itemResourceOfItemStack = itemResourceClass.getMethod("ofItemStack", ItemStack.class);
                Method itemResourceToItemStack = itemResourceClass.getMethod("toItemStack", long.class);

                Class<?> resourceAmountClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.resource.ResourceAmount");
                Method resourceAmountResource = resourceAmountClass.getMethod("resource");
                Method resourceAmountAmount = resourceAmountClass.getMethod("amount");

                Class<?> securityHelperClass = Class.forName(
                        "com.refinedmods.refinedstorage.common.api.security.SecurityHelper");
                Class<?> permissionClass = Class.forName(
                        "com.refinedmods.refinedstorage.api.network.security.Permission");
                Method securityIsAllowed = securityHelperClass.getMethod("isAllowed",
                        ServerPlayer.class, permissionClass, networkClass);
                Class<?> builtinPermissionClass = Class.forName(
                        "com.refinedmods.refinedstorage.common.security.BuiltinPermission");

                return new RsReflection(
                        networkCapability,
                        providerGetContainers,
                        containerGetNode,
                        nodeGetNetwork,
                        storageNetworkComponentClass,
                        networkGetComponent,
                        storageGetAll,
                        storageInsert,
                        storageExtract,
                        itemResourceClass,
                        itemResourceOfItemStack,
                        itemResourceToItemStack,
                        resourceAmountResource,
                        resourceAmountAmount,
                        actionSimulate,
                        actionExecute,
                        actorEmptyField,
                        playerActorConstructor,
                        securityIsAllowed,
                        builtinPermissionClass);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }

        private RsNetworkRef findNetwork(ServerLevel level, BlockPos pos) {
            Object provider = findProvider(level, pos);
            if (provider == null) {
                return null;
            }

            Object containersObject = invoke(this.providerGetContainers, provider);
            if (!(containersObject instanceof Set<?> containers)) {
                return null;
            }
            for (Object container : containers) {
                Object node = invoke(this.containerGetNode, container);
                Object network = invoke(this.nodeGetNetwork, node);
                Object storageComponent = resolveStorageComponent(network);
                if (storageComponent != null) {
                    return new RsNetworkRef(network, storageComponent);
                }
            }
            return null;
        }

        private boolean hasNetworkNodeProvider(ServerLevel level, BlockPos pos) {
            return findProvider(level, pos) != null;
        }

        private Object findProvider(ServerLevel level, BlockPos pos) {
            if (level == null || pos == null) {
                return null;
            }
            return level.getCapability(
                    (BlockCapability<Object, Direction>) this.networkNodeContainerProviderCapability, pos, null);
        }

        private Object resolveStorageComponent(Object network) {
            if (network == null) {
                return null;
            }
            Object component = invoke(this.networkGetComponent, network, this.storageNetworkComponentClass);
            return this.storageNetworkComponentClass.isInstance(component) ? component : null;
        }

        private List<SlotView> snapshot(Object storageComponent) {
            List<SlotView> out = new ArrayList<>();
            Object all = invoke(this.storageGetAll, storageComponent);
            if (!(all instanceof Collection<?> resources)) {
                return out;
            }
            for (Object resourceAmount : resources) {
                Object resource = invoke(this.resourceAmountResource, resourceAmount);
                if (resource == null || !this.itemResourceClass.isInstance(resource)) {
                    continue;
                }
                long amount = asLong(invoke(this.resourceAmountAmount, resourceAmount));
                if (amount <= 0L) {
                    continue;
                }
                ItemStack display = toStack(resource, 1);
                if (display.isEmpty()) {
                    continue;
                }
                display.setCount(1);
                out.add(new SlotView(resource, display, amount));
            }
            return out;
        }

        private Object toItemResource(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            Object resource = invoke(this.itemResourceOfItemStack, null, stack);
            return this.itemResourceClass.isInstance(resource) ? resource : null;
        }

        private ItemStack toStack(Object resource, long count) {
            if (resource == null || !this.itemResourceClass.isInstance(resource) || count <= 0L) {
                return ItemStack.EMPTY;
            }
            long boundedCount = Math.min(Integer.MAX_VALUE, count);
            Object stack = invoke(this.itemResourceToItemStack, resource, boundedCount);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        }

        private long insert(Object storageComponent, Object resource, long amount, ServerPlayer player,
                boolean simulate) {
            if (storageComponent == null || resource == null || amount <= 0L) {
                return 0L;
            }
            return asLong(invoke(this.storageInsert, storageComponent, resource, amount,
                    simulate ? this.actionSimulate : this.actionExecute, actorFor(player)));
        }

        private long extract(Object storageComponent, Object resource, long amount, ServerPlayer player,
                boolean simulate) {
            if (storageComponent == null || resource == null || amount <= 0L) {
                return 0L;
            }
            return asLong(invoke(this.storageExtract, storageComponent, resource, amount,
                    simulate ? this.actionSimulate : this.actionExecute, actorFor(player)));
        }

        private boolean isAllowed(ServerPlayer player, Object network, String permissionName) {
            if (player == null || network == null || this.securityIsAllowed == null
                    || this.builtinPermissionClass == null) {
                return true;
            }
            Object permission;
            try {
                permission = Enum.valueOf((Class<? extends Enum>) this.builtinPermissionClass.asSubclass(Enum.class),
                        permissionName);
            } catch (IllegalArgumentException ex) {
                return true;
            }
            Object allowed = invoke(this.securityIsAllowed, null, player, permission, network);
            return !(allowed instanceof Boolean value) || value;
        }

        private Object actorFor(ServerPlayer player) {
            if (player != null && this.playerActorConstructor != null) {
                try {
                    return this.playerActorConstructor.newInstance(player);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            try {
                return this.actorEmptyField.get(null);
            } catch (IllegalAccessException ignored) {
                return null;
            }
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
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
                RtsbuildingMod.LOGGER.debug("Refined Storage reflective call failed", e);
                return null;
            }
        }
    }
}
