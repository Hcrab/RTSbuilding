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
    private final Map<UUID, TrackedServerMenu> serverMenus = new ConcurrentHashMap<>();
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
        markServerSession(player, menu);
    }

    /**
     * 记录任意一次由 RTS 生产交互链打开的服务端菜单。
     *
     * <p>这里故意不要求菜单出现在显式兼容名单中。服务端每 tick 的距离校验是所有
     * {@link AbstractContainerMenu} 的共同关门点；按真实菜单对象跟踪，既能覆盖未知模组菜单，
     * 又不会因为容器 ID 在稍后被复用而错误放行另一个本地菜单。
     */
    public void markServerSession(ServerPlayer player, AbstractContainerMenu menu) {
        if (player == null || menu == null) {
            clearServer(player);
            return;
        }
        this.serverMenus.put(player.getUUID(), new TrackedServerMenu(menu.containerId, menu));
    }

    public void clearServer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        this.serverMenus.remove(player.getUUID());
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

    /** 仅供服务端统一的菜单关闭闸门查询；不依赖第三方菜单类型白名单。 */
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
