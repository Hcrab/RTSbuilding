package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uicore.storage.StorageUiEntry;
import com.rtsbuilding.rtsbuilding.uikit.layout.StorageWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.StorageWindowStyle;

/**
 * 绑定储存行、三枚控件、占位图标与滚动条的纯 Canvas chrome。
 *
 * <p>本类不绘制真实 ItemStack、文本或 EditBox，也不派发优先级、仅提取和解绑命令。</p>
 */
public final class StorageWindowChromeRenderer {
    private StorageWindowChromeRenderer() {
    }

    public static void renderRow(
            UiCanvas2D canvas,
            StorageWindowLayout.RowGeometry geometry,
            StorageUiEntry entry,
            boolean priorityEditing) {
        renderRow(
                canvas,
                geometry,
                entry,
                priorityEditing,
                Double.NEGATIVE_INFINITY,
                Double.NEGATIVE_INFINITY);
    }

    public static void renderRow(
            UiCanvas2D canvas,
            StorageWindowLayout.RowGeometry geometry,
            StorageUiEntry entry,
            boolean priorityEditing,
            double mouseX,
            double mouseY) {
        if (canvas == null || geometry == null || entry == null) {
            throw new IllegalArgumentException(
                    "canvas, geometry and entry must not be null");
        }
        frame(
                canvas,
                geometry.row,
                StorageWindowStyle.row(
                        geometry.row.contains(mouseX, mouseY)));
        if (!priorityEditing) {
            frame(
                    canvas,
                    geometry.priority,
                    StorageWindowStyle.priority(
                            geometry.priority.contains(mouseX, mouseY)));
        }
        frame(
                canvas,
                geometry.extract,
                StorageWindowStyle.extract(
                        entry.extractOnly,
                        geometry.extract.contains(mouseX, mouseY)));
        frame(
                canvas,
                geometry.unlink,
                StorageWindowStyle.unlink(
                        geometry.unlink.contains(mouseX, mouseY)));
    }

    public static void renderMissingIcon(
            UiCanvas2D canvas,
            UiRect icon) {
        if (canvas == null || icon == null) {
            throw new IllegalArgumentException(
                    "canvas and icon must not be null");
        }
        canvas.fill(icon, StorageWindowStyle.PLACEHOLDER_BACKGROUND);
        canvas.fill(
                icon.getX(),
                icon.getY(),
                icon.getWidth() + 1.0D,
                1.0D,
                StorageWindowStyle.PLACEHOLDER_BORDER);
        canvas.fill(
                icon.getX(),
                icon.getY(),
                1.0D,
                icon.getHeight() + 1.0D,
                StorageWindowStyle.PLACEHOLDER_BORDER);
    }

    public static void renderScrollbar(
            UiCanvas2D canvas,
            StorageWindowLayout.Geometry geometry) {
        if (canvas == null || geometry == null) {
            throw new IllegalArgumentException(
                    "canvas and geometry must not be null");
        }
        if (!geometry.hasScrollbar()) {
            return;
        }
        canvas.fill(
                geometry.scrollbarTrack,
                StorageWindowStyle.SCROLLBAR_TRACK);
        canvas.fill(
                geometry.scrollbarInset,
                StorageWindowStyle.SCROLLBAR_INSET);
        canvas.fill(
                geometry.scrollbarThumb,
                StorageWindowStyle.SCROLLBAR_THUMB);
    }

    private static void frame(
            UiCanvas2D canvas,
            UiRect bounds,
            StorageWindowStyle.FrameVisual visual) {
        UiCompactFrameRenderer.frame(
                canvas,
                bounds,
                visual.background,
                visual.border,
                visual.darkBorder);
    }
}
