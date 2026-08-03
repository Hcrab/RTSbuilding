package com.rtsbuilding.rtsbuilding.client.compat;

import com.rtsbuilding.rtsbuilding.compat.remote.RtsRemoteMenuCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** 客户端远程容器存活与 GUI/Container 安全配对兼容层。 */
@SideOnly(Side.CLIENT)
public final class RtsClientRemoteMenuCompat {
    private static final String[] STORAGE_SCREEN_BASE_CLASSES = {
            "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase",
            "net.p3pp3rf1y.sophisticatedcore.client.gui.StorageGuiContainerBase"
    };

    private RtsClientRemoteMenuCompat() {
    }

    public static Container install(Minecraft minecraft, Container menu) {
        if (minecraft == null || minecraft.player == null || menu == null) return menu;

        Container wrapped = RtsRemoteMenuCompat.wrapRemoteMenu(menu);
        if (RtsRemoteMenuCompat.isSupportedRemoteMenu(wrapped)) {
            RtsRemoteMenuCompat.markClientRemoteMenu(wrapped);
        } else {
            RtsRemoteMenuCompat.clearClientRemoteMenu();
        }

        GuiScreen screen = minecraft.currentScreen;
        if (!isScreenMenuPairSafe(screen, wrapped)) {
            throw new IllegalStateException("Incompatible container " + wrapped.getClass().getName()
                    + " for screen " + screen.getClass().getName());
        }

        minecraft.player.openContainer = wrapped;
        remapContainerScreenMenu(screen, wrapped);
        return wrapped;
    }

    /** 服务端统一拦截 Container 存活检查；客户端不得反射替换第三方容器字段。 */
    public static void relaxValidation(Container menu) {
        // 保留入口，避免改动现有客户端生命周期；具体放宽由 RemoteContainerPlayerMixin 完成。
    }

    private static void remapContainerScreenMenu(GuiScreen screen, Container menu) {
        if (!(screen instanceof GuiContainer) || menu == null) return;
        ((GuiContainer) screen).inventorySlots = menu;
    }

    private static boolean isScreenMenuPairSafe(GuiScreen screen, Container menu) {
        if (screen == null || menu == null) return true;
        String name = screen.getClass().getName();
        if (!name.startsWith("net.p3pp3rf1y.sophisticated")) return true;

        for (String baseClass : STORAGE_SCREEN_BASE_CLASSES) {
            if (isInstanceOf(screen, baseClass)) {
                return RtsRemoteMenuCompat.isStorageContainerMenuBase(menu);
            }
        }
        // 可选模组类在该 1.12 运行环境不存在时不误关原版或其它 GUI。
        return true;
    }

    private static boolean isInstanceOf(Object instance, String className) {
        try {
            return Class.forName(className).isInstance(instance);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

}
