package com.rtsbuilding.rtsbuilding.compat.remote;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

public final class RtsRemoteMenuCompat {
    private static final Map<UUID, Integer> SERVER_REMOTE_MENU_IDS = new ConcurrentHashMap<>();
    private static volatile int clientRemoteMenuId = -1;
    private static volatile boolean clientRemoteMenuPending;

    private RtsRemoteMenuCompat() {
    }

    public static boolean isSupportedRemoteMenu(AbstractContainerMenu menu) {
        return isVanillaChestMenu(menu) || isIronFurnacesMenu(menu) || isGeneratorGaloreMenu(menu);
    }

    public static boolean isVanillaChestMenu(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu;
    }

    public static boolean isIronFurnacesMenu(AbstractContainerMenu menu) {
        return menu != null
                && (isInstanceOf(menu, "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase")
                        || isInstanceOf(menu, "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase"));
    }

    public static boolean isGeneratorGaloreMenu(AbstractContainerMenu menu) {
        return menu != null && isInstanceOf(menu, "cy.jdkdigital.generatorgalore.common.container.GeneratorMenu");
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
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
