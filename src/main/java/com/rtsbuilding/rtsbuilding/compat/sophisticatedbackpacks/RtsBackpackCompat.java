package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Retro Sophisticated Backpacks 1.12.2 的可选链接存储桥。
 *
 * <p>Retro SB 把 UUID 与内容保存在 {@code BackpackWrapper} capability 中。方块形态从
 * {@code BackpackTileEntity.getWrapper()} 读取；携带形态优先返回玩家真实物品栏/饰品栏里的能力，
 * 然后查询模组自己的 UUID 缓存，最后才创建带相同 UUID 的虚拟物品能力。
 */
public final class RtsBackpackCompat {
    private static final String MOD_ID = "retro_sophisticated_backpacks";
    private static final String BACKPACK_ITEM_CLASS =
            "com.cleanroommc.retrosophisticatedbackpacks.item.BackpackItem";
    private static final BackpackReflection REFLECTION = BackpackReflection.tryLoad();

    private RtsBackpackCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static boolean isBackpackBlockEntity(TileEntity blockEntity) {
        return isAvailable() && REFLECTION.isBackpackBlockEntity(blockEntity);
    }

    public static Optional<UUID> getBackpackUuid(TileEntity blockEntity) {
        return isAvailable() ? REFLECTION.getBackpackUuid(blockEntity) : Optional.<UUID>empty();
    }

