package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.GuideWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GuideWindowChromeRendererTest {
    @Test
    void topicKeepsLegacyFivePrimitiveGeometryAndOrdering() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect row = new UiRect(10, 20, 30, 18);

        int primitives = GuideWindowChromeRenderer.renderTopic(canvas, row, true);

        assertEquals(GuideWindowChromeRenderer.TOPIC_PRIMITIVE_COUNT, primitives);
        assertEquals(Arrays.asList(
                row,
                new UiRect(10, 20, 31, 1),
                new UiRect(10, 38, 31, 1),
                new UiRect(10, 20, 1, 19),
                new UiRect(40, 20, 1, 19)), canvas.rects);
        assertEquals(GuideWindowStyle.TOPIC_SELECTED_BACKGROUND, canvas.colors.get(0));
        assertEquals(GuideWindowStyle.TOPIC_SELECTED_BORDER_LIGHT, canvas.colors.get(1));
        assertEquals(GuideWindowStyle.TOPIC_BORDER_DARK, canvas.colors.get(4));
    }

    @Test
    void scrollbarClampsScrollAndKeepsExactKnobFormula() {
        CapturingCanvas canvas = new CapturingCanvas();

        int primitives = GuideWindowChromeRenderer.renderScrollbar(
                canvas, new UiRect(100, 20, 3, 100), 3, 10, 4);

        assertEquals(GuideWindowChromeRenderer.SCROLLBAR_PRIMITIVE_COUNT, primitives);
        assertEquals(Arrays.asList(
                new UiRect(100, 20, 3, 100),
                new UiRect(100, 50, 3, 40)), canvas.rects);
        assertEquals(Arrays.asList(
                GuideWindowStyle.SCROLLBAR_TRACK,
                GuideWindowStyle.SCROLLBAR_KNOB), canvas.colors);
    }

    @Test
    void hiddenScrollbarSubmitsNoPrimitives() {
        CapturingCanvas canvas = new CapturingCanvas();
        assertEquals(0, GuideWindowChromeRenderer.renderScrollbar(
                canvas, new UiRect(0, 0, 3, 100), 0, 4, 4));
        assertEquals(0, canvas.rects.size());
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
