package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
 * that UUID from placed backpack block entities. When the backpack is carried,
 * the bridge first resolves Sophisticated Backpacks' registered player slots
 * (including accessory slots); a virtual stack is only the last fallback. All
 * access is reflective so RTSBuilding does not gain a hard runtime dependency.
 */
public final class RtsBackpackCompat {
    private static final String MOD_ID = "sophisticatedbackpacks";
    private static final String BACKPACK_ITEM_CLASS =
            "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem";
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
        if (!isAvailable() || uuid == null) {
            return Optional.empty();
        }
        Optional<IItemHandler> carriedHandler = findBackpackHandlerByUuid(fallbackPlayer, uuid);
        if (carriedHandler.isPresent()) {
            return carriedHandler;
        }
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        ResourceLocation itemKey = ResourceLocation.tryParse(itemId);
        if (itemKey == null || !BuiltInRegistries.ITEM.containsKey(itemKey)) {
            return findBackpackHandlerByUuid(fallbackPlayer, uuid);
        }
        Item item = BuiltInRegistries.ITEM.get(itemKey);
        if (item == null) {
            return findBackpackHandlerByUuid(fallbackPlayer, uuid);
        }
        return REFLECTION.openBackpack(uuid, new ItemStack(item));
    }

    public static Optional<IItemHandler> findBackpackHandlerByUuid(ServerPlayer player, UUID uuid) {
        if (!isAvailable() || player == null || uuid == null) {
            return Optional.empty();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty() || !isBackpackItem(stack)) {
                continue;
            }
            if (uuid.equals(REFLECTION.getStackUuid(stack).orElse(null))) {
                return REFLECTION.openExistingBackpack(stack);
            }
        }
        return REFLECTION.findCarriedBackpack(player, uuid)
                .flatMap(REFLECTION::openExistingBackpack);
    }

    /**
     * 仅识别精妙背包本体，不把同模组的升级物品误送进背包专用放置链路。
     */
    public static boolean isBackpackItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null || !MOD_ID.equals(itemId.getNamespace())) {
            return false;
        }
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (BACKPACK_ITEM_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static final class BackpackReflection {
        private final Class<?> backpackBlockEntityClass;
        private final Method getBackpackWrapper;
        private final Method getContentsUuid;
        private final Method getBackpackStack;
        private final Method fromStack;
        private final Method getBackpackCapability;
        private final Method itemStackGetCapability;
        private final Method lazyOptionalResolve;
        private final Method setContentsUuid;
        private final Method getInventoryForInputOutput;
        private final Method getInventoryHandler;
        private final Method playerInventoryProviderGet;
        private final Method runOnBackpacks;
        private final Class<?> backpackSlotConsumerClass;

        private BackpackReflection(
                Class<?> backpackBlockEntityClass,
                Method getBackpackWrapper,
                Method getContentsUuid,
                Method getBackpackStack,
                Method fromStack,
                Method getBackpackCapability,
                Method itemStackGetCapability,
                Method lazyOptionalResolve,
                Method setContentsUuid,
                Method getInventoryForInputOutput,
                Method getInventoryHandler,
                Method playerInventoryProviderGet,
                Method runOnBackpacks,
                Class<?> backpackSlotConsumerClass) {
            this.backpackBlockEntityClass = backpackBlockEntityClass;
            this.getBackpackWrapper = getBackpackWrapper;
            this.getContentsUuid = getContentsUuid;
            this.getBackpackStack = getBackpackStack;
            this.fromStack = fromStack;
            this.getBackpackCapability = getBackpackCapability;
            this.itemStackGetCapability = itemStackGetCapability;
            this.lazyOptionalResolve = lazyOptionalResolve;
            this.setContentsUuid = setContentsUuid;
            this.getInventoryForInputOutput = getInventoryForInputOutput;
            this.getInventoryHandler = getInventoryHandler;
            this.playerInventoryProviderGet = playerInventoryProviderGet;
            this.runOnBackpacks = runOnBackpacks;
            this.backpackSlotConsumerClass = backpackSlotConsumerClass;
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
                Method fromStack = findMethodOrNull(backpackWrapper, "fromStack", ItemStack.class);
                Method getBackpackCapability = null;
                Method itemStackGetCapability = null;
                Method lazyOptionalResolve = null;
                if (fromStack == null) {
                    Class<?> capabilityClass = Class.forName("net.minecraftforge.common.capabilities.Capability");
                    Class<?> capabilityBackpackWrapper = Class.forName(
                            "net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper");
                    Class<?> lazyOptionalClass = Class.forName("net.minecraftforge.common.util.LazyOptional");
                    getBackpackCapability = capabilityBackpackWrapper.getMethod("getCapabilityInstance");
                    itemStackGetCapability = ItemStack.class.getMethod("getCapability", capabilityClass);
                    lazyOptionalResolve = lazyOptionalClass.getMethod("resolve");
                }
                Method setCu = iBackpackWrapper.getMethod("setContentsUuid", UUID.class);
                Method getInputOutput = iBackpackWrapper.getMethod("getInventoryForInputOutput");
                Method getInventory = iBackpackWrapper.getMethod("getInventoryHandler");
                Method providerGet = null;
                Method runOnBackpacks = null;
                Class<?> backpackSlotConsumer = null;
                try {
                    Class<?> providerClass = Class.forName(
                            "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider");
                    backpackSlotConsumer = Class.forName(
                            "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider$BackpackInventorySlotConsumer");
                    providerGet = providerClass.getMethod("get");
                    runOnBackpacks = providerClass.getMethod(
                            "runOnBackpacks", net.minecraft.world.entity.player.Player.class, backpackSlotConsumer);
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // 旧版没有统一随身槽位提供器时，仍保留主物品栏与 UUID 虚拟背包回退。
                }
                return new BackpackReflection(
                        backpackBlockEntity,
                        getBw,
                        getCu,
                        getStack,
                        fromStack,
                        getBackpackCapability,
                        itemStackGetCapability,
                        lazyOptionalResolve,
                        setCu,
                        getInputOutput,
                        getInventory,
                        providerGet,
                        runOnBackpacks,
                        backpackSlotConsumer);
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

        private static Method findMethodOrNull(Class<?> target, String name, Class<?>... parameterTypes) {
            try {
                return target.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
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
                Object wrapper = wrapperFromStack(backpackStack);
                if (wrapper == null) {
                    return Optional.empty();
                }
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
                // 随身背包必须使用真实 ItemStack，让精妙背包自己的 wrapper 仓库与饰品槽保持同一实例。
                Object wrapper = wrapperFromStack(backpackStack);
                if (wrapper == null) {
                    return Optional.empty();
                }
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
                Object wrapper = wrapperFromStack(backpackStack);
                if (wrapper == null) {
                    return Optional.empty();
                }
                Object uuidOpt = getContentsUuid.invoke(wrapper);
                if (uuidOpt instanceof Optional<?> optional && optional.orElse(null) instanceof UUID uuid) {
                    return Optional.of(uuid);
                }
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
            }
            return Optional.empty();
        }

        Optional<ItemStack> findCarriedBackpack(ServerPlayer player, UUID uuid) {
            if (player == null || uuid == null || playerInventoryProviderGet == null
                    || runOnBackpacks == null || backpackSlotConsumerClass == null) {
                return Optional.empty();
            }
            ItemStack[] match = new ItemStack[1];
            Object consumer = Proxy.newProxyInstance(
                    backpackSlotConsumerClass.getClassLoader(),
                    new Class<?>[]{backpackSlotConsumerClass},
                    (proxy, method, args) -> {
                        if ("accept".equals(method.getName()) && args != null && args.length > 0
                                && args[0] instanceof ItemStack stack
                                && uuid.equals(getStackUuid(stack).orElse(null))) {
                            match[0] = stack;
                            return true;
                        }
                        return false;
                    });
            try {
                Object provider = playerInventoryProviderGet.invoke(null);
                runOnBackpacks.invoke(provider, player, consumer);
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException ignored) {
                return Optional.empty();
            }
            return Optional.ofNullable(match[0]);
        }

        private Object wrapperFromStack(ItemStack stack)
                throws InvocationTargetException, IllegalAccessException {
            if (fromStack != null) {
                return fromStack.invoke(null, stack);
            }
            if (getBackpackCapability == null || itemStackGetCapability == null || lazyOptionalResolve == null) {
                return null;
            }
            Object capability = getBackpackCapability.invoke(null);
            Object lazyOptional = itemStackGetCapability.invoke(stack, capability);
            Object resolved = lazyOptionalResolve.invoke(lazyOptional);
            return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
        }
    }
}