    public static Optional<String> getBackpackItemId(TileEntity blockEntity) {
        return isAvailable() ? REFLECTION.getBackpackItemId(blockEntity) : Optional.<String>empty();
    }

    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId) {
        return openBackpack(uuid, itemId, null);
    }

    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId, EntityPlayerMP fallbackPlayer) {
        if (!isAvailable() || uuid == null) return Optional.empty();

        Optional<IItemHandler> carried = findBackpackHandlerByUuid(fallbackPlayer, uuid);
        if (carried.isPresent()) return carried;
        Optional<IItemHandler> cached = REFLECTION.openCachedBackpack(uuid);
        if (cached.isPresent()) return cached;
        if (itemId == null || itemId.trim().isEmpty()) return Optional.empty();

        Item item;
        try {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        if (item == null) return Optional.empty();
        return REFLECTION.openBackpack(uuid, new ItemStack(item));
    }

    public static Optional<IItemHandler> findBackpackHandlerByUuid(EntityPlayerMP player, UUID uuid) {
        if (!isAvailable() || player == null || uuid == null) return Optional.empty();
        for (int slot = 0; slot < player.inventory.getSizeInventory(); slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (!isBackpackItem(stack)) continue;
            if (uuid.equals(REFLECTION.getStackUuid(stack).orElse(null))) {
                return REFLECTION.openExistingBackpack(stack);
            }
        }
        return REFLECTION.findBaubleBackpack(player, uuid);
    }

    /** 命名空间里还有升级物品，因此同时校验真实 BackpackItem 类层级。 */
    public static boolean isBackpackItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation itemId = stack.getItem().getRegistryName();
        if (itemId == null || !MOD_ID.equals(itemId.getNamespace())) return false;
        for (Class<?> type = stack.getItem().getClass(); type != null; type = type.getSuperclass()) {
            if (BACKPACK_ITEM_CLASS.equals(type.getName())) return true;
        }
        return false;
    }

    /** 只在 Retro SB 与可选 Baubles 已加载后解析其类，避免形成硬运行时依赖。 */
    private static final class BackpackReflection {
        private final Class<?> backpackBlockEntityClass;
        private final Method blockEntityGetWrapper;
        private final Method wrapperGetUuid;
        private final Method wrapperSetUuid;
        private final Capability<?> backpackCapability;
        private final Object capabilityHandlerInstance;
        private final Method capabilityHandlerGetCache;
        private final Method baublesGetHandler;
        private final Method baublesGetSlots;
        private final Method baublesGetStackInSlot;

        private BackpackReflection(Class<?> backpackBlockEntityClass, Method blockEntityGetWrapper,
                Method wrapperGetUuid, Method wrapperSetUuid, Capability<?> backpackCapability,
                Object capabilityHandlerInstance, Method capabilityHandlerGetCache, Method baublesGetHandler,
                Method baublesGetSlots, Method baublesGetStackInSlot) {
            this.backpackBlockEntityClass = backpackBlockEntityClass;
            this.blockEntityGetWrapper = blockEntityGetWrapper;
            this.wrapperGetUuid = wrapperGetUuid;
            this.wrapperSetUuid = wrapperSetUuid;
            this.backpackCapability = backpackCapability;
            this.capabilityHandlerInstance = capabilityHandlerInstance;
            this.capabilityHandlerGetCache = capabilityHandlerGetCache;
            this.baublesGetHandler = baublesGetHandler;
            this.baublesGetSlots = baublesGetSlots;
            this.baublesGetStackInSlot = baublesGetStackInSlot;
        }

        private static BackpackReflection tryLoad() {
            if (!Loader.isModLoaded(MOD_ID)) return null;
            try {
                Class<?> blockEntityClass = Class.forName(
                        "com.cleanroommc.retrosophisticatedbackpacks.tileentity.BackpackTileEntity");
                Class<?> wrapperClass = Class.forName(
                        "com.cleanroommc.retrosophisticatedbackpacks.capability.BackpackWrapper");
                Class<?> capabilitiesClass = Class.forName(
                        "com.cleanroommc.retrosophisticatedbackpacks.capability.Capabilities");
                Method getWrapper = blockEntityClass.getMethod("getWrapper");
                Method getUuid = wrapperClass.getMethod("getUuid");
                Method setUuid = wrapperClass.getMethod("setUuid", UUID.class);
                Capability<?> capability = (Capability<?>) capabilitiesClass
                        .getField("BACKPACK_CAPABILITY").get(null);
                if (capability == null) return null;

                Object cacheOwner = null;
                Method getCache = null;
                try {
                    Class<?> handlerClass = Class.forName(
                            "com.cleanroommc.retrosophisticatedbackpacks.handler.CapabilityHandler");
                    Field instanceField = handlerClass.getField("INSTANCE");
                    cacheOwner = instanceField.get(null);
                    getCache = handlerClass.getMethod("getBACKPACK_INVENTORY_CACHE");
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // 早期版本没有公开 UUID 缓存时，仍可使用方块和真实携带物品能力。
                }

                Method baublesGet = null;
                Method baublesSlots = null;
                Method baublesStack = null;
                if (Loader.isModLoaded("baubles")) {
                    try {
                        Class<?> baublesApi = Class.forName("baubles.api.BaublesApi");
                        Class<?> baublesHandler = Class.forName("baubles.api.cap.IBaublesItemHandler");
                        baublesGet = baublesApi.getMethod("getBaublesHandler", EntityPlayer.class);
                        baublesSlots = baublesHandler.getMethod("getSlots");
                        baublesStack = baublesHandler.getMethod("getStackInSlot", int.class);
                    } catch (ReflectiveOperationException | LinkageError ignored) {
                        baublesGet = null;
                        baublesSlots = null;
                        baublesStack = null;
                    }
                }
                return new BackpackReflection(blockEntityClass, getWrapper, getUuid, setUuid, capability,
                        cacheOwner, getCache, baublesGet, baublesSlots, baublesStack);
            } catch (ReflectiveOperationException | LinkageError | ClassCastException ignored) {
                return null;
            }
        }

        private boolean isBackpackBlockEntity(TileEntity blockEntity) {
            return blockEntity != null && this.backpackBlockEntityClass.isInstance(blockEntity);
        }

        private Optional<UUID> getBackpackUuid(TileEntity blockEntity) {
            Object wrapper = wrapperFromBlockEntity(blockEntity);
            Object uuid = invoke(this.wrapperGetUuid, wrapper);
            return uuid instanceof UUID ? Optional.of((UUID) uuid) : Optional.<UUID>empty();
        }

        private Optional<String> getBackpackItemId(TileEntity blockEntity) {
            if (!isBackpackBlockEntity(blockEntity) || blockEntity.getWorld() == null) return Optional.empty();
            try {
                Block block = blockEntity.getWorld().getBlockState(blockEntity.getPos()).getBlock();
                Item item = Item.getItemFromBlock(block);
                ResourceLocation id = item == null ? null : item.getRegistryName();
                return id == null ? Optional.<String>empty() : Optional.of(id.toString());
            } catch (RuntimeException ignored) {
                return Optional.empty();
            }
        }

        private Optional<IItemHandler> openBackpack(UUID uuid, ItemStack backpackStack) {
            if (uuid == null || backpackStack == null || backpackStack.isEmpty()) return Optional.empty();
            Object wrapper = wrapperFromStack(backpackStack);
            if (wrapper == null) return Optional.empty();
            try {
                this.wrapperSetUuid.invoke(wrapper, uuid);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
                return Optional.empty();
            }
            return handlerFromWrapper(wrapper);
        }

        private Optional<IItemHandler> openExistingBackpack(ItemStack backpackStack) {
            if (backpackStack == null || backpackStack.isEmpty()) return Optional.empty();
            return handlerFromWrapper(wrapperFromStack(backpackStack));
        }

        private Optional<UUID> getStackUuid(ItemStack backpackStack) {
            if (backpackStack == null || backpackStack.isEmpty()) return Optional.empty();
            Object uuid = invoke(this.wrapperGetUuid, wrapperFromStack(backpackStack));
            return uuid instanceof UUID ? Optional.of((UUID) uuid) : Optional.<UUID>empty();
        }

        private Optional<IItemHandler> openCachedBackpack(UUID uuid) {
            if (uuid == null || this.capabilityHandlerInstance == null || this.capabilityHandlerGetCache == null) {
                return Optional.empty();
            }
            Object cache = invoke(this.capabilityHandlerGetCache, this.capabilityHandlerInstance);
            if (!(cache instanceof Map<?, ?>)) return Optional.empty();
            return handlerFromWrapper(((Map<?, ?>) cache).get(uuid));
        }

        private Optional<IItemHandler> findBaubleBackpack(EntityPlayerMP player, UUID uuid) {
            if (player == null || uuid == null || this.baublesGetHandler == null
                    || this.baublesGetSlots == null || this.baublesGetStackInSlot == null) {
                return Optional.empty();
            }
            Object handler = invoke(this.baublesGetHandler, null, player);
            Object slotsValue = invoke(this.baublesGetSlots, handler);
            int slots = slotsValue instanceof Number ? ((Number) slotsValue).intValue() : 0;
            for (int slot = 0; slot < slots; slot++) {
                Object value = invoke(this.baublesGetStackInSlot, handler, slot);
                if (!(value instanceof ItemStack)) continue;
                ItemStack stack = (ItemStack) value;
                if (isBackpackItem(stack) && uuid.equals(getStackUuid(stack).orElse(null))) {
                    return openExistingBackpack(stack);
                }
            }
            return Optional.empty();
        }

        private Object wrapperFromBlockEntity(TileEntity blockEntity) {
            return isBackpackBlockEntity(blockEntity) ? invoke(this.blockEntityGetWrapper, blockEntity) : null;
        }

        @SuppressWarnings("unchecked")
        private Object wrapperFromStack(ItemStack stack) {
            if (stack == null || stack.isEmpty() || this.backpackCapability == null) return null;
            try {
                Capability<Object> capability = (Capability<Object>) this.backpackCapability;
                Object wrapper = stack.getCapability(capability, null);
                if (wrapper != null) return wrapper;
                return stack.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private Optional<IItemHandler> handlerFromWrapper(Object wrapper) {
            return wrapper instanceof IItemHandler
                    ? Optional.of((IItemHandler) wrapper) : Optional.<IItemHandler>empty();
        }

        private static Object invoke(Method method, Object target, Object... arguments) {
            if (method == null) return null;
            try {
                return method.invoke(target, arguments);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException ignored) {
                return null;
            }
        }
    }
}
