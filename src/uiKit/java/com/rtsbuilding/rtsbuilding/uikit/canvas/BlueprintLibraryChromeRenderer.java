package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;

/**
 * 底部蓝图库顶栏、搜索框、列表卡片、动作框和详情进度的纯 Canvas chrome。
 *
 * <p>本类不绘制本地化文字、真实 ItemStack，也不派发文件或捕获命令。生产和离屏适配器
 * 必须把平台内容画在这些共享矩形上方。</p>
 */
public final class BlueprintLibraryChromeRenderer {
    private BlueprintLibraryChromeRenderer() {
    }

    public static void renderTopBar(
            UiCanvas2D canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryLayout.TopBar top,
            boolean searchFocused,
            double mouseX,
            double mouseY) {
        require(canvas, geometry, top);
        frame(
                canvas,
                top.folderBounds(geometry.y),
                BlueprintLibraryStyle.button(
                        top.folderBounds(geometry.y).contains(mouseX, mouseY),
                        false));
        frame(
                canvas,
                top.importBounds(geometry.y),
                BlueprintLibraryStyle.button(
                        top.importBounds(geometry.y).contains(mouseX, mouseY),
                        false));
        frame(
                canvas,
                top.syncBounds(geometry.y),
                BlueprintLibraryStyle.button(
                        top.syncBounds(geometry.y).contains(mouseX, mouseY),
                        false));
        frame(
                canvas,
                top.captureBounds(geometry.y),
                BlueprintLibraryStyle.button(
                        top.captureBounds(geometry.y).contains(mouseX, mouseY),
                        false));
        frame(
                canvas,
                top.searchBounds(geometry.y),
                BlueprintLibraryStyle.search(searchFocused));
    }

    public static void renderBodyFrames(
            UiCanvas2D canvas,
            BlueprintLibraryLayout.Geometry geometry,
            boolean captureLocked) {
        if (canvas == null || geometry == null) {
            throw new IllegalArgumentException("canvas and geometry must not be null");
        }
        if (captureLocked) {
            frame(canvas, geometry.captureBounds);
            return;
        }
        frame(canvas, geometry.listBounds);
        frame(canvas, geometry.detailsBounds);
    }

    public static void renderFrame(
            UiCanvas2D canvas,
            UiRect bounds) {
        if (canvas == null || bounds == null) {
            throw new IllegalArgumentException(
                    "canvas and bounds must not be null");
        }
        frame(canvas, bounds);
    }

    public static void renderRow(
            UiCanvas2D canvas,
            BlueprintLibraryLayout.RowGeometry geometry,
            BlueprintLibraryUiEntry entry,
            boolean selected,
            boolean showActions,
            double mouseX,
            double mouseY) {
        if (canvas == null || geometry == null || entry == null) {
            throw new IllegalArgumentException(
                    "canvas, geometry and entry must not be null");
        }
        boolean ready = entry.buildPercent >= 100;
        boolean hovered = geometry.hitBounds.contains(mouseX, mouseY);
        canvas.fill(
                geometry.card,
                BlueprintLibraryStyle.rowBackground(
                        entry.valid(),
                        ready,
                        selected,
                        hovered));
        canvas.fill(
                geometry.progress,
                BlueprintLibraryStyle.PROGRESS_TRACK);
        canvas.fill(
                geometry.progress.getX(),
                geometry.progress.getY(),
                Math.floor(
                        geometry.progress.getWidth()
                                * entry.buildPercent / 100.0D),
                geometry.progress.getHeight(),
                BlueprintLibraryStyle.progress(ready));
        if (!showActions) {
            return;
        }
        if (entry.valid()) {
            frame(
                    canvas,
                    geometry.save,
                    BlueprintLibraryStyle.button(
                            geometry.save.contains(mouseX, mouseY),
                            false));
            frame(
                    canvas,
                    geometry.rename,
                    BlueprintLibraryStyle.button(
                            geometry.rename.contains(mouseX, mouseY),
                            false));
        }
        frame(
                canvas,
                geometry.delete,
                BlueprintLibraryStyle.button(
                        geometry.delete.contains(mouseX, mouseY),
                        false));
    }

    public static void renderDetailsProgress(
            UiCanvas2D canvas,
            BlueprintLibraryLayout.DetailsGeometry geometry,
            BlueprintLibraryUiEntry entry) {
        if (canvas == null || geometry == null || entry == null) {
            throw new IllegalArgumentException(
                    "canvas, geometry and entry must not be null");
        }
        canvas.fill(
                geometry.progress,
                BlueprintLibraryStyle.PROGRESS_TRACK);
        canvas.fill(
                geometry.progress.getX(),
                geometry.progress.getY(),
                Math.floor(
                        geometry.progress.getWidth()
                                * entry.buildPercent / 100.0D),
                geometry.progress.getHeight(),
                BlueprintLibraryStyle.progress(
                        entry.buildPercent >= 100));
    }

    public static void renderPreviewSlot(
            UiCanvas2D canvas,
            UiRect slot) {
        if (canvas == null || slot == null) {
            throw new IllegalArgumentException(
                    "canvas and slot must not be null");
        }
        canvas.fill(
                slot,
                BlueprintLibraryStyle.PREVIEW_SLOT_BACKGROUND);
    }

    private static void require(
            UiCanvas2D canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryLayout.TopBar top) {
        if (canvas == null || geometry == null || top == null) {
            throw new IllegalArgumentException(
                    "canvas, geometry and top must not be null");
        }
    }

    private static void frame(
            UiCanvas2D canvas,
            UiRect bounds) {
        UiCompactFrameRenderer.frame(
                canvas,
                bounds,
                BlueprintLibraryStyle.FRAME_BACKGROUND,
                BlueprintLibraryStyle.FRAME_BORDER,
                BlueprintLibraryStyle.FRAME_DARK_BORDER);
    }

    private static void frame(
            UiCanvas2D canvas,
            UiRect bounds,
            BlueprintLibraryStyle.FrameVisual visual) {
        UiCompactFrameRenderer.frame(
                canvas,
                bounds,
                visual.background,
                visual.border,
                visual.darkBorder);
    }
}
