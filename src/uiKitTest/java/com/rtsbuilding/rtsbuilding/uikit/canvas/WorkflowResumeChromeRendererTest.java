package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowResumeStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorkflowResumeChromeRendererTest {
    @Test
    void placementConflictRendersTwoDividersAndTwoFrames() {
        WorkflowResumeWindowLayout.PlacementGeometry geometry =
                WorkflowResumeWindowLayout.placement(
                        0,
                        0,
                        258,
                        179,
                        true);
        CapturingCanvas canvas = new CapturingCanvas();

        WorkflowResumeChromeRenderer.renderPlacement(
                canvas,
                geometry,
                true,
                geometry.secondaryAction.getX(),
                geometry.secondaryAction.getY());

        assertEquals(12, canvas.rects.size());
        assertEquals(WorkflowResumeStyle.DIVIDER, canvas.colors.get(0));
        assertEquals(WorkflowResumeStyle.DIVIDER, canvas.colors.get(1));
        assertEquals(
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.SKIP,
                        true,
                        false).background,
                canvas.colors.get(2));
        assertEquals(
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.OVERWRITE,
                        true,
                        true).background,
                canvas.colors.get(7));
    }

    @Test
    void blueprintRendersTwoDividersAndOneDisabledFrame() {
        WorkflowResumeWindowLayout.BlueprintGeometry geometry =
                WorkflowResumeWindowLayout.blueprint(
                        0,
                        0,
                        278,
                        219,
                        8);
        CapturingCanvas canvas = new CapturingCanvas();

        WorkflowResumeChromeRenderer.renderBlueprint(
                canvas,
                geometry,
                false,
                geometry.action.getX(),
                geometry.action.getY());

        assertEquals(7, canvas.rects.size());
        assertEquals(
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        false,
                        true).background,
                canvas.colors.get(2));
    }

    @Test
    void missingItemUsesSharedTopAndLeftPlaceholderEdges() {
        UiRect icon = new UiRect(8, 48, 16, 16);
        CapturingCanvas canvas = new CapturingCanvas();

        WorkflowResumeChromeRenderer.renderMissingItem(canvas, icon);

        assertEquals(3, canvas.rects.size());
        assertEquals(icon, canvas.rects.get(0));
        assertEquals(new UiRect(8, 48, 17, 1), canvas.rects.get(1));
        assertEquals(new UiRect(8, 48, 1, 17), canvas.rects.get(2));
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
