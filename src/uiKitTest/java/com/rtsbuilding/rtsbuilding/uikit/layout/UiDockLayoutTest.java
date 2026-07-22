package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiInsets;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiDockLayoutTest {
    @Test
    void 四边预留得到中央内容区() {
        UiDockLayout layout = new UiDockLayout(new UiRect(0, 0, 200, 120),
                new UiInsets(20, 10, 30, 15));
        assertEquals(new UiRect(20, 10, 150, 95), layout.getContent());
        assertEquals(new UiRect(0, 0, 200, 10), layout.top());
        assertEquals(new UiRect(0, 105, 200, 15), layout.bottom());
        assertEquals(new UiRect(0, 10, 20, 95), layout.left());
        assertEquals(new UiRect(170, 10, 30, 95), layout.right());
    }

    @Test
    void 超额预留不会产生负内容尺寸() {
        UiDockLayout layout = new UiDockLayout(new UiRect(5, 6, 100, 50),
                new UiInsets(80, 40, 80, 40));
        assertEquals(new UiRect(85, 46, 0, 0), layout.getContent());
        assertEquals(100, layout.getApplied().horizontal(), 0.0001);
        assertEquals(50, layout.getApplied().vertical(), 0.0001);
    }

    @Test
    void 所有预留矩形保持在屏幕内() {
        UiDockLayout layout = new UiDockLayout(new UiRect(10, 20, 300, 180),
                new UiInsets(25, 30, 35, 40));
        assertTrue(layout.getScreen().contains(layout.top()));
        assertTrue(layout.getScreen().contains(layout.bottom()));
        assertTrue(layout.getScreen().contains(layout.left()));
        assertTrue(layout.getScreen().contains(layout.right()));
        assertTrue(layout.getScreen().contains(layout.getContent()));
    }

    @Test
    void 小数缩放的右下边缘不会因累计误差越屏() {
        UiDockLayout layout = new UiDockLayout(new UiRect(0, 0, 1024, 768),
                new UiInsets(0, 39.2, 58.8, 145.6));
        assertTrue(layout.getScreen().contains(layout.bottom()));
        assertTrue(layout.getScreen().contains(layout.right()));
    }

    @Test
    void 拒绝空屏幕和空预留() {
        assertThrows(IllegalArgumentException.class,
                () -> new UiDockLayout(UiRect.EMPTY, UiInsets.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new UiDockLayout(new UiRect(0, 0, 10, 10), null));
    }
}
