package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UiBevelOutlineRendererTest {
    @Test
    void 四边保持原版线段的包含末端外边界与覆盖顺序() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiColor light = new UiColor(0xFFEEEEEE);
        UiColor dark = new UiColor(0xFF050505);

        int primitives = UiBevelOutlineRenderer.outline(
                canvas, new UiRect(10, 20, 100, 5), light, dark);

        assertEquals(4, primitives);
        assertEquals(new UiRect(10, 20, 101, 1), canvas.rects.get(0));
        assertEquals(new UiRect(10, 25, 101, 1), canvas.rects.get(1));
        assertEquals(new UiRect(10, 20, 1, 6), canvas.rects.get(2));
        assertEquals(new UiRect(110, 20, 1, 6), canvas.rects.get(3));
        assertEquals(light, canvas.colors.get(2));
        assertEquals(dark, canvas.colors.get(3));
    }

    @Test
    void 空轮廓立即失败() {
        assertThrows(IllegalArgumentException.class, () ->
                UiBevelOutlineRenderer.outline(
                        new CapturingCanvas(), new UiRect(0, 0, 0, 1),
                        new UiColor(0), new UiColor(0)));
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiRect> rects = new ArrayList<UiRect>();
        private final List<UiColor> colors = new ArrayList<UiColor>();

        @Override
        public void fill(UiRect rect, UiColor color) {
            rects.add(rect);
            colors.add(color);
        }

        @Override public void text(String text, double x, double topY, UiColor color) { }
        @Override public void pushClip(UiRect clip) { }
        @Override public void popClip() { }
        @Override public void pushTransform() { }
        @Override public void popTransform() { }
        @Override public void translate(double x, double y) { }
        @Override public void scale(double x, double y) { }
    }
}
