package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.QuickBuildWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.QuickBuildStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuickBuildChromeRendererTest {
    @Test
    void 模式按钮共享外框内框和动画叠色边界() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect area = new UiRect(8, 25, 79, 18);
        QuickBuildStyle.ModeVisual visual =
                QuickBuildStyle.mode(true, true, false);

        QuickBuildChromeRenderer.renderMode(canvas, area, visual, 1.0D);

        assertEquals(3, canvas.rects.size());
        assertEquals(area, canvas.rects.get(0));
        assertEquals(new UiRect(9, 26, 77, 16), canvas.rects.get(1));
        assertEquals(new UiRect(9, 26, 77, 16), canvas.rects.get(2));
        assertEquals(visual.border, canvas.colors.get(0));
        assertEquals(visual.background, canvas.colors.get(1));
        assertEquals(QuickBuildStyle.MODE_ANIMATION_OVERLAY, canvas.colors.get(2));
    }

    @Test
    void 进度取整钳制和空闲刻度与生产历史行为一致() {
        assertEquals(81, QuickBuildChromeRenderer.progressFillWidth(162, 1, 2));
        assertEquals(54, QuickBuildChromeRenderer.progressFillWidth(162, 1, 3));
        assertEquals(162, QuickBuildChromeRenderer.progressFillWidth(162, 4, 3));
        assertEquals(0, QuickBuildChromeRenderer.progressFillWidth(162, -1, 3));

        CapturingCanvas idle = new CapturingCanvas();
        QuickBuildWindowLayout.Geometry layout =
                QuickBuildWindowLayout.geometry(100, 50, false);
        QuickBuildChromeRenderer.renderStatus(idle, layout, -1, 0);

        assertEquals(3, idle.rects.size());
        assertEquals(layout.divider, idle.rects.get(0));
        assertEquals(layout.progress, idle.rects.get(1));
        assertEquals(new UiRect(
                layout.progress.getX(),
                layout.progress.getY(),
                1,
                layout.progress.getHeight()), idle.rects.get(2));
        assertEquals(QuickBuildStyle.PROGRESS_IDLE_TICK, idle.colors.get(2));
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
