package com.rtsbuilding.rtsbuilding.uicore.routing;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class UiLayerStackTest {
    @Test
    void 后注册窗口默认在最上层() {
        UiLayerStack<String> stack = overlappingStack();
        assertEquals("front", stack.topmostAt(5, 5));
    }

    @Test
    void bringToFront改变唯一命中者() {
        UiLayerStack<String> stack = overlappingStack();
        stack.bringToFront("back");
        assertEquals("back", stack.topmostAt(5, 5));
    }

    @Test
    void 隐藏窗口不参与命中() {
        UiLayerStack<String> stack = overlappingStack();
        stack.setVisible("front", false);
        assertEquals("back", stack.topmostAt(5, 5));
    }

    @Test
    void 模态窗口阻断其外部的底层窗口() {
        UiLayerStack<String> stack = overlappingStack();
        stack.register("modal", "modal", new UiRect(20, 20, 10, 10), true, true);
        assertNull(stack.topmostAt(5, 5));
        assertEquals("modal", stack.topmostModal());
    }

    @Test
    void 模态窗口内部仍可唯一命中() {
        UiLayerStack<String> stack = overlappingStack();
        stack.register("modal", "modal", new UiRect(2, 2, 5, 5), true, true);
        assertEquals("modal", stack.topmostAt(3, 3));
    }

    @Test
    void bounds更新后旧位置不再命中() {
        UiLayerStack<String> stack = new UiLayerStack<String>();
        stack.register("window", "window", new UiRect(0, 0, 10, 10), false, true);
        stack.updateBounds("window", new UiRect(50, 50, 10, 10));
        assertNull(stack.topmostAt(5, 5));
        assertEquals("window", stack.topmostAt(55, 55));
    }

    @Test
    void 重复稳定id立即失败() {
        UiLayerStack<String> stack = new UiLayerStack<String>();
        stack.register("same", "one", new UiRect(0, 0, 1, 1), false, true);
        assertThrows(IllegalArgumentException.class,
                () -> stack.register("same", "two", new UiRect(0, 0, 1, 1), false, true));
    }

    @Test
    void 渲染顺序从后到前确定() {
        UiLayerStack<String> stack = overlappingStack();
        assertEquals(Arrays.asList("back", "front"), stack.ownersBackToFront());
    }

    private static UiLayerStack<String> overlappingStack() {
        UiLayerStack<String> stack = new UiLayerStack<String>();
        stack.register("back", "back", new UiRect(0, 0, 10, 10), false, true);
        stack.register("front", "front", new UiRect(0, 0, 10, 10), false, true);
        return stack;
    }
}
