package com.rtsbuilding.rtsbuilding.compat.sophisticatedstorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.fml.ModList;

public final class RtsSophisticatedStorageCompat {
    private static final String MOD_ID = "sophisticatedstorage";
    private static final String BACKPACKS_MOD_ID = "sophisticatedbackpacks";
    private static final String MENU_CLASS_PREFIX = "net.p3pp3rf1y.sophisticatedstorage.common.gui.";
    private static final String BACKPACK_MENU_CLASS_PREFIX = "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.";
    private static final String STORAGE_MENU_BASE_CLASS = "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase";
    private static final Map<UUID, Integer> SERVER_REMOTE_MENU_IDS = new ConcurrentHashMap<>();
    private static volatile int clientRemoteMenuId = -1;
    private static volatile boolean clientRemoteMenuPending;

    private RtsSophisticatedStorageCompat() {
    }

    public static AbstractContainerMenu wrapRemoteMenu(AbstractContainerMenu menu) {
        if (!isSupportedRemoteMenu(menu)) {
            return menu;
        }
        // SophisticatedCore storage screens hard-require the original
        // StorageContainerMenuBase type, so remote opens must preserve it.
        return menu;
    }

    public static boolean isSupportedRemoteMenu(AbstractContainerMenu menu) {
        if (menu == null) {
            return false;
        }
        String menuClassName = menu.getClass().getName();
        if (ModList.get().isLoaded(MOD_ID) && menuClassName.startsWith(MENU_CLASS_PREFIX)) {
            return true;
        }
        return ModList.get().isLoaded(BACKPACKS_MOD_ID) && menuClassName.startsWith(BACKPACK_MENU_CLASS_PREFIX);
    }

    public static boolean isStorageContainerMenuBase(AbstractContainerMenu menu) {
        return menu != null && isInstanceOf(menu, STORAGE_MENU_BASE_CLASS);
    }

    public static void markServerRemoteMenu(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || !isSupportedRemoteMenu(menu)) {
            clearServerRemoteMenu(player);
            return;
        }
        SERVER_REMOTE_MENU_IDS.put(player.getUUID(), menu.containerId);
    }

    public static void clearServerRemoteMenu(ServerPlayer player) {
        if (player == null) {
            return;
        }
        SERVER_REMOTE_MENU_IDS.remove(player.getUUID());
    }

    public static void beginClientRemoteMenuOpen() {
        clientRemoteMenuPending = true;
    }

    public static void markClientRemoteMenu(AbstractContainerMenu menu) {
        if (!isSupportedRemoteMenu(menu)) {
            clearClientRemoteMenu();
            return;
        }
        clientRemoteMenuId = menu.containerId;
        clientRemoteMenuPending = false;
    }

    public static void clearClientRemoteMenu() {
        clientRemoteMenuId = -1;
        clientRemoteMenuPending = false;
    }

    public static boolean shouldForceStillValid(AbstractContainerMenu menu, Player player) {
        if (!isSupportedRemoteMenu(menu) || player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return clientRemoteMenuPending || menu.containerId == clientRemoteMenuId;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Integer remoteMenuId = SERVER_REMOTE_MENU_IDS.get(serverPlayer.getUUID());
            return remoteMenuId != null && remoteMenuId == menu.containerId;
        }
        return false;
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            return Class.forName(className).isInstance(instance);
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
