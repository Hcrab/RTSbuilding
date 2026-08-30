package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowSliderLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.WindowTextBoxLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowSliderStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.WindowTextBoxStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WindowWidgetChromeRendererTest {
    @Test
    void textBoxUsesBackgroundThenFourInnerBorders() {
        CapturingCanvas canvas = new CapturingCanvas();
        WindowTextBoxLayout.Geometry geometry = WindowTextBoxLayout.geometry(
                new UiRect(10, 20, 100, 14), 9, 30, false, false);

        assertEquals(WindowTextBoxChromeRenderer.PRIMITIVE_COUNT,
                WindowTextBoxChromeRenderer.render(canvas, geometry, true));
        assertEquals(Arrays.asList(
                geometry.bounds, geometry.topBorder, geometry.bottomBorder,
                geometry.leftBorder, geometry.rightBorder), canvas.rects);
        assertEquals(WindowTextBoxStyle.BACKGROUND, canvas.colors.get(0));
        assertEquals(WindowTextBoxStyle.BORDER_FOCUSED, canvas.colors.get(4));
    }

    @Test
    void sliderUsesSharedTrackFillAndKnobGeometry() {
        CapturingCanvas canvas = new CapturingCanvas();
        WindowSliderLayout.Geometry geometry = WindowSliderLayout.geometry(
                new UiRect(10, 20, 100, 18), 1, 256, 129);

        assertEquals(WindowSliderChromeRenderer.PRIMITIVE_COUNT,
                WindowSliderChromeRenderer.render(canvas, geometry));
        assertEquals(Arrays.asList(geometry.track, geometry.trackFill, geometry.knob),
                canvas.rects);
        assertEquals(Arrays.asList(
                WindowSliderStyle.TRACK_BACKGROUND,
                WindowSliderStyle.TRACK_FILL,
                WindowSliderStyle.KNOB), canvas.colors);
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
