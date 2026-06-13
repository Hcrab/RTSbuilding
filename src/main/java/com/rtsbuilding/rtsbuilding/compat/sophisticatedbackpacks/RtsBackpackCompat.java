package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;
import net.minecraftforge.items.IItemHandler;

/**
 * Optional Sophisticated Backpacks bridge for RTS linked storage.
 *
 * <p>Backpack contents are keyed by Sophisticated Backpacks' storage UUID, not
 * by the world position of a placed backpack block. This compat class reads
 * that UUID from placed backpack block entities and can reopen the matching
 * inventory through a virtual backpack stack when the original block position
 * is no longer present. All access is reflective so RTSBuilding does not gain a
 * hard runtime dependency on Sophisticated Backpacks.
 */
public final class RtsBackpackCompat {
    private static final String MOD_ID = "sophisticatedbackpacks";
    private static final BackpackReflection REFLECTION = BackpackReflection.tryLoad();

    private RtsBackpackCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static boolean isBackpackBlockEntity(BlockEntity blockEntity) {
        return isAvailable() && REFLECTION.isBackpackBlockEntity(blockEntity);
    }

    public static Optional<UUID> getBackpackUuid(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackUuid(blockEntity);
    }

    public static Optional<String> getBackpackItemId(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackStack(blockEntity)
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .map(ResourceLocation::toString);
    }

    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId) {
        return openBackpack(uuid, itemId, null);
    }

    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId, ServerPlayer fallbackPlayer) {
        if (!isAvailable() || uuid == null || itemId == null || itemId.isBlank()) {
            return findBackpackHandlerByUuid(fallbackPlayer, uuid);
        }
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !BuiltInRegistries.ITEM.containsKey(itemKey)) {
            return findBackpackHandlerByUuid(fallbackPlayer, uuid);
        }
        Item item = BuiltInRegistries.ITEM.get(itemKey);
        if (item == null) {
            return findBackpackHandlerByUuid(fallbackPlayer, uuid);
        }
        Optional<IItemHandler> handler = REFLECTION.openBackpack(uuid, new ItemStack(item));
        return handler.isPresent() ? handler : findBackpackHandlerByUuid(fallbackPlayer, uuid);
    }

    public static Optional<IItemHandler> findBackpackHandlerByUuid(ServerPlayer player, UUID uuid) {
        if (!isAvailable() || player == null || uuid == null) {
            return Optional.empty();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty() || !isSophisticatedBackpackItem(stack)) {
                continue;
            }
            if (uuid.equals(REFLECTION.getStackUuid(stack).orElse(null))) {
                return REFLECTION.openExistingBackpack(stack);
            }
        }
        return Optional.empty();
    }

    private static boolean isSophisticatedBackpackItem(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && MOD_ID.equals(itemId.getNamespace());
    }

    private static final class BackpackReflection {
        private final Class<?> backpackBlockEntityClass;
        private final Method getBackpackWrapper;
        private final Method getContentsUuid;
        private final Method getBackpackStack;
        private final Method fromStack;
        private final Method setContentsUuid;
        private final Method getInventoryForInputOutput;
        private final Method getInventoryHandler;

        private BackpackReflection(
                Class<?> backpackBlockEntityClass,
                Method getBackpackWrapper,
                Method getContentsUuid,
                Method getBackpackStack,
                Method fromStack,
                Method setContentsUuid,
                Method getInventoryForInputOutput,
                Method getInventoryHandler) {
            this.backpackBlockEntityClass = backpackBlockEntityClass;
            this.getBackpackWrapper = getBackpackWrapper;
            this.getContentsUuid = getContentsUuid;
            this.getBackpackStack = getBackpackStack;
            this.fromStack = fromStack;
            this.setContentsUuid = setContentsUuid;
            this.getInventoryForInputOutput = getInventoryForInputOutput;
            this.getInventoryHandler = getInventoryHandler;
        }

        static BackpackReflection tryLoad() {
            if (!ModList.get().isLoaded(MOD_ID)) {
                return null;
            }
            try {
                Class<?> backpackBlockEntity = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity");
                Class<?> backpackWrapper = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.BackpackWrapper");
                Class<?> iBackpackWrapper = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper");

                Method getBw = backpackBlockEntity.getMethod("getBackpackWrapper");
                Method getCu = iBackpackWrapper.getMethod("getContentsUuid");
                Method getStack = findFirstMethod(iBackpackWrapper, "getBackpack", "getBackpackStack");
                Method fromStack = backpackWrapper.getMethod("fromStack", ItemStack.class);
                Method setCu = iBackpackWrapper.getMethod("setContentsUuid", UUID.class);
                Method getInputOutput = iBackpackWrapper.getMethod("getInventoryForInputOutput");
                Method getInventory = iBackpackWrapper.getMethod("getInventoryHandler");
                return new BackpackReflection(
                        backpackBlockEntity,
                        getBw,
                        getCu,
                        getStack,
                        fromStack,
                        setCu,
                        getInputOutput,
                        getInventory);
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                return null;
            }
        }

        private static Method findFirstMethod(Class<?> target, String... names) throws NoSuchMethodException {
            for (String name : names) {
                try {
                    return target.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                }
            }
            throw new NoSuchMethodException(String.join("/", names));
        }

        boolean isBackpackBlockEntity(BlockEntity blockEntity) {
            return blockEntity != null && backpackBlockEntityClass.isInstance(blockEntity);
        }

        Optional<UUID> getBackpackUuid(BlockEntity blockEntity) {
            if (!isBackpackBlockEntity(blockEntity)) {
                return Optional.empty();
            }
            try {
                Object wrapper = getBackpackWrapper.invoke(blockEntity);
                Object uuidOpt = getContentsUuid.invoke(wrapper);
                if (uuidOpt instanceof Optional<?> optional && optional.orElse(null) instanceof UUID uuid) {
                    return Optional.of(uuid);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }

        Optional<ItemStack> getBackpackStack(BlockEntity blockEntity) {
            if (!isBackpackBlockEntity(blockEntity) || getBackpackStack == null) {
                return Optional.empty();
            }
            try {
                Object wrapper = getBackpackWrapper.invoke(blockEntity);
                Object stack = getBackpackStack.invoke(wrapper);
                if (stack instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                    return Optional.of(itemStack);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }

        Optional<IItemHandler> openBackpack(UUID uuid, ItemStack backpackStack) {
            if (uuid == null || backpackStack == null || backpackStack.isEmpty()) {
                return Optional.empty();
            }
            try {
                Object wrapper = fromStack.invoke(null, backpackStack);
                setContentsUuid.invoke(wrapper, uuid);
                Object handler = getInventoryForInputOutput.invoke(wrapper);
                if (handler instanceof IItemHandler itemHandler) {
                    return Optional.of(itemHandler);
                }
                handler = getInventoryHandler.invoke(wrapper);
                if (handler instanceof IItemHandler itemHandler) {
                    return Optional.of(itemHandler);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }

        Optional<IItemHandler> openExistingBackpack(ItemStack backpackStack) {
            if (backpackStack == null || backpackStack.isEmpty()) {
                return Optional.empty();
            }
            try {
                Object wrapper = fromStack.invoke(null, backpackStack.copy());
                Object handler = getInventoryForInputOutput.invoke(wrapper);
                if (handler instanceof IItemHandler itemHandler) {
                    return Optional.of(itemHandler);
                }
                handler = getInventoryHandler.invoke(wrapper);
                if (handler instanceof IItemHandler itemHandler) {
                    return Optional.of(itemHandler);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }

        Optional<UUID> getStackUuid(ItemStack backpackStack) {
            if (backpackStack == null || backpackStack.isEmpty()) {
                return Optional.empty();
            }
            try {
                Object wrapper = fromStack.invoke(null, backpackStack.copy());
                Object uuidOpt = getContentsUuid.invoke(wrapper);
                if (uuidOpt instanceof Optional<?> optional && optional.orElse(null) instanceof UUID uuid) {
                    return Optional.of(uuid);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }
    }
}
