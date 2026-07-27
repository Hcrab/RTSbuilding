package com.rtsbuilding.addon.refinedstorage;

import com.rtsbuilding.rtsbuilding.api.compat.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

@Mod("rtsbuilding_addon_refinedstorage")
public class RtsRefinedStorageAddon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/RS");

    public RtsRefinedStorageAddon(IEventBus modEventBus, ModContainer modContainer) {
        var modFile = FMLLoader.getLoadingModList().getModFileById("refinedstorage");
        if (modFile == null) {
            LOGGER.info("Refined Storage not detected — addon will not register providers");
            return;
        }
        var reflection = new RsReflection();
        if (!reflection.loaded) {
            LOGGER.warn("Refined Storage found but reflection load failed");
            return;
        }
        RtsCompatRegistry.register(new RsStorageProvider(reflection));
        LOGGER.info("Refined Storage provider registered");
    }

    private static final class RsStorageProvider implements RtsStorageNetworkProvider {
        private final RsReflection ref;

        RsStorageProvider(RsReflection ref) { this.ref = ref; }

        @Override public String getModId() { return "refinedstorage"; }
        @Override public boolean isAvailable() { return ref.loaded; }

        @Override @Nullable
        public IItemHandler createItemHandler(ServerPlayer player, BlockPos pos) {
            if (!ref.loaded) return null;
            try {
                var networkRef = ref.resolveNetwork(player, pos);
                if (networkRef == null) return null;
                return new RsNetworkItemHandler(ref, networkRef);
            } catch (Throwable e) {
                return null;
            }
        }

        @Override
        public void releaseItemHandler(IItemHandler handler) {
            // RS handler doesn't need explicit release
        }

        @Override
        public boolean isNetworkNode(ServerPlayer player, BlockPos pos) {
            return createItemHandler(player, pos) != null;
        }

        @Override @Nullable
        public String getNetworkDisplayName(ServerPlayer player) {
            return "Refined Storage Network";
        }
    }

    private static final class RsNetworkItemHandler
            implements IItemHandler, ReportedCountItemHandler, AnySlotInsertItemHandler, RefreshableSnapshotHandler {

        private static final int REFRESH_THROTTLE = 10;

        private final RsReflection ref;
        private final Object network;
        private final Object storageComponent;
        private List<SlotView> slots = List.of();
        private int tickSinceRefresh = 0;

        RsNetworkItemHandler(RsReflection ref, RsNetworkRef networkRef) {
            this.ref = ref;
            this.network = networkRef.network();
            this.storageComponent = networkRef.storageComponent();
            ensureFreshSnapshot();
        }

        @Override
        public void ensureFreshSnapshot() {
            if (tickSinceRefresh < REFRESH_THROTTLE && !slots.isEmpty()) {
                tickSinceRefresh++;
                return;
            }
            slots = buildSlots();
            tickSinceRefresh = 0;
        }

        @SuppressWarnings("unchecked")
        private List<SlotView> buildSlots() {
            try {
                return ref.snapshotAll(storageComponent);
            } catch (Throwable e) {
                return List.of();
            }
        }

        private boolean hasPermission(int actionFlag) {
            try {
                return ref.hasPermission(network, actionFlag);
            } catch (Throwable e) {
                return true;
            }
        }

        @Override public int getSlots() { ensureFreshSnapshot(); return slots.size(); }
        @Override public ItemStack getStackInSlot(int slot) { ensureFreshSnapshot(); return slot < slots.size() ? slots.get(slot).displayStack() : ItemStack.EMPTY; }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!hasPermission(1)) return stack; // INSERT = 1
            try {
                long inserted = ref.insert(storageComponent, stack, stack.getCount(), simulate);
                if (inserted <= 0) return stack;
                if (!simulate) this.slots = List.of();
                ItemStack remaining = stack.copy();
                remaining.setCount((int) (stack.getCount() - inserted));
                return remaining;
            } catch (Throwable e) {
                return stack;
            }
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!hasPermission(2)) return ItemStack.EMPTY; // EXTRACT = 2
            ensureFreshSnapshot();
            if (slot >= slots.size()) return ItemStack.EMPTY;
            var view = slots.get(slot);
            try {
                long extracted = ref.extract(storageComponent, view.resource(), amount, simulate);
                if (extracted <= 0) return ItemStack.EMPTY;
                if (!simulate) this.slots = List.of();
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
            return slot < slots.size() ? slots.get(slot).amount() : 0;
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }

    private record RsNetworkRef(Object network, Object storageComponent) {}
    private record SlotView(Object resource, ItemStack displayStack, long amount) {}

    private static final class RsReflection {
        boolean loaded = false;

        private Class<?> clNetworkManager, clNetwork, clStorageComponent, clStorageView, clResource, clPlayerActor;
        private MethodHandle mhGetNetworkFromPos, mhGetStorageComponent, mhGetAll, mhGetResource, mhGetAmount;
        private MethodHandle mhInsert, mhExtract, mhHasPermission;

        RsReflection() {
            try {
                var lookup = MethodHandles.publicLookup();
                var cl = getClass().getClassLoader();
                var cls = "com.refinedstorage2.core.networking";

                clNetworkManager = Class.forName("com.refinedstorage2.api.network.NetworkManager", false, cl);
                clNetwork = Class.forName("com.refinedstorage2.api.network.Network", false, cl);
                clStorageComponent = Class.forName("com.refinedstorage2.api.storage.StorageComponent", false, cl);
                clStorageView = Class.forName("com.refinedstorage2.api.storage.StorageView", false, cl);
                clResource = Class.forName("com.refinedstorage2.api.resource.ResourceAmount", false, cl);
                clPlayerActor = Class.forName("com.refinedstorage2.api.network.security.PlayerActor", false, cl);

                var clSecurityActor = Class.forName("com.refinedstorage2.api.network.security.SecurityActor", false, cl);
                var clSecurityManager = Class.forName("com.refinedstorage2.api.network.security.SecurityManager", false, cl);
                var clNetworkBuilder = Class.forName("com.refinedstorage2.api.network.NetworkBuilder", false, cl);

                // NetworkManager.getInstance().getNetwork(pos)
                var mhGetInstance = lookup.findStatic(clNetworkManager, "getInstance",
                        MethodType.methodType(clNetworkManager));
                var instance = mhGetInstance.invoke();
                mhGetNetworkFromPos = lookup.findVirtual(clNetworkManager, "getNetwork",
                        MethodType.methodType(Optional.class, BlockPos.class))
                        .bindTo(instance);

                // Network.getComponent(Class)
                mhGetStorageComponent = lookup.findVirtual(clNetwork, "getComponent",
                        MethodType.methodType(Optional.class, Class.class));

                // StorageComponent.getAll()
                mhGetAll = lookup.findVirtual(clStorageComponent, "getAll",
                        MethodType.methodType(Collection.class));

                // StorageView methods
                mhGetResource = lookup.findVirtual(clStorageView, "getResource",
                        MethodType.methodType(clResource));

                // ResourceAmount methods
                var mhGetResourceAmountResource = lookup.findVirtual(clResource, "getResource",
                        MethodType.methodType(Object.class));
                var mhGetResourceAmountAmount = lookup.findVirtual(clResource, "getAmount",
                        MethodType.methodType(long.class));

                // StorageComponent.insert/extract
                mhInsert = lookup.findVirtual(clStorageComponent, "insert",
                        MethodType.methodType(long.class, Object.class, long.class, Object.class, boolean.class));
                mhExtract = lookup.findVirtual(clStorageComponent, "extract",
                        MethodType.methodType(long.class, Object.class, long.class, Object.class, boolean.class));

                // SecurityManager.hasPermission
                mhHasPermission = lookup.findVirtual(clSecurityManager, "hasPermission",
                        MethodType.methodType(boolean.class, clSecurityActor, int.class));

                loaded = true;
            } catch (Throwable e) {
                LOGGER.warn("RS2 reflection load failed: {}", e.getMessage());
            }
        }

        @Nullable
        RsNetworkRef resolveNetwork(ServerPlayer player, BlockPos pos) throws Throwable {
            var optNetwork = (Optional<?>) mhGetNetworkFromPos.invoke(pos);
            if (optNetwork == null || optNetwork.isEmpty()) return null;
            var network = optNetwork.get();

            var optComponent = (Optional<?>) mhGetStorageComponent.invoke(network,
                    Class.forName("com.refinedstorage2.api.storage.StorageComponent", false, getClass().getClassLoader()));
            if (optComponent == null || optComponent.isEmpty()) return null;
            var storageComponent = optComponent.get();

            return new RsNetworkRef(network, storageComponent);
        }

        @SuppressWarnings("unchecked")
        List<SlotView> snapshotAll(Object storageComponent) throws Throwable {
            List<SlotView> result = new ArrayList<>();
            var all = (Collection<?>) mhGetAll.invoke(storageComponent);
            if (all == null) return result;
            for (var view : all) {
                var resourceAmount = mhGetResource.invoke(view);
                var resource = mhGetResource.invoke(resourceAmount);
                long amount = (long) mhGetAmount.invoke(resourceAmount);
                ItemStack display = toItemStack(resource);
                if (display != null) {
                    result.add(new SlotView(resource, display, amount));
                }
            }
            return result;
        }

        @Nullable
        private ItemStack toItemStack(Object resource) {
            if (resource instanceof ItemStack stack) return stack.copy();
            try {
                var stackClass = Class.forName("net.minecraft.world.item.ItemStack", false, getClass().getClassLoader());
                if (stackClass.isInstance(resource)) {
                    return ((ItemStack) resource).copy();
                }
            } catch (Exception ignored) {}
            return null;
        }

        long insert(Object storageComponent, ItemStack stack, long amount, boolean simulate) throws Throwable {
            return (long) mhInsert.invoke(storageComponent, stack, amount, null, simulate);
        }

        long extract(Object storageComponent, Object resource, long amount, boolean simulate) throws Throwable {
            return (long) mhExtract.invoke(storageComponent, resource, amount, null, simulate);
        }

        boolean hasPermission(Object network, int actionFlag) throws Throwable {
            return false;
        }
    }
}
