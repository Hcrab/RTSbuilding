package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.PlayerStatusStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class PlayerStatusChromeRendererTest {
    @Test
    void fullBarKeepsHistoricalOuterEdgeAndInteriorWidth() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect bounds = new UiRect(10, 20, 130, 10);

        int primitives = PlayerStatusChromeRenderer.renderBar(
                canvas, bounds, 1.0D, PlayerStatusStyle.HEALTH_HIGH);

        assertEquals(6, primitives);
        assertEquals(new UiRect(11, 21, 128, 8), canvas.rects.get(5));
        assertEquals(PlayerStatusStyle.HEALTH_HIGH, canvas.colors.get(5));
    }

    @Test
    void emptyBarSubmitsOnlyTheFrameAndClampsNegativeProgress() {
        CapturingCanvas canvas = new CapturingCanvas();

        int primitives = PlayerStatusChromeRenderer.renderBar(
                canvas, new UiRect(0, 0, 130, 10), -1.0D, PlayerStatusStyle.ARMOR);

        assertEquals(PlayerStatusChromeRenderer.FRAME_PRIMITIVE_COUNT, primitives);
        assertEquals(PlayerStatusChromeRenderer.FRAME_PRIMITIVE_COUNT, canvas.rects.size());
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiRect> rects = new ArrayList<UiRect>();
        private final List<UiColor> colors = new ArrayList<UiColor>();

        @Override public void fill(UiRect rect, UiColor color) {
            rects.add(rect);
            colors.add(color);
        }
        @Override public void fill(double x, double y, double width, double height, UiColor color) {
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
