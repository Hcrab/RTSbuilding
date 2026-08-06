package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowButtonStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowButtonChromeRendererTest {
    @Test
    void preservesLegacyFivePrimitiveGeometryAndOrdering() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect bounds = new UiRect(10, 20, 30, 14);

        int primitives = WindowButtonChromeRenderer.renderSolid(canvas, bounds, false);

        assertEquals(WindowButtonChromeRenderer.PRIMITIVE_COUNT, primitives);
        assertEquals(Arrays.asList(
                bounds,
                new UiRect(10, 20, 31, 1),
                new UiRect(10, 34, 31, 1),
                new UiRect(10, 20, 1, 15),
                new UiRect(40, 20, 1, 15)), canvas.rects);
        assertEquals(WindowButtonStyle.BACKGROUND, canvas.colors.get(0));
        assertEquals(WindowButtonStyle.BORDER_LIGHT, canvas.colors.get(3));
        assertEquals(WindowButtonStyle.BORDER_DARK, canvas.colors.get(4));
    }

    @Test
    void hoverChangesOnlyTheSubmittedBackground() {
        CapturingCanvas canvas = new CapturingCanvas();

        WindowButtonChromeRenderer.renderSolid(canvas, new UiRect(0, 0, 20, 10), true);

        assertEquals(WindowButtonStyle.HOVER_BACKGROUND, canvas.colors.get(0));
        assertEquals(WindowButtonStyle.BORDER_LIGHT, canvas.colors.get(1));
        assertEquals(WindowButtonStyle.BORDER_DARK, canvas.colors.get(2));
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiRect> rects = new ArrayList<UiRect>();
        private final List<UiColor> colors = new ArrayList<UiColor>();

        @Override
        public void fill(UiRect rect, UiColor color) {
            rects.add(rect);
            colors.add(color);
        }

        @Override
        public void fill(double x, double y, double width, double height, UiColor color) {
            fill(new UiRect(x, y, width, height), color);
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
