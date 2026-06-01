package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;

public final class RtsBackpackCompat {
    private static final BackpackReflection REFLECTION = BackpackReflection.tryLoad();

    private RtsBackpackCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    /**
     * Get the UUID from a placed backpack block entity.
     */
    public static Optional<UUID> getBackpackUuid(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackUuid(blockEntity);
    }

    /**
     * Get the actual backpack ItemStack from a placed block entity.
     * Used as a template to determine backpack type for virtual stack creation.
     */
    public static Optional<ItemStack> getBackpackStack(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackStack(blockEntity);
    }

    /**
     * Open a backpack by UUID using a template ItemStack to determine the
     * backpack type. Creates a virtual ItemStack that acts as a UUID key
     * to the server-global BackpackStorage. Works regardless of where the
     * real backpack ItemStack is physically stored.
     */
    public static Optional<IItemHandler> openBackpack(UUID uuid, ItemStack template) {
        if (!isAvailable() || uuid == null || template == null || template.isEmpty()) {
            return Optional.empty();
        }
        ItemStack virtual = new ItemStack(template.getItem());
        virtual.getOrCreateTag().putUUID("contentsUuid", uuid);
        return virtual.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
    }

    /**
     * Open a backpack by UUID and item registry name.
     * Resolves the Item from the registry and creates a virtual backpack.
     * Falls back to scanning the player's inventory if itemId is unknown
     * (legacy save data before bpItem was stored).
     */
    public static Optional<IItemHandler> openBackpack(UUID uuid, String itemId, net.minecraft.server.level.ServerPlayer fallbackPlayer) {
        if (!isAvailable() || uuid == null) {
            return Optional.empty();
        }
        if (itemId != null && !itemId.isBlank()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(itemId));
            if (item != null) {
                ItemStack virtual = new ItemStack(item);
                virtual.getOrCreateTag().putUUID("contentsUuid", uuid);
                Optional<IItemHandler> result = virtual.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
                if (result.isPresent()) {
                    return result;
                }
            }
        }
        // Legacy fallback: scan player inventory for matching UUID
        return findBackpackHandlerByUuid(fallbackPlayer, uuid);
    }

    /**
     * Legacy fallback — scans player inventory for a backpack matching the UUID.
     * Only used when itemId is unavailable (old save data).
     */
    public static Optional<IItemHandler> findBackpackHandlerByUuid(net.minecraft.server.level.ServerPlayer player, UUID uuid) {
        if (!isAvailable() || player == null || uuid == null) {
            return Optional.empty();
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || stack.getTag() == null) {
                continue;
            }
            try {
                if (uuid.equals(stack.getTag().getUUID("contentsUuid"))) {
                    return stack.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve();
                }
            } catch (Exception e) {
                continue;
            }
        }
        return Optional.empty();
    }

    /**
     * Get the registry name of the backpack item from a block entity.
     * Used to persist the backpack type for later virtual reconstruction.
     */
    public static Optional<String> getBackpackItemId(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackStack(blockEntity)
                .map(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static final class BackpackReflection {
        private final Method getBackpackWrapper;
        private final Method getContentsUuid;
        private final Method getBackpackStackMethod;

        private BackpackReflection(Method getBackpackWrapper, Method getContentsUuid, Method getBackpackStackMethod) {
            this.getBackpackWrapper = getBackpackWrapper;
            this.getContentsUuid = getContentsUuid;
            this.getBackpackStackMethod = getBackpackStackMethod;
        }

        static BackpackReflection tryLoad() {
            boolean loaded = ModList.get().isLoaded("sophisticatedbackpacks");
            if (!loaded) {
                return null;
            }
            try {
                Class<?> backpackBlockEntity = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity");
                Class<?> iBackpackWrapper = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper");

                Method getBw = backpackBlockEntity.getMethod("getBackpackWrapper");
                Method getCu = iBackpackWrapper.getMethod("getContentsUuid");
                Method getBs = null;
                try {
                    getBs = iBackpackWrapper.getMethod("getBackpack");
                } catch (NoSuchMethodException e) {
                    try {
                        getBs = iBackpackWrapper.getMethod("getBackpackStack");
                    } catch (NoSuchMethodException e2) {
                    }
                }

                return new BackpackReflection(getBw, getCu, getBs);
            } catch (ClassNotFoundException | NoSuchMethodException e) {
                return null;
            }
        }

        Optional<UUID> getBackpackUuid(BlockEntity blockEntity) {
            try {
                Object wrapper = getBackpackWrapper.invoke(blockEntity);
                Optional<?> uuidOpt = (Optional<?>) getContentsUuid.invoke(wrapper);
                return uuidOpt.map(u -> (UUID) u);
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
                return Optional.empty();
            }
        }

        Optional<ItemStack> getBackpackStack(BlockEntity blockEntity) {
            if (getBackpackStackMethod == null) {
                return Optional.empty();
            }
            try {
                Object wrapper = getBackpackWrapper.invoke(blockEntity);
                return Optional.ofNullable((ItemStack) getBackpackStackMethod.invoke(wrapper));
            } catch (IllegalAccessException | InvocationTargetException | ClassCastException e) {
                return Optional.empty();
            }
        }
    }
}
