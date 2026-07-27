package com.rtsbuilding.addon.sophisticatedbackpacks;

import com.rtsbuilding.rtsbuilding.api.compat.RtsBackpackProvider;
import com.rtsbuilding.rtsbuilding.api.compat.RtsCompatRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import java.util.Optional;
import java.util.UUID;

@Mod("rtsbuilding_addon_sophisticatedbackpacks")
public class RtsSophisticatedBackpacksAddon {

    private static final Logger LOGGER = LoggerFactory.getLogger("RTSBuilding/Backpacks");

    public RtsSophisticatedBackpacksAddon(IEventBus modEventBus, ModContainer modContainer) {
        var modFile = FMLLoader.getLoadingModList().getModFileById("sophisticatedbackpacks");
        if (modFile == null) {
            LOGGER.info("Sophisticated Backpacks not detected — addon will not register");
            return;
        }
        var reflection = new BackpackReflection();
        if (!reflection.loaded) {
            LOGGER.warn("Sophisticated Backpacks found but reflection load failed");
            return;
        }
        RtsCompatRegistry.register(new BackpackProvider(reflection));
        LOGGER.info("Sophisticated Backpacks provider registered");
    }

    private static final class BackpackProvider implements RtsBackpackProvider {
        private final BackpackReflection ref;

        BackpackProvider(BackpackReflection ref) { this.ref = ref; }

        @Override public String getModId() { return "sophisticatedbackpacks"; }

        @Override
        public boolean isBackpackBlockEntity(BlockEntity be) {
            return ref.isBackpackBlockEntity(be);
        }

        @Override
        public Optional<UUID> getBackpackUuid(BlockEntity be) {
            return ref.getBackpackUuid(be);
        }

        @Override
        public Optional<String> getBackpackItemId(BlockEntity be) {
            return ref.getBackpackItemId(be);
        }

        @Override
        public Optional<IItemHandler> openBackpack(UUID uuid, String itemId, ServerPlayer player) {
            return ref.openBackpack(uuid, itemId, player);
        }
    }

    private static final class BackpackReflection {
        boolean loaded = false;

        private Class<?> clBackpackBlockEntity, clBackpackWrapper;
        private MethodHandle mhIsBackpack, mhGetWrapper, mhGetUuid, mhGetStack;
        private MethodHandle mhOpenBackpack, mhOpenExisting;
        private Object backPackItem;

        BackpackReflection() {
            try {
                var lookup = MethodHandles.publicLookup();
                var cl = getClass().getClassLoader();

                clBackpackBlockEntity = Class.forName(
                        "net.sophisticatedbackpacks.common.blockentity.BackpackBlockEntity", false, cl);

                clBackpackWrapper = Class.forName(
                        "net.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper", false, cl);
                var clInventoryHandler = Class.forName(
                        "net.sophisticatedbackpacks.util.InventoryHelper", false, cl);

                // instanceof check
                mhIsBackpack = lookup.findStatic(BackpackReflection.class, "staticIsInstance",
                        MethodType.methodType(boolean.class, Object.class, Class.class));

                // BackpackBlockEntity.getBackpackWrapper()
                mhGetWrapper = lookup.findVirtual(clBackpackBlockEntity, "getBackpackWrapper",
                        MethodType.methodType(clBackpackWrapper));

                // BackpackWrapper.getContentsUuid()
                mhGetUuid = lookup.findVirtual(clBackpackWrapper, "getContentsUuid",
                        MethodType.methodType(UUID.class));

                // BackpackWrapper.getBackpack()
                mhGetStack = lookup.findVirtual(clBackpackWrapper, "getBackpack",
                        MethodType.methodType(ItemStack.class));

                // BackpackWrapper.openBackpack(Player) — returns IItemHandler
                mhOpenBackpack = lookup.findVirtual(clBackpackWrapper, "openBackpack",
                        MethodType.methodType(IItemHandler.class, ServerPlayer.class));

                loaded = true;
            } catch (Exception e) {
                LOGGER.warn("Backpack reflection load failed: {}", e.getMessage());
            }
        }

        @SuppressWarnings("unused")
        static boolean staticIsInstance(Object obj, Class<?> clazz) {
            return clazz.isInstance(obj);
        }

        boolean isBackpackBlockEntity(BlockEntity be) {
            return loaded && clBackpackBlockEntity.isInstance(be);
        }

        Optional<UUID> getBackpackUuid(BlockEntity be) {
            if (!loaded || !clBackpackBlockEntity.isInstance(be)) return Optional.empty();
            try {
                var wrapper = mhGetWrapper.invoke(be);
                if (wrapper == null) return Optional.empty();
                var uuid = (UUID) mhGetUuid.invoke(wrapper);
                return Optional.ofNullable(uuid);
            } catch (Throwable e) {
                return Optional.empty();
            }
        }

        Optional<String> getBackpackItemId(BlockEntity be) {
            if (!loaded || !clBackpackBlockEntity.isInstance(be)) return Optional.empty();
            try {
                var wrapper = mhGetWrapper.invoke(be);
                if (wrapper == null) return Optional.empty();
                var stack = (ItemStack) mhGetStack.invoke(wrapper);
                var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
                return Optional.of(id.toString());
            } catch (Throwable e) {
                return Optional.empty();
            }
        }

        Optional<IItemHandler> openBackpack(UUID uuid, String itemId, ServerPlayer player) {
            if (!loaded) return Optional.empty();
            try {
                var wrapperClass = clBackpackWrapper;
                var openMethod = lookupOpenBackpack(uuid, player);
                if (openMethod != null) return openMethod;
                return findBackpackInInventory(uuid, player);
            } catch (Throwable e) {
                return Optional.empty();
            }
        }

        @Nullable
        private Optional<IItemHandler> lookupOpenBackpack(UUID uuid, ServerPlayer player) throws Throwable {
            try {
                var lookup = MethodHandles.publicLookup();
                var findMethod = lookup.findStatic(
                        Class.forName("net.sophisticatedbackpacks.util.InventoryHelper", false, getClass().getClassLoader()),
                        "openBackpack",
                        MethodType.methodType(Optional.class, UUID.class, ServerPlayer.class));
                @SuppressWarnings("unchecked")
                var result = (Optional<IItemHandler>) findMethod.invoke(uuid, player);
                return result;
            } catch (Exception e) {
                return null;
            }
        }

        private Optional<IItemHandler> findBackpackInInventory(UUID uuid, ServerPlayer player) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                try {
                    var wrapperClass = clBackpackWrapper;
                    var fromStack = MethodHandles.publicLookup().findStatic(wrapperClass, "fromStack",
                            MethodType.methodType(wrapperClass, ItemStack.class));
                    var wrapper = fromStack.invoke(stack);
                    var backpackUuid = (UUID) mhGetUuid.invoke(wrapper);
                    if (uuid.equals(backpackUuid)) {
                        var handler = (IItemHandler) mhOpenBackpack.invoke(wrapper, player);
                        return Optional.of(handler);
                    }
                } catch (Throwable ignored) {
                }
            }
            return Optional.empty();
        }
    }
}
