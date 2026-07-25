package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class UiCompactFrameRendererTest {
    @Test
    void 五块框体保持旧外边界和明确角落归属() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiColor center = new UiColor(0xFF101010);
        UiColor light = new UiColor(0xFFEEEEEE);
        UiColor dark = new UiColor(0xFF050505);

        int primitives = UiCompactFrameRenderer.frame(canvas,
                new UiRect(10, 20, 100, 80), center, light, dark);

        assertEquals(5, primitives);
        assertEquals(new UiRect(11, 21, 99, 79), canvas.rects.get(0));
        assertEquals(new UiRect(10, 20, 100, 1), canvas.rects.get(1));
        assertEquals(new UiRect(10, 100, 101, 1), canvas.rects.get(3));
        assertEquals(new UiRect(110, 20, 1, 80), canvas.rects.get(4));
        assertEquals(dark, canvas.colors.get(4));
    }

    @Test
    void 过小框体立即失败() {
        assertThrows(IllegalArgumentException.class, () -> UiCompactFrameRenderer.frame(
                new CapturingCanvas(), new UiRect(0, 0, 1, 10),
                new UiColor(0), new UiColor(0), new UiColor(0)));
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
