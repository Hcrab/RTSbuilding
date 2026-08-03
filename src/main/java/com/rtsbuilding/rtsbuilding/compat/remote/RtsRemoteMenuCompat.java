package com.rtsbuilding.rtsbuilding.compat.remote;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.trace.RtsTraceIds;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.12 远程容器兼容边界。
 *
 * <p>服务端和客户端只对明确标记的同一 windowId 放宽距离存活检查，避免把普通
 * 本地容器永久变成远程容器。第三方容器仍保持原实例，防止其 GUI 对具体容器类型
 * 的强制转换失效。</p>
 */
public final class RtsRemoteMenuCompat {
    private static final Map<UUID, ServerMarker> SERVER_MARKERS = new ConcurrentHashMap<>();
    private static volatile int clientWindowId = -1;
    private static volatile boolean clientWindowPending;

    private static final String DISABLE_PERSISTENCE_PROPERTY =
            "rtsbuilding.guiCompatDisableRemoteMenuPersistence";
    private static final String DISABLE_PERSISTENCE_ENV =
            "RTSBUILDING_GUI_COMPAT_DISABLE_REMOTE_MENU_PERSISTENCE";

    private static final String[] STORAGE_CONTAINER_BASE_CLASSES = {
            "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase",
            "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerBase"
    };
    private static final String SOPHISTICATED_STORAGE_PKG =
            "net.p3pp3rf1y.sophisticatedstorage.common.gui.";
    private static final String SOPHISTICATED_BACKPACKS_PKG =
            "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.";

    private RtsRemoteMenuCompat() {
    }

    public static boolean isSupportedRemoteMenu(Container menu) {
        // 资格来自“这个 windowId 是由一次 RTS 远程交互明确打开的”，而不是容器类名。
        // windowId 0 是玩家自身 inventoryContainer，绝不能被标成远程菜单。
        return menu != null && menu.windowId != 0;
    }

    public static boolean isVanillaChestMenu(Container menu) {
        return menu instanceof ContainerChest;
    }

    public static boolean isIronFurnacesMenu(Container menu) {
        return menu != null && (isInstanceOf(menu,
                "ironfurnaces.container.furnaces.BlockIronFurnaceContainerBase")
                || isInstanceOf(menu, "ironfurnaces.container.BlockWirelessEnergyHeaterContainerBase")
                || isInstanceOf(menu, "ironfurnaces.container.ContainerIronFurnaceBase"));
    }

    public static boolean isGeneratorGaloreMenu(Container menu) {
        return menu != null && (isInstanceOf(menu,
                "cy.jdkdigital.generatorgalore.common.container.GeneratorMenu")
                || isInstanceOf(menu, "cy.jdkdigital.generatorgalore.common.container.GeneratorContainer"));
    }

    public static boolean isSophisticatedMenu(Container menu) {
        if (menu == null) return false;
        String name = menu.getClass().getName();
        return name.startsWith(SOPHISTICATED_STORAGE_PKG)
                || name.startsWith(SOPHISTICATED_BACKPACKS_PKG);
    }

    /** 保留原容器具体类型，供依赖具体容器类的第三方 GUI 安全配对。 */
    public static Container wrapRemoteMenu(Container menu) {
        return menu;
    }

    public static boolean isStorageContainerMenuBase(Container menu) {
        if (menu == null) return false;
        for (String className : STORAGE_CONTAINER_BASE_CLASSES) {
            if (isInstanceOf(menu, className)) return true;
        }
        return false;
    }

    public static void markServerRemoteMenu(EntityPlayerMP player, Container menu) {
        markServerRemoteMenu(player, menu, RtsTraceIds.NONE);
    }

    /** 在服务端交互开始前保留 trace，使早于菜单标记发生的校验也能出现在同一条时间线中。 */
    public static void beginServerRemoteMenuOpen(EntityPlayerMP player, long traceId) {
        if (player == null) return;
        SERVER_MARKERS.put(player.getUniqueID(), new ServerMarker(-1, traceId, true));
    }

    public static void markServerRemoteMenu(EntityPlayerMP player, Container menu, long traceId) {
        if (player == null) return;
        if (!isSupportedRemoteMenu(menu)) {
            clearServerRemoteMenu(player);
            return;
        }
        SERVER_MARKERS.put(player.getUniqueID(), new ServerMarker(menu.windowId, traceId, false));
    }

