package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Shared tracker for remote-menu state management. Both vanilla chests and
 * modded storage containers (Sophisticated Storage, etc.) follow the same
 * pattern: mark a menu as "remote-opened" so that {@code stillValid()} checks
 * accept the remote origin.
 *
 * <p>Each compat module creates its own instance with a
 * {@code isSupportedMenu} predicate and delegates the shared logic here,
 * eliminating the duplication previously present across compat classes.
 */
public final class RemoteMenuTracker {
    private final Predicate<AbstractContainerMenu> isSupportedMenu;
    private final Map<UUID, Integer> serverMenuIds = new ConcurrentHashMap<>();
    private volatile int clientMenuId = -1;
    private volatile boolean clientMenuPending;

    public RemoteMenuTracker(Predicate<AbstractContainerMenu> isSupportedMenu) {
        this.isSupportedMenu = isSupportedMenu;
    }

    public boolean isSupported(AbstractContainerMenu menu) {
        return menu != null && this.isSupportedMenu.test(menu);
    }

    public void markServer(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || !isSupported(menu)) {
            clearServer(player);
            return;
        }
        this.serverMenuIds.put(player.getUUID(), menu.containerId);
    }

    public void clearServer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        this.serverMenuIds.remove(player.getUUID());
    }

    public void beginClientOpen() {
        this.clientMenuPending = true;
    }

    public void markClient(AbstractContainerMenu menu) {
        if (!isSupported(menu)) {
            clearClient();
            return;
        }
        this.clientMenuId = menu.containerId;
        this.clientMenuPending = false;
    }

    public void clearClient() {
        this.clientMenuId = -1;
        this.clientMenuPending = false;
    }

    public boolean shouldForceStillValid(AbstractContainerMenu menu, Player player) {
        if (!isSupported(menu) || player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return this.clientMenuPending || menu.containerId == this.clientMenuId;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Integer remoteMenuId = this.serverMenuIds.get(serverPlayer.getUUID());
            return remoteMenuId != null && remoteMenuId == menu.containerId;
        }
        return false;
    }
}
