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
    private final Map<UUID, TrackedServerMenu> serverMenus = new ConcurrentHashMap<>();
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
        markServerSession(player, menu);
    }

    /**
     * 记录任意一次由 RTS 生产交互链打开的服务端菜单。
     *
     * <p>服务端公共关窗点不需要依赖第三方菜单白名单；同时校验容器编号和真实对象，
     * 避免编号复用后把另一个本地菜单误判为远程菜单。</p>
     */
    public void markServerSession(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null) {
            clearServer(player);
            return;
        }
        this.serverMenus.put(player.getUUID(), new TrackedServerMenu(menu.containerId, menu));
    }

    public void clearServer(ServerPlayer player) {
        if (player != null) {
            this.serverMenus.remove(player.getUUID());
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
            return isTrackedServerSession(menu, serverPlayer);
        }
        return false;
    }

    /** 仅供服务端统一关窗闸门查询；不依赖第三方菜单类型白名单。 */
    public boolean isTrackedServerSession(AbstractContainerMenu menu, ServerPlayer player) {
        if (menu == null || player == null) {
            return false;
        }
        TrackedServerMenu tracked = this.serverMenus.get(player.getUUID());
        return tracked != null
                && tracked.containerId() == menu.containerId
                && tracked.menu() == menu;
    }

    private record TrackedServerMenu(int containerId, AbstractContainerMenu menu) {
    }
}
