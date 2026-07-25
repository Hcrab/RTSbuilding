package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.FunnelBufferStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FunnelBufferChromeRendererTest {
    @Test
    void togglePanelAndHoveredRowUseSharedSemanticPrimitives() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect toggle = new UiRect(0, 0, 60, 16);
        UiRect panel = new UiRect(0, 20, 132, 196);
        UiRect row = new UiRect(4, 36, 124, 20);
        UiRect slot = new UiRect(6, 38, 18, 18);

        FunnelBufferChromeRenderer.renderToggle(canvas, toggle, true);
        FunnelBufferChromeRenderer.renderPanel(canvas, panel);
        int rowPrimitives = FunnelBufferChromeRenderer.renderRow(canvas, row, slot, true);

        assertEquals(3, rowPrimitives);
        assertEquals(5, canvas.rects.size());
        assertEquals(FunnelBufferStyle.TOGGLE_VISIBLE, canvas.colors.get(0));
        assertEquals(FunnelBufferStyle.PANEL_BACKGROUND, canvas.colors.get(1));
        assertEquals(FunnelBufferStyle.ROW_HOVER_OVERLAY, canvas.colors.get(4));
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
