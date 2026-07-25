package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlueprintWindowChromeRendererTest {
    @Test
    void sectionAndStatusUseSharedSemanticChrome() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect section = new UiRect(4, 5, 80, 40);
        UiRect status = new UiRect(4, 50, 80, 34);

        BlueprintWindowChromeRenderer.renderSection(canvas, section);
        BlueprintWindowChromeRenderer.renderStatus(canvas, status);

        assertEquals(5, canvas.rects.size());
        assertEquals(BlueprintWindowStyle.SECTION_BACKGROUND, canvas.colors.get(0));
        assertEquals(BlueprintWindowStyle.STATUS_BACKGROUND, canvas.colors.get(3));
        assertEquals(status, canvas.rects.get(3));
    }

    @Test
    void primaryActionPreservesOutsideOnePixelOutline() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect bounds = new UiRect(10, 20, 100, 20);

        BlueprintWindowChromeRenderer.renderPrimaryAction(canvas, bounds);

        assertEquals(5, canvas.rects.size());
        assertEquals(bounds, canvas.rects.get(0));
        assertEquals(new UiRect(9, 19, 102, 1), canvas.rects.get(1));
        assertEquals(BlueprintWindowStyle.PRIMARY_ACTION_BORDER, canvas.colors.get(4));
    }

    @Test
    void fieldsAndDisabledOverlayUseSharedTokens() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect bounds = new UiRect(0, 0, 64, 20);

        BlueprintWindowChromeRenderer.renderField(canvas, bounds, false);
        BlueprintWindowChromeRenderer.renderDisabledFieldOverlay(canvas, bounds);

        assertEquals(UiCompactFrameRenderer.PRIMITIVE_COUNT + 1, canvas.rects.size());
        assertEquals(BlueprintWindowStyle.FIELD_DISABLED_BACKGROUND, canvas.colors.get(0));
        assertEquals(BlueprintWindowStyle.DISABLED_FIELD_OVERLAY,
                canvas.colors.get(canvas.colors.size() - 1));
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
