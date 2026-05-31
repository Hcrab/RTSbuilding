package com.rtsbuilding.rtsbuilding.compat.sophisticatedbackpacks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;

import com.rtsbuilding.rtsbuilding.forgecompat.fml.ModList;

public final class RtsBackpackCompat {
    private static final BackpackReflection REFLECTION = BackpackReflection.tryLoad();

    private RtsBackpackCompat() {
    }

    public static boolean isAvailable() {
        return REFLECTION != null;
    }

    public static Optional<UUID> getBackpackUuid(BlockEntity blockEntity) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.getBackpackUuid(blockEntity);
    }

    public static Optional<IItemHandler> findBackpackHandlerByUuid(ServerPlayer player, UUID uuid) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        return REFLECTION.findBackpackHandlerByUuid(player, uuid);
    }

    private static final class BackpackReflection {
        private final Method getBackpackWrapper;
        private final Method getContentsUuid;

        private BackpackReflection(Method getBackpackWrapper, Method getContentsUuid) {
            this.getBackpackWrapper = getBackpackWrapper;
            this.getContentsUuid = getContentsUuid;
        }

        static BackpackReflection tryLoad() {
            if (!ModList.get().isLoaded("sophisticatedbackpacks")) {
                return null;
            }
            try {
                Class<?> backpackBlockEntity = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity");
                Class<?> backpackWrapper = Class.forName(
                        "net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper");

                Method getBackpackWrapper = backpackBlockEntity.getMethod("getBackpackWrapper");
                Method getContentsUuid = backpackWrapper.getMethod("getContentsUuid");

                return new BackpackReflection(getBackpackWrapper, getContentsUuid);
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

        Optional<IItemHandler> findBackpackHandlerByUuid(ServerPlayer player, UUID uuid) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (uuid.equals(getUuidFromStack(stack))) {
                    return getItemHandlerFromStack(stack);
                }
            }
            return Optional.empty();
        }

        private UUID getUuidFromStack(ItemStack stack) {
            if (stack.isEmpty() || stack.getTag() == null) {
                return null;
            }
            // UUID stored as int array in NBT under "contentsUuid"
            try {
                return stack.getTag().getUUID("contentsUuid");
            } catch (Exception e) {
                return null;
            }
        }

        private Optional<IItemHandler> getItemHandlerFromStack(ItemStack stack) {
            return stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
                    .resolve();
        }
    }
}
