package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.StorageWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class StorageWindowChromeRendererTest {
    @Test
    void rowUsesFourCompactFramesAndCanYieldPriorityToEditBox() {
        StorageWindowLayout.RowGeometry row =
                geometry(1, 1, 0).rows.get(0);
        CapturingCanvas normal = new CapturingCanvas();
        CapturingCanvas editing = new CapturingCanvas();

        StorageWindowChromeRenderer.renderRow(
                normal,
                row,
                entry(true),
                false);
        StorageWindowChromeRenderer.renderRow(
                editing,
                row,
                entry(true),
                true);

        assertEquals(20, normal.rects.size());
        assertEquals(15, editing.rects.size());
        assertEquals(
                StorageWindowStyle.ROW_BACKGROUND,
                normal.colors.get(0));
        assertEquals(
                StorageWindowStyle.PRIORITY_BACKGROUND,
                normal.colors.get(5));
        assertEquals(
                StorageWindowStyle.EXTRACT_ACTIVE_BACKGROUND,
                normal.colors.get(10));
        assertEquals(
                StorageWindowStyle.UNLINK_BACKGROUND,
                normal.colors.get(15));
    }

    @Test
    void hoverAndScrollbarUseSharedSemanticColors() {
        StorageWindowLayout.Geometry geometry =
                geometry(4, 2000, 1996);
        CapturingCanvas rowCanvas = new CapturingCanvas();
        StorageWindowLayout.RowGeometry row = geometry.rows.get(0);
        StorageWindowChromeRenderer.renderRow(
                rowCanvas,
                row,
                entry(false),
                false,
                row.unlink.getX(),
                row.unlink.getY());

        assertEquals(
                StorageWindowStyle.UNLINK_HOVER_BACKGROUND,
                rowCanvas.colors.get(15));

        CapturingCanvas scrollbar = new CapturingCanvas();
        StorageWindowChromeRenderer.renderScrollbar(
                scrollbar,
                geometry);
        assertEquals(3, scrollbar.rects.size());
        assertEquals(
                geometry.scrollbarTrack,
                scrollbar.rects.get(0));
        assertEquals(
                geometry.scrollbarInset,
                scrollbar.rects.get(1));
        assertEquals(
                geometry.scrollbarThumb,
                scrollbar.rects.get(2));
    }

    @Test
    void missingItemPlaceholderKeepsLegacyTopAndLeftEdge() {
        CapturingCanvas canvas = new CapturingCanvas();
        UiRect icon = new UiRect(13, 39, 16, 16);

        StorageWindowChromeRenderer.renderMissingIcon(canvas, icon);

        assertEquals(3, canvas.rects.size());
        assertEquals(icon, canvas.rects.get(0));
        assertEquals(new UiRect(13, 39, 17, 1),
                canvas.rects.get(1));
        assertEquals(new UiRect(13, 39, 1, 17),
                canvas.rects.get(2));
    }

    private static StorageWindowLayout.Geometry geometry(
            int visible,
            int total,
            int scroll) {
        return StorageWindowLayout.geometry(
                0,
                0,
                388,
                189,
                visible,
                total,
                scroll);
    }

    private static StorageUiEntry entry(boolean extractOnly) {
        return new StorageUiEntry(
                "12,64,9",
                "Chest",
                "12, 64, 9",
                2,
                extractOnly,
                true,
                "minecraft:chest");
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
