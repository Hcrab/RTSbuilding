package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.CullingWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CullingWindowChromeRendererTest {
    @Test
    void submitsTheSharedCompactDangerFrame() {
        CapturingCanvas canvas = new CapturingCanvas();

        int primitives = CullingWindowChromeRenderer.renderDeleteButton(
                canvas, new UiRect(3, 5, 80, 20), true);

        assertEquals(UiCompactFrameRenderer.PRIMITIVE_COUNT, primitives);
        assertEquals(UiCompactFrameRenderer.PRIMITIVE_COUNT, canvas.colors.size());
        assertEquals(CullingWindowStyle.DELETE_HOVER_BACKGROUND, canvas.colors.get(0));
        assertEquals(CullingWindowStyle.DELETE_HOVER_BORDER, canvas.colors.get(1));
        assertEquals(CullingWindowStyle.DELETE_DARK_BORDER, canvas.colors.get(4));
    }

    private static final class CapturingCanvas implements UiCanvas2D {
        private final List<UiColor> colors = new ArrayList<UiColor>();

        @Override public void fill(UiRect rect, UiColor color) { colors.add(color); }
        @Override public void fill(double x, double y, double width, double height, UiColor color) {
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
