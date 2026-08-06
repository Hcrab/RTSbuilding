package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UiOutlineRendererTest {
    @Test
    void 四块轮廓不覆盖按钮内部() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiColor color = new UiColor(0xFFAACCEE);

        assertEquals(4, UiOutlineRenderer.outline(
                canvas, new UiRect(10, 20, 32, 24), color));
        assertEquals(new UiRect(10, 20, 32, 1), canvas.rects.get(0));
        assertEquals(new UiRect(10, 43, 32, 1), canvas.rects.get(1));
        assertEquals(new UiRect(10, 21, 1, 22), canvas.rects.get(2));
        assertEquals(new UiRect(41, 21, 1, 22), canvas.rects.get(3));
    }

    @Test
    void 过小轮廓立即失败() {
        assertThrows(IllegalArgumentException.class, () -> UiOutlineRenderer.outline(
                new CapturingCanvas(), new UiRect(0, 0, 1, 1), new UiColor(0)));
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiRect> rects = new ArrayList<>();

        @Override public void fill(UiRect rect, UiColor color) { rects.add(rect); }
        @Override public void text(String text, double x, double topY, UiColor color) { }
        @Override public void pushClip(UiRect clip) { }
        @Override public void popClip() { }
        @Override public void pushTransform() { }
        @Override public void popTransform() { }
        @Override public void translate(double x, double y) { }
        @Override public void scale(double x, double y) { }
    }
}
