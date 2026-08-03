package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * 解析 JEI/HEI 配方页背后的真实容器画面。
 *
 * <p>本类只负责客户端画面身份，不读取 JEI 的配方注册表，也不发送网络包。1.12 的
 * {@code RecipesGui} 会暂时替换 {@code Minecraft.currentScreen}，但会通过公开的
 * {@code getParentScreen()} 保留原容器画面；用反射读取这个稳定边界，可同时兼容 JEI
 * 与 HEI，又不会把实现类变成生产编译依赖。任何结构差异都只返回 {@code null}。</p>
 */
public final class RtsJeiScreenContext {
    private static final int MAX_PARENT_DEPTH = 3;

    private RtsJeiScreenContext() {
    }

    public static boolean isRtsCraftTerminal(Container container) {
        GuiContainer parent = resolveParentContainerScreen();
        return parent instanceof RtsCraftTerminalScreen
                && parent.inventorySlots == container;
    }

    public static boolean hasActiveContainerOverlay(Container container) {
        if (!RtsClientUiStateStore.isContainerOverlayEnabled()) {
            return false;
        }
        GuiContainer parent = resolveParentContainerScreen();
        if (parent == null || parent instanceof RtsCraftTerminalScreen
                || parent.inventorySlots != container) {
            return false;
        }
        ClientRtsController controller = ClientRtsController.get();
        return controller.canUseStorageOverlay() && controller.hasStoragePageSnapshot();
    }

    @Nullable
    public static GuiContainer resolveParentContainerScreen() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        for (int depth = 0; screen != null && depth < MAX_PARENT_DEPTH; depth++) {
            if (screen instanceof GuiContainer) {
                return (GuiContainer) screen;
            }
            screen = parentOf(screen);
        }
        return null;
    }

    @Nullable
    private static GuiScreen parentOf(GuiScreen screen) {
        try {
            Method method = screen.getClass().getMethod("getParentScreen");
            Object parent = method.invoke(screen);
            return parent instanceof GuiScreen ? (GuiScreen) parent : null;
        } catch (ReflectiveOperationException | RuntimeException incompatibleJei) {
            return null;
        }
    }
}
