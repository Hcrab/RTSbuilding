package com.rtsbuilding.rtsbuilding.uicore.routing;

import com.rtsbuilding.rtsbuilding.uicore.event.UiEventReply;
import com.rtsbuilding.rtsbuilding.uicore.event.UiKeyEvent;
import com.rtsbuilding.rtsbuilding.uicore.event.UiPointerEvent;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiEventRouterTest {
    @Test
    void press请求捕获后drag和release只回原目标() {
        Fixture fixture = new Fixture();
        fixture.front.pointerReply = UiEventReply.CAPTURE_POINTER;
        fixture.router.routePointer(pointer(UiPointerEvent.Type.PRESS, 10, 10, 0));
        fixture.router.routePointer(pointer(UiPointerEvent.Type.DRAG, 500, 500, 0));
        fixture.router.routePointer(pointer(UiPointerEvent.Type.RELEASE, 500, 500, 0));
        assertEquals(3, fixture.front.pointerCalls);
        assertEquals(0, fixture.back.pointerCalls);
        assertFalse(fixture.capture.isCaptured(0));
    }

    @Test
    void 捕获目标返回pass仍阻断世界() {
        Fixture fixture = new Fixture();
        fixture.front.pointerReply = UiEventReply.CAPTURE_POINTER;
        fixture.router.routePointer(pointer(UiPointerEvent.Type.PRESS, 10, 10, 0));
        fixture.front.pointerReply = UiEventReply.PASS;
        assertTrue(fixture.router.routePointer(pointer(UiPointerEvent.Type.DRAG, 500, 500, 0)).isBlockWorld());
    }

    @Test
    void 模态层外部点击阻断底层和世界() {
        Fixture fixture = new Fixture(true);
        UiEventReply reply = fixture.router.routePointer(pointer(UiPointerEvent.Type.PRESS, 150, 150, 0));
        assertTrue(reply.isBlockWorld());
        assertEquals(0, fixture.back.pointerCalls);
    }

    @Test
    void 没有UI目标时事件通过() {
        Fixture fixture = new Fixture();
        assertSame(UiEventReply.PASS,
                fixture.router.routePointer(pointer(UiPointerEvent.Type.PRESS, 500, 500, 0)));
    }

    @Test
    void 文本目标可独立请求键盘焦点() {
        Fixture fixture = new Fixture();
        fixture.front.pointerReply = UiEventReply.FOCUS_TEXT;
        fixture.router.routePointer(pointer(UiPointerEvent.Type.PRESS, 10, 10, 0));
        assertSame(fixture.front, fixture.focus.getOwner());
        assertFalse(fixture.capture.isCaptured(0));
    }

    @Test
    void 键盘优先发送给焦点所有者() {
        Fixture fixture = new Fixture();
        fixture.focus.request(fixture.back);
        fixture.back.keyReply = UiEventReply.CONSUMED;
        fixture.router.routeKey(new UiKeyEvent(UiKeyEvent.Type.PRESS, 65, 0, 0, '\0'));
        assertEquals(1, fixture.back.keyCalls);
        assertEquals(0, fixture.front.keyCalls);
    }

    @Test
    void 焦点pass后模态层接管键盘() {
        Fixture fixture = new Fixture(true);
        fixture.focus.request(fixture.back);
        fixture.back.keyReply = UiEventReply.PASS;
        fixture.front.keyReply = UiEventReply.CONSUMED;
        UiEventReply reply = fixture.router.routeKey(
                new UiKeyEvent(UiKeyEvent.Type.PRESS, 65, 0, 0, '\0'));
        assertEquals(1, fixture.back.keyCalls);
        assertEquals(1, fixture.front.keyCalls);
        assertTrue(reply.isBlockWorld());
    }

    @Test
    void escape一次只调用栈顶() {
        Fixture fixture = new Fixture();
        fixture.escape.push(fixture.back);
        fixture.escape.push(fixture.front);
        fixture.router.routeEscape();
        assertEquals(1, fixture.front.escapeCalls);
        assertEquals(0, fixture.back.escapeCalls);
        assertSame(fixture.back, fixture.escape.peek());
    }

    @Test
    void 未关闭的escape目标保留在栈顶() {
        Fixture fixture = new Fixture();
        fixture.front.escapeHandled = false;
        fixture.escape.push(fixture.front);
        assertTrue(fixture.router.routeEscape().isBlockWorld());
        assertSame(fixture.front, fixture.escape.peek());
    }

    private static UiPointerEvent pointer(UiPointerEvent.Type type, double x, double y, int button) {
        return new UiPointerEvent(type, x, y, button, 0, 0, 0);
    }

    private static final class Fixture {
        final Target back = new Target();
        final Target front = new Target();
        final UiLayerStack<Target> layers = new UiLayerStack<Target>();
        final PointerCapture<Target> capture = new PointerCapture<Target>();
        final KeyboardFocus<Target> focus = new KeyboardFocus<Target>();
        final UiEscapeStack<Target> escape = new UiEscapeStack<Target>();
        final UiEventRouter<Target> router;

        Fixture() {
            this(false);
        }

        Fixture(boolean modalFront) {
            layers.register("back", back, new UiRect(0, 0, 200, 200), false, true);
            layers.register("front", front, new UiRect(0, 0, 100, 100), modalFront, true);
            router = new UiEventRouter<Target>(layers, capture, focus, escape);
        }
    }

    private static final class Target implements UiEventTarget {
        UiEventReply pointerReply = UiEventReply.CONSUMED;
        UiEventReply keyReply = UiEventReply.PASS;
        boolean escapeHandled = true;
        int pointerCalls;
        int keyCalls;
        int escapeCalls;

        @Override
        public UiEventReply handlePointer(UiPointerEvent event) {
            pointerCalls++;
            return pointerReply;
        }

        @Override
        public UiEventReply handleKey(UiKeyEvent event) {
            keyCalls++;
            return keyReply;
        }

        @Override
        public boolean handleEscape() {
            escapeCalls++;
            return escapeHandled;
        }
    }
}
