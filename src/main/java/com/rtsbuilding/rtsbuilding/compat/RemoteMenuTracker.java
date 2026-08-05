package com.rtsbuilding.rtsbuilding.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Forge 1.12.2 服务端/客户端远程容器 windowId 状态跟踪器。 */
public final class RemoteMenuTracker {
    private final Predicate<Container> isSupportedMenu;
    private final Map<UUID, Integer> serverMenuIds = new ConcurrentHashMap<>();
    private volatile int clientMenuId = -1;
    private volatile boolean clientMenuPending;

    public RemoteMenuTracker(Predicate<Container> isSupportedMenu) {
        this.isSupportedMenu = isSupportedMenu;
    }

    public boolean isSupported(Container menu) {
        return menu != null && this.isSupportedMenu.test(menu);
    }

    public void markServer(EntityPlayerMP player, Container menu) {
        if (player == null) {
            return;
        }
        if (!isSupported(menu)) {
            clearServer(player);
            return;
        }
        this.serverMenuIds.put(player.getUniqueID(), menu.windowId);
    }

    public void clearServer(EntityPlayerMP player) {
        if (player != null) {
            this.serverMenuIds.remove(player.getUniqueID());
        }
    }

    public void beginClientOpen() {
        this.clientMenuPending = true;
    }

    public void markClient(Container menu) {
        if (!isSupported(menu)) {
            clearClient();
            return;
        }
        this.clientMenuId = menu.windowId;
        this.clientMenuPending = false;
    }

    public void clearClient() {
        this.clientMenuId = -1;
        this.clientMenuPending = false;
    }

    public boolean shouldForceStillValid(Container menu, EntityPlayer player) {
        if (!isSupported(menu) || player == null || player.worldObj == null) {
            return false;
        }
        if (player.worldObj.isRemote) {
            return this.clientMenuPending || menu.windowId == this.clientMenuId;
        }
        if (player instanceof EntityPlayerMP) {
            Integer expected = this.serverMenuIds.get(player.getUniqueID());
            return expected != null && expected.intValue() == menu.windowId;
        }
        return false;
    }
}
