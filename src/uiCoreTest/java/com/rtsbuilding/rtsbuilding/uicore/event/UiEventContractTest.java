package com.rtsbuilding.rtsbuilding.uicore.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiEventContractTest {
    @Test
    void pass不处理也不阻断世界() {
        assertFalse(UiEventReply.PASS.isHandled());
        assertFalse(UiEventReply.PASS.isBlockWorld());
    }

    @Test
    void 捕获指针同时阻断世界() {
        assertTrue(UiEventReply.CAPTURE_POINTER.isCapturePointer());
        assertTrue(UiEventReply.CAPTURE_POINTER.isBlockWorld());
    }

    @Test
    void 文本焦点不等于指针捕获() {
        assertTrue(UiEventReply.FOCUS_TEXT.isRequestKeyboardFocus());
        assertFalse(UiEventReply.FOCUS_TEXT.isCapturePointer());
    }

    @Test
    void 未处理事件不能请求焦点() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiEventReply(false, false, false, false, true));
    }

    @Test
    void 合并结果只增加约束() {
        UiEventReply merged = UiEventReply.CONSUMED.merge(UiEventReply.BLOCK_WORLD);
        assertTrue(merged.isHandled());
        assertTrue(merged.isStopUiPropagation());
        assertTrue(merged.isBlockWorld());
    }

    @Test
    void 指针事件保留滚轮增量() {
        UiPointerEvent event = new UiPointerEvent(UiPointerEvent.Type.SCROLL,
                12, 18, UiPointerEvent.NO_BUTTON, -1, 2, 4);
        assertEquals(-1, event.getDeltaX());
        assertEquals(2, event.getDeltaY());
        assertEquals(4, event.getModifiers());
    }

    @Test
    void 键盘事件保留字符输入() {
        UiKeyEvent event = new UiKeyEvent(UiKeyEvent.Type.CHAR_TYPED, 0, 0, 2, '界');
        assertEquals('界', event.getCharacter());
        assertEquals(UiKeyEvent.Type.CHAR_TYPED, event.getType());
    }
}
