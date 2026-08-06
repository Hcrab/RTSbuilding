package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.workflow.WorkflowUiRow;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorkflowChromeRendererTest {
    @Test
    void progressFillUsesCompletedOnlyAndClampsToTrack() {
        assertEquals(49,
                WorkflowChromeRenderer.progressFillWidth(146, 1, 3));
        assertEquals(146,
                WorkflowChromeRenderer.progressFillWidth(146, 5, 3));
        assertEquals(0,
                WorkflowChromeRenderer.progressFillWidth(146, 0, 3));
        assertEquals(0,
                WorkflowChromeRenderer.progressFillWidth(146, 3, 0));
    }

    @Test
    void rowProgressAndThreeButtonsShareOneChromePath() {
        CapturingCanvas canvas = new CapturingCanvas();
        WorkflowWindowLayout.RowGeometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 1).rows.get(0);

        WorkflowChromeRenderer.renderRow(
                canvas,
                geometry,
                row(false, false, false, 1, 3));

        assertEquals(26, canvas.rects.size());
        assertEquals(geometry.row, canvas.rects.get(0));
        assertEquals(
                WorkflowStyle.ACTIVE_BACKGROUND,
                canvas.colors.get(0));
        assertEquals(geometry.progress, canvas.rects.get(5));
        assertEquals(new UiRect(14, 42, 49, 6),
                canvas.rects.get(6));
        assertEquals(
                WorkflowStyle.ACTIVE_PROGRESS_FILL,
                canvas.colors.get(6));
        assertEquals(geometry.protect, canvas.rects.get(11));
        assertEquals(geometry.action, canvas.rects.get(16));
        assertEquals(geometry.delete, canvas.rects.get(21));
    }

    @Test
    void pointerChangesOnlyTheHoveredActionSurface() {
        CapturingCanvas canvas = new CapturingCanvas();
        WorkflowWindowLayout.RowGeometry geometry =
                WorkflowWindowLayout.geometry(10, 30, 1).rows.get(0);

        WorkflowChromeRenderer.renderRow(
                canvas,
                geometry,
                row(false, false, false, 0, 3),
                166,
                30);

        // 无进度填充时 protect 框从第 10 个 primitive 开始。
        assertEquals(
                WorkflowStyle.PROTECT_IDLE_HOVER_BACKGROUND,
                canvas.colors.get(10));
    }

    private static WorkflowUiRow row(
            boolean suspended,
            boolean paused,
            boolean protectedWorkflow,
            int completed,
            int total) {
        return new WorkflowUiRow(
                7,
                "place",
                "Bridge",
                completed + "/" + total,
                completed,
                total,
                2,
                Math.max(0, total - completed),
                suspended,
                paused,
                protectedWorkflow,
                false);
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
        public void fill(
                double x,
                double y,
                double width,
                double height,
                UiColor color) {
            fill(new UiRect(x, y, width, height), color);
        }

        @Override
        public void text(
                String text,
                double x,
                double topY,
                UiColor color) {
        }

        @Override public void pushClip(UiRect clip) { }
        @Override public void popClip() { }
        @Override public void pushTransform() { }
        @Override public void popTransform() { }
        @Override public void translate(double x, double y) { }
        @Override public void scale(double x, double y) { }
    }
}
