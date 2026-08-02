package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiChromeRendererTest {
    @Test
    void frameAlwaysSubmitsNineQuadsAndKeepsLegacyOuterEdge() {
        CapturingCanvas canvas = new CapturingCanvas();
        int quads = UiChromeRenderer.frame(canvas, new UiRect(10, 20, 100, 80), 1,
                new UiColor(0xFF101010), new UiColor(0xFFEEEEEE), new UiColor(0xFF050505));

        assertEquals(UiChromeRenderer.SLICE_COUNT, quads);
        assertEquals(9, canvas.rects.size());
        assertEquals(new UiRect(10, 20, 1, 1), canvas.rects.get(0));
        assertEquals(new UiRect(110, 100, 1, 1), canvas.rects.get(8));
        assertEquals(new UiColor(0xFF101010), canvas.colors.get(4));
        assertEquals(new UiColor(0xFF050505), canvas.colors.get(8));
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
