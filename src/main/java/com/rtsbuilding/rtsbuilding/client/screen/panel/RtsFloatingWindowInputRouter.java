package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.uicore.event.UiEventReply;
import com.rtsbuilding.rtsbuilding.uicore.event.UiKeyEvent;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.routing.KeyboardFocus;
import com.rtsbuilding.rtsbuilding.uicore.routing.PointerCapture;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiEscapeStack;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiEventRouter;
import com.rtsbuilding.rtsbuilding.uicore.routing.UiLayerStack;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 浮窗层到 Core 权威事件路由器的 26.1 生产适配器。
 *
 * <p>这里只翻译 Minecraft 输入，并同步窗口 bounds、z-order、可见性与 modal
 * 标记；不绘制窗口、不执行玩法 Action，也不保留第二份鼠标捕获规则。这样拖拽、
 * 释放、滚轮、键盘、字符和 Escape 都经由同一个 Core router，窗口边界仍会阻断
 * 世界输入。</p>
 */
final class RtsFloatingWindowInputRouter {
    private final List<RtsWindowPanel> windows;
    private final UiLayerStack<RtsWindowPanel> layers = new UiLayerStack<>();
    private final PointerCapture<RtsWindowPanel> pointerCapture = new PointerCapture<>();
    private final KeyboardFocus<RtsWindowPanel> keyboardFocus = new KeyboardFocus<>();
    private final UiEscapeStack<RtsWindowPanel> escapeStack = new UiEscapeStack<>();
    private final UiEventRouter<RtsWindowPanel> router =
            new UiEventRouter<>(layers, pointerCapture, keyboardFocus, escapeStack);
    private final Map<RtsWindowPanel, String> ids = new IdentityHashMap<>();

    RtsFloatingWindowInputRouter(List<RtsWindowPanel> windows) {
        this.windows = windows;
        for (int i = 0; i < windows.size(); i++) {
            RtsWindowPanel window = windows.get(i);
            String id = "floating-window-" + i;
            ids.put(window, id);
            layers.register(id, window, UiRect.EMPTY, false, false);
        }
    }

    boolean mouseClicked(double x, double y, int button) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(
                UiPointerEvent.Type.PRESS, x, y, button, 0.0D, 0.0D)));
    }

    boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(
                UiPointerEvent.Type.DRAG, x, y, button, dragX, dragY)));
    }

    boolean mouseReleased(double x, double y, int button) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(
                UiPointerEvent.Type.RELEASE, x, y, button, 0.0D, 0.0D)));
    }

    boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(
                UiPointerEvent.Type.SCROLL, x, y,
                UiPointerEvent.NO_BUTTON, scrollX, scrollY)));
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        synchronizeLayers();
        UiEventReply reply = keyCode == GLFW.GLFW_KEY_ESCAPE
                ? router.routeEscape()
                : router.routeKey(new UiKeyEvent(UiKeyEvent.Type.PRESS,
                        keyCode, scanCode, modifiers, '\0'));
        return handled(reply);
    }

    boolean charTyped(char codePoint, int modifiers) {
        synchronizeLayers();
        return handled(router.routeKey(new UiKeyEvent(UiKeyEvent.Type.CHAR_TYPED,
                0, 0, modifiers, codePoint)));
    }

    /** 切屏、断线或退出时清空瞬时输入所有权，绝不泄漏到下一个 Screen。 */
    void clearTransientState() {
        pointerCapture.clear();
        keyboardFocus.clear();
        escapeStack.clear();
    }

    boolean hasPointerCapture(int button) {
        return pointerCapture.isCaptured(button);
    }

    private void synchronizeLayers() {
        windows.sort(Comparator.comparingLong(RtsWindowPanel::getLastClickTime));
        for (RtsWindowPanel window : windows) {
            String id = ids.get(window);
            int border = window.resizable ? window.getResizeBorderWidth() : 0;
            layers.updateBounds(id, new UiRect(
                    window.getWindowX() - border,
                    window.getWindowY() - border,
                    window.getWindowWidth() + border * 2.0D,
                    window.getWindowHeight() + border * 2.0D));
            layers.setVisible(id, window.isVisibleWindow());
            layers.setModal(id, window.isModalWindow());
            layers.bringToFront(id);
            escapeStack.remove(window);
            if (!window.isVisibleWindow()) {
                pointerCapture.releaseOwner(window);
                keyboardFocus.clear(window);
            }
            if (window.isVisibleWindow() && window.closable) {
                escapeStack.push(window);
            }
        }
    }

    private static UiPointerEvent pointer(
            UiPointerEvent.Type type, double x, double y,
            int button, double deltaX, double deltaY) {
        return new UiPointerEvent(type, x, y, button, deltaX, deltaY, 0);
    }

    private static boolean handled(UiEventReply reply) {
        return reply.isHandled() || reply.isBlockWorld();
    }
}
