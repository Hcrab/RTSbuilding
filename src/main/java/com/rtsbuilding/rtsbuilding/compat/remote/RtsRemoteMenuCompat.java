package com.rtsbuilding.rtsbuilding.compat.remote;

import com.rtsbuilding.rtsbuilding.compat.RemoteMenuTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;

public final class RtsRemoteMenuCompat {
    private static final RemoteMenuTracker TRACKER = new RemoteMenuTracker(RtsRemoteMenuCompat::isSupportedRemoteMenu);
    private static final String DISABLE_PERSISTENCE_PROPERTY =
            "rtsbuilding.guiCompatDisableRemoteMenuPersistence";
    private static final String DISABLE_PERSISTENCE_ENV =
            "RTSBUILDING_GUI_COMPAT_DISABLE_REMOTE_MENU_PERSISTENCE";

    private static final String STORAGE_MENU_BASE_CLASS =
            "net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase";
    private static final String SOPHISTICATED_STORAGE_PKG =
            "net.p3pp3rf1y.sophisticatedstorage.common.gui.";
    private static final String SOPHISTICATED_BACKPACKS_PKG =
            "net.p3pp3rf1y.sophisticatedbackpacks.common.gui.";

    private RtsRemoteMenuCompat() {
    }

    // ==================== 容器类型检测 ====================

    public static boolean isSupportedRemoteMenu(AbstractContainerMenu menu) {
        return isVanillaChestMenu(menu)
                || isIronFurnacesMenu(menu)
                || isGeneratorGaloreMenu(menu)
                || isSophisticatedMenu(menu);
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

    public static boolean isSophisticatedMenu(AbstractContainerMenu menu) {
        if (menu == null) {
            return false;
        }
        String name = menu.getClass().getName();
        return name.startsWith(SOPHISTICATED_STORAGE_PKG)
                || name.startsWith(SOPHISTICATED_BACKPACKS_PKG);
    }

    // ==================== Sophisticated* 专用工具 ====================

    /**
     * SophisticatedCore storage screens hard-require the original
     * StorageContainerMenuBase type, so remote opens must preserve it.
     * Always returns the same menu instance (no wrapping needed).
     */
    public static AbstractContainerMenu wrapRemoteMenu(AbstractContainerMenu menu) {
        return menu;
    }

    public static boolean isStorageContainerMenuBase(AbstractContainerMenu menu) {
        return menu != null && isInstanceOf(menu, STORAGE_MENU_BASE_CLASS);
    }

    // ==================== RemoteMenuTracker 委托 ====================

    public static void markServerRemoteMenu(ServerPlayer player, AbstractContainerMenu menu) {
        TRACKER.markServer(player, menu);
    }

    /** 记录生产交互链打开的任意菜单，供服务端统一 stillValid 闸门使用。 */
    public static void markServerRemoteMenuSession(ServerPlayer player, AbstractContainerMenu menu) {
        TRACKER.markServerSession(player, menu);
    }

    public static void clearServerRemoteMenu(ServerPlayer player) {
        TRACKER.clearServer(player);
    }

    public static void beginClientRemoteMenuOpen() {
        TRACKER.beginClientOpen();
    }

    public static void markClientRemoteMenu(AbstractContainerMenu menu) {
        TRACKER.markClient(menu);
    }

    public static void clearClientRemoteMenu() {
        TRACKER.clearClient();
    }

    public static boolean shouldForceStillValid(AbstractContainerMenu menu, Player player) {
        if (isRemoteMenuPersistenceDisabledForProbe()) {
            return false;
        }
        return TRACKER.shouldForceStillValid(menu, player);
    }

    /**
     * 服务端在真正关闭菜单之前使用的通用判定。
     * 精确绑定玩家和菜单对象，因此不需要枚举第三方菜单类。
     */
    public static boolean shouldKeepServerRemoteMenuOpen(AbstractContainerMenu menu, Player player) {
        if (isRemoteMenuPersistenceDisabledForProbe() || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        return TRACKER.isTrackedServerSession(menu, serverPlayer);
    }

    public static boolean isLocalSophisticatedMenu(AbstractContainerMenu menu, Player player) {
        return isSophisticatedMenu(menu) && !shouldForceStillValid(menu, player);
    }

    public static boolean isRemoteMenuPersistenceDisabledForProbe() {
        String configured = System.getProperty(DISABLE_PERSISTENCE_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(DISABLE_PERSISTENCE_ENV);
        }
        return "1".equals(configured)
                || "true".equalsIgnoreCase(configured)
                || "yes".equalsIgnoreCase(configured);
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            ClassLoader loader = instance.getClass().getClassLoader();
            return Class.forName(className, false, loader).isInstance(instance);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
