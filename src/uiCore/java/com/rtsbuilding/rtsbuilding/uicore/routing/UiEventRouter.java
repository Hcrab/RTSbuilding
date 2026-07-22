package com.rtsbuilding.rtsbuilding.uicore.routing;

import com.rtsbuilding.rtsbuilding.uicore.event.UiEventReply;
import com.rtsbuilding.rtsbuilding.uicore.event.UiKeyEvent;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;

import java.util.List;

/**
 * Core 中唯一的事件决策器：捕获所有者、模态层、最上层窗口、焦点和 Escape
 * 都通过同一条路径解析。
 *
 * <p>平台适配器只负责事件翻译和执行返回值，不得在旁边再保留第二套分发器。</p>
 */
public final class UiEventRouter<T extends UiEventTarget> {
    private final UiLayerStack<T> layers;
    private final PointerCapture<T> pointerCapture;
    private final KeyboardFocus<T> keyboardFocus;
    private final UiEscapeStack<T> escapeStack;

    public UiEventRouter(UiLayerStack<T> layers, PointerCapture<T> pointerCapture,
                         KeyboardFocus<T> keyboardFocus, UiEscapeStack<T> escapeStack) {
        if (layers == null || pointerCapture == null || keyboardFocus == null || escapeStack == null) {
            throw new IllegalArgumentException("router collaborators must not be null");
        }
        this.layers = layers;
        this.pointerCapture = pointerCapture;
        this.keyboardFocus = keyboardFocus;
        this.escapeStack = escapeStack;
    }

    public UiEventReply routePointer(UiPointerEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        int button = event.getButton();
        T captured = button >= 0 ? pointerCapture.ownerOf(button) : null;
        if (captured != null && (event.getType() == UiPointerEvent.Type.DRAG
                || event.getType() == UiPointerEvent.Type.RELEASE)) {
            try {
                return captured.handlePointer(event).merge(UiEventReply.BLOCK_WORLD);
            } finally {
                if (event.getType() == UiPointerEvent.Type.RELEASE) {
                    pointerCapture.release(button);
                }
            }
        }

        T target = layers.topmostAt(event.getX(), event.getY());
        if (target == null) {
            return layers.topmostModal() == null ? UiEventReply.PASS : UiEventReply.BLOCK_WORLD;
        }
        UiEventReply reply = target.handlePointer(event);
        if (reply.isCapturePointer()) {
            if (button < 0 || !pointerCapture.capture(button, target)) {
                throw new IllegalStateException("pointer capture request has no available button owner");
            }
        }
        if (reply.isRequestKeyboardFocus()) {
            keyboardFocus.request(target);
        }
        return reply;
    }

    public UiEventReply routeKey(UiKeyEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        T focused = keyboardFocus.getOwner();
        if (focused != null) {
            UiEventReply reply = focused.handleKey(event);
            if (reply.isHandled() || reply.isStopUiPropagation()) return reply;
        }
        T modal = layers.topmostModal();
        if (modal != null && modal != focused) {
            return modal.handleKey(event).merge(UiEventReply.BLOCK_WORLD);
        }
        List<T> ordered = layers.ownersBackToFront();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            T target = ordered.get(i);
            if (target == focused) continue;
            UiEventReply reply = target.handleKey(event);
            if (reply.isHandled() || reply.isStopUiPropagation()) return reply;
        }
        return UiEventReply.PASS;
    }

    public UiEventReply routeEscape() {
        T target = escapeStack.peek();
        if (target == null) return UiEventReply.PASS;
        if (target.handleEscape()) {
            escapeStack.remove(target);
        }
        return UiEventReply.BLOCK_WORLD;
    }
}
