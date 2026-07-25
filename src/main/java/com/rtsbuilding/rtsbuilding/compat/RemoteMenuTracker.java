package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 远程菜单有效性状态的共享追踪器。
 *
 * <p>它只记录服务端玩家/菜单编号和客户端待打开状态，不判断具体模组类型，也不负责
 * 打开菜单。各兼容入口通过谓词定义支持范围，从而让 1.21.1 与 1.20.1 使用同一套状态语义。</p>
 */
public final class RemoteMenuTracker {
    private final Predicate<AbstractContainerMenu> supportedMenu;
    private final Map<UUID, Integer> serverMenuIds = new ConcurrentHashMap<>();
    private volatile int clientMenuId = -1;
    private volatile boolean clientMenuPending;

    public RemoteMenuTracker(Predicate<AbstractContainerMenu> supportedMenu) {
        this.supportedMenu = supportedMenu;
    }

    public boolean isSupported(AbstractContainerMenu menu) {
        return menu != null && this.supportedMenu.test(menu);
    }

    public void markServer(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || !isSupported(menu)) {
            clearServer(player);
            return;
        }
        this.serverMenuIds.put(player.getUUID(), menu.containerId);
    }

    public void clearServer(ServerPlayer player) {
        if (player != null) {
            this.serverMenuIds.remove(player.getUUID());
        }
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
