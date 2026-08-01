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
 * 浮窗层到 Core 权威事件路由器的窄生产适配器。
 *
 * <p>本类只翻译 Minecraft 输入、同步窗口 bounds/z-order/可见性，并把 press、drag、
 * release、scroll、key、char 和 Escape 交给同一个 {@link UiEventRouter}。它不渲染窗口、
 * 不执行业务 Action，也不维护第二份指针捕获规则。</p>
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
        return handled(router.routePointer(pointer(UiPointerEvent.Type.PRESS, x, y, button, 0, 0)));
    }

    boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(UiPointerEvent.Type.DRAG, x, y, button, dragX, dragY)));
    }

    boolean mouseReleased(double x, double y, int button) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(UiPointerEvent.Type.RELEASE, x, y, button, 0, 0)));
    }

    boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        synchronizeLayers();
        return handled(router.routePointer(pointer(UiPointerEvent.Type.SCROLL, x, y,
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

    /** 切屏、断线或退出时一次清理捕获/焦点/Escape，不保留跨 Screen 的瞬时所有权。 */
    void clearTransientState() {
        pointerCapture.clear();
        keyboardFocus.clear();
        escapeStack.clear();
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

    private static UiPointerEvent pointer(UiPointerEvent.Type type, double x, double y,
                                          int button, double deltaX, double deltaY) {
        return new UiPointerEvent(type, x, y, button, deltaX, deltaY, 0);
    }

    private static boolean handled(UiEventReply reply) {
        return reply.isHandled() || reply.isBlockWorld();
    }
}