    public static void cancelServerRemoteMenuOpen(EntityPlayerMP player, long traceId, String reason) {
        if (player == null) return;
        UUID playerId = player.getUniqueID();
        ServerMarker marker = SERVER_MARKERS.get(playerId);
        if (marker == null || !marker.pending || marker.traceId != traceId) return;
        SERVER_MARKERS.remove(playerId, marker);
        if (RtsTraceIds.isPresent(traceId)) {
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=MENU_PENDING_CLEARED trace={} kind=REMOTE_GUI reason={}",
                    RtsTraceIds.format(traceId), reason);
        }
    }

    public static void clearServerRemoteMenu(EntityPlayerMP player) {
        if (player != null) SERVER_MARKERS.remove(player.getUniqueID());
    }

    public static void beginClientRemoteMenuOpen() {
        clientWindowPending = true;
    }

    public static void markClientRemoteMenu(Container menu) {
        if (!isSupportedRemoteMenu(menu)) {
            clearClientRemoteMenu();
            return;
        }
        clientWindowId = menu.windowId;
        clientWindowPending = false;
    }

    public static void clearClientRemoteMenu() {
        clientWindowId = -1;
        clientWindowPending = false;
    }

    public static boolean shouldForceStillValid(Container menu, EntityPlayer player) {
        if (isRemoteMenuPersistenceDisabledForProbe()
                || !isSupportedRemoteMenu(menu) || player == null) {
            return false;
        }
        if (player.world.isRemote) {
            return clientWindowPending || menu.windowId == clientWindowId;
        }
        if (player instanceof EntityPlayerMP) {
            ServerMarker marker = SERVER_MARKERS.get(player.getUniqueID());
            if (marker == null) return false;
            if (marker.pending) {
                marker.logPendingValidation(menu);
                return false;
            }
            if (marker.windowId == menu.windowId) {
                marker.logForcedValidation(menu);
                return true;
            }
            marker.logWindowMismatch(menu);
            return false;
        }
        return false;
    }

    public static boolean isLocalSophisticatedMenu(Container menu, EntityPlayer player) {
        return isSophisticatedMenu(menu) && !shouldForceStillValid(menu, player);
    }

    public static boolean isRemoteMenuPersistenceDisabledForProbe() {
        String configured = System.getProperty(DISABLE_PERSISTENCE_PROPERTY);
        if (isBlank(configured)) configured = System.getenv(DISABLE_PERSISTENCE_ENV);
        return "1".equals(configured)
                || "true".equalsIgnoreCase(configured)
                || "yes".equalsIgnoreCase(configured);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            return Class.forName(className).isInstance(instance);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    /** 每名玩家只保留一个远程菜单标记，并对高频 stillValid 检查实行一次性日志。 */
    private static final class ServerMarker {
        private final int windowId;
        private final long traceId;
        private final boolean pending;
        private volatile boolean pendingValidationLogged;
        private volatile boolean forcedValidationLogged;
        private volatile boolean mismatchLogged;

        private ServerMarker(int windowId, long traceId, boolean pending) {
            this.windowId = windowId;
            this.traceId = traceId;
            this.pending = pending;
        }

        private void logPendingValidation(Container menu) {
            if (pendingValidationLogged || !RtsTraceIds.isPresent(traceId)) return;
            pendingValidationLogged = true;
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-TRACE] side=S event=STILL_VALID_BEFORE_MARK trace={} kind=REMOTE_GUI checkedWindow={} menu={} forced=false",
                    RtsTraceIds.format(traceId), menu.windowId, menu.getClass().getName());
        }

        private void logForcedValidation(Container menu) {
            if (forcedValidationLogged || !RtsTraceIds.isPresent(traceId)) return;
            forcedValidationLogged = true;
            RtsbuildingMod.LOGGER.info(
                    "[RTS-TRACE] side=S event=STILL_VALID_FORCED trace={} kind=REMOTE_GUI window={} menu={}",
                    RtsTraceIds.format(traceId), menu.windowId, menu.getClass().getName());
        }

        private void logWindowMismatch(Container menu) {
            if (mismatchLogged || !RtsTraceIds.isPresent(traceId)) return;
            mismatchLogged = true;
            RtsbuildingMod.LOGGER.warn(
                    "[RTS-TRACE] side=S event=STILL_VALID_MISMATCH trace={} kind=REMOTE_GUI expectedWindow={} checkedWindow={} menu={} forced=false",
                    RtsTraceIds.format(traceId), windowId, menu.windowId, menu.getClass().getName());
        }
    }
}
