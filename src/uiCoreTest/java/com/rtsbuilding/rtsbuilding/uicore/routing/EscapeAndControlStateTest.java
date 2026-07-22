package com.rtsbuilding.rtsbuilding.uicore.routing;

import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class EscapeAndControlStateTest {
    @Test
    void escape一次只弹出最上层() {
        UiEscapeStack<String> stack = new UiEscapeStack<String>();
        stack.push("window");
        stack.push("dialog");
        assertEquals("dialog", stack.popTop());
        assertEquals("window", stack.peek());
    }

    @Test
    void 重复push会把同一所有者移到顶部而非复制() {
        UiEscapeStack<String> stack = new UiEscapeStack<String>();
        stack.push("a");
        stack.push("b");
        stack.push("a");
        assertEquals(Arrays.asList("b", "a"), stack.snapshotBottomToTop());
    }

    @Test
    void 空栈escape安全返回null() {
        assertNull(new UiEscapeStack<Object>().popTop());
    }

    @Test
    void 启用控件不能携带禁用原因() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiControlState(true, false, false, false, "locked"));
    }

    @Test
    void 禁用控件必须解释原因() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiControlState(false, false, false, false, ""));
    }

    @Test
    void pending与failed互斥() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiControlState(true, false, true, true, ""));
    }

    @Test
    void 选中状态可以在禁用时保留() {
        UiControlState state = new UiControlState(false, true, false, false, "需要蓝图插件");
        assertTrue(state.isSelected());
        assertFalse(state.isEnabled());
        assertEquals("需要蓝图插件", state.getDisabledReason());
    }
}
