package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BlueprintLibraryChromeRendererTest {
    @Test
    void topBarUsesFiveCompactFramesAndFocusedSearchColor() {
        BlueprintLibraryLayout.Geometry geometry =
                BlueprintLibraryLayout.geometry(
                        0,
                        0,
                        800,
                        120);
        BlueprintLibraryLayout.TopBar top =
                BlueprintLibraryLayout.topBar(
                        0,
                        800,
                        false,
                        40,
                        40,
                        40,
                        40);
        CapturingCanvas canvas = new CapturingCanvas();

        BlueprintLibraryChromeRenderer.renderTopBar(
                canvas,
                geometry,
                top,
                true,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);

        assertEquals(25, canvas.rects.size());
        assertEquals(
                BlueprintLibraryStyle.BUTTON_BACKGROUND,
                canvas.colors.get(0));
        assertEquals(
                BlueprintLibraryStyle.SEARCH_FOCUSED_BACKGROUND,
                canvas.colors.get(20));
    }

    @Test
    void rowSharesCardProgressAndThreeActionFrames() {
        BlueprintLibraryLayout.RowGeometry geometry =
                BlueprintLibraryLayout.rowGeometry(
                        0,
                        20,
                        590,
                        0,
                        0,
                        new BlueprintLibraryLayout.ActionTextWidths(
                                20,
                                20,
                                20));
        CapturingCanvas canvas = new CapturingCanvas();

        BlueprintLibraryChromeRenderer.renderRow(
                canvas,
                geometry,
                entry(73),
                true,
                true,
                geometry.delete.getX(),
                geometry.delete.getY());

        assertEquals(18, canvas.rects.size());
        assertEquals(
                BlueprintLibraryStyle.ROW_SELECTED_BACKGROUND,
                canvas.colors.get(0));
        assertEquals(
                BlueprintLibraryStyle.PROGRESS_TRACK,
                canvas.colors.get(1));
        assertEquals(
                BlueprintLibraryStyle.PROGRESS_PARTIAL,
                canvas.colors.get(2));
        assertEquals(
                Math.floor(
                        geometry.progress.getWidth() * 73.0D / 100.0D),
                canvas.rects.get(2).getWidth());
        assertEquals(
                BlueprintLibraryStyle.BUTTON_HOVER_BACKGROUND,
                canvas.colors.get(13));
    }

    @Test
    void detailsProgressAndPreviewSlotUseSharedGeometry() {
        BlueprintLibraryLayout.Geometry root =
                BlueprintLibraryLayout.geometry(
                        0,
                        0,
                        800,
                        120);
        BlueprintLibraryLayout.DetailsGeometry details =
                BlueprintLibraryLayout.detailsGeometry(root);
        CapturingCanvas canvas = new CapturingCanvas();

        BlueprintLibraryChromeRenderer.renderDetailsProgress(
                canvas,
                details,
                entry(100));
        BlueprintLibraryChromeRenderer.renderPreviewSlot(
                canvas,
                details.previewSlots.get(0));

        assertEquals(3, canvas.rects.size());
        assertEquals(details.progress, canvas.rects.get(0));
        assertEquals(
                BlueprintLibraryStyle.PROGRESS_READY,
                canvas.colors.get(1));
        assertEquals(
                BlueprintLibraryStyle.PREVIEW_SLOT_BACKGROUND,
                canvas.colors.get(2));
    }

    private static BlueprintLibraryUiEntry entry(int percent) {
        return new BlueprintLibraryUiEntry(
                "harbour.nbt",
                "Harbour",
                "NBT",
                "32x18x24",
                4386,
                percent,
                percent + "%",
                "",
                Collections.<String>emptyList());
    }

    private static final class CapturingCanvas
            implements UiCanvas2D {
        private final List<UiRect> rects =
                new ArrayList<UiRect>();
        private final List<UiColor> colors =
                new ArrayList<UiColor>();

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
