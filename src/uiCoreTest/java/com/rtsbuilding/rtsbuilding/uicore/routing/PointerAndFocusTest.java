package com.rtsbuilding.rtsbuilding.uicore.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PointerAndFocusTest {
    @Test
    void 同一按钮只能有一个捕获者() {
        PointerCapture<Object> capture = new PointerCapture<Object>();
        Object first = new Object();
        assertTrue(capture.capture(0, first));
        assertFalse(capture.capture(0, new Object()));
        assertSame(first, capture.ownerOf(0));
    }

    @Test
    void 同一所有者可重复确认捕获() {
        PointerCapture<Object> capture = new PointerCapture<Object>();
        Object owner = new Object();
        assertTrue(capture.capture(0, owner));
        assertTrue(capture.capture(0, owner));
    }

    @Test
    void 多鼠标键独立捕获() {
        PointerCapture<String> capture = new PointerCapture<String>();
        capture.capture(0, "left");
        capture.capture(1, "right");
        assertEquals("left", capture.ownerOf(0));
        assertEquals("right", capture.ownerOf(1));
    }

    @Test
    void release只释放对应按钮() {
        PointerCapture<String> capture = new PointerCapture<String>();
        capture.capture(0, "window");
        capture.capture(1, "window");
        assertEquals("window", capture.release(0));
        assertTrue(capture.isCaptured(1));
    }

    @Test
    void 可按所有者一次释放多个按钮() {
        PointerCapture<Object> capture = new PointerCapture<Object>();
        Object owner = new Object();
        capture.capture(0, owner);
        capture.capture(1, owner);
        assertEquals(2, capture.releaseOwner(owner));
        assertTrue(capture.snapshot().isEmpty());
    }

    @Test
    void 焦点与捕获完全分离() {
        KeyboardFocus<String> focus = new KeyboardFocus<String>();
        PointerCapture<String> capture = new PointerCapture<String>();
        focus.request("search");
        capture.capture(0, "window");
        assertEquals("search", focus.getOwner());
        assertEquals("window", capture.ownerOf(0));
    }

    @Test
    void 错误所有者不能清除焦点() {
        KeyboardFocus<String> focus = new KeyboardFocus<String>();
        focus.request("search");
        assertFalse(focus.clear("other"));
        assertEquals("search", focus.getOwner());
    }
}
