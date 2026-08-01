package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.drawCentered;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.trim;

/**
 * 蓝图库可见卡片与卡片动作的生产文字适配器。
 *
 * <p>它只访问 Core 的轻量可见快照，并把文字画在 Kit 决定的卡片、进度和按钮矩形上；
 * 不扫描不可见蓝图、不拥有滚动，也不执行保存、重命名或删除。</p>
 */
final class BlueprintLibraryRowRenderer {
    private BlueprintLibraryRowRenderer() {
    }

    static void render(
            GuiGraphics graphics,
            Font font,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryUiState state,
            BlueprintLibraryLayout.ActionTextWidths actionWidths,
            int mouseX,
            int mouseY) {
        List<BlueprintLibraryUiEntry> filtered =
                state.filteredEntries();
        if (filtered.isEmpty()) {
            String key = state.entries.isEmpty()
                    ? "screen.rtsbuilding.blueprints.empty"
                    : "screen.rtsbuilding.blueprints.no_results";
            graphics.drawString(
                    font,
                    trim(font, text(key), geometry.listW - 12),
                    geometry.x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    geometry.listY
                            + BlueprintLibraryLayout.EMPTY_TEXT_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(),
                    false);
            return;
        }

        BlueprintLibraryLayout.VisibleWindow window =
                BlueprintLibraryLayout.visibleWindow(
                        filtered.size(),
                        state.scrollRows,
                        geometry.listW,
                        geometry.listH);
        for (int row = 0; row < window.visibleRows; row++) {
            for (int column = 0;
                 column < window.columns;
                 column++) {
                int index = (window.scrollRows + row)
                        * window.columns + column;
                if (index >= filtered.size()) {
                    break;
                }
                BlueprintLibraryUiEntry entry = filtered.get(index);
                BlueprintLibraryLayout.RowGeometry rowGeometry =
                        BlueprintLibraryLayout.rowGeometry(
                                geometry.x,
                                geometry.listY,
                                geometry.listW,
                                row,
                                column,
                                actionWidths);
                boolean selected = entry.fileName.equals(
                        state.selectedFileName);
                boolean showActions = selected
                        || rowGeometry.hitBounds.contains(
                                mouseX,
                                mouseY);
                BlueprintLibraryChromeRenderer.renderRow(
                        canvas,
                        rowGeometry,
                        entry,
                        selected,
                        showActions,
                        mouseX,
                        mouseY);
                drawText(
                        graphics,
                        font,
                        rowGeometry,
                        entry,
                        showActions);
            }
        }
    }

    private static void drawText(
            GuiGraphics graphics,
            Font font,
            BlueprintLibraryLayout.RowGeometry geometry,
            BlueprintLibraryUiEntry entry,
            boolean showActions) {
        int cellX = (int) geometry.hitBounds.getX();
        int rowY = (int) geometry.hitBounds.getY();
        int actualWidth = (int) geometry.hitBounds.getWidth();
        int rightTextX = showActions
                ? (int) geometry.save.getX() - 4
                : cellX + actualWidth
                        - BlueprintLibraryLayout.ROW_PERCENT_RIGHT;
        graphics.drawString(
                font,
                trim(
                        font,
                        entry.name,
                        Math.max(
                                32,
                                rightTextX - cellX - 8)),
                cellX + BlueprintLibraryLayout.ROW_NAME_X,
                rowY + BlueprintLibraryLayout.ROW_NAME_Y,
                entry.valid()
                        ? BlueprintLibraryStyle.ROW_NAME_TEXT.toArgb()
                        : BlueprintLibraryStyle.ROW_INVALID_TEXT.toArgb(),
                false);
        if (showActions) {
            if (entry.valid()) {
                drawCentered(
                        graphics,
                        font,
                        geometry.save,
                        text(
                                "screen.rtsbuilding.blueprints.save_as_short"));
                drawCentered(
                        graphics,
                        font,
                        geometry.rename,
                        text(
                                "screen.rtsbuilding.blueprints.rename"));
            }
            drawCentered(
                    graphics,
                    font,
                    geometry.delete,
                    text("screen.rtsbuilding.blueprints.delete"));
        } else {
            graphics.drawString(
                    font,
                    entry.buildPercent + "%",
                    cellX + actualWidth
                            - BlueprintLibraryLayout.ROW_PERCENT_RIGHT,
                    rowY + BlueprintLibraryLayout.ROW_NAME_Y,
                    entry.buildPercent >= 100
                            ? BlueprintLibraryStyle
                                    .ROW_PERCENT_READY_TEXT.toArgb()
                            : BlueprintLibraryStyle.ROW_PERCENT_TEXT
                                    .toArgb(),
                    false);
        }
        graphics.drawString(
                font,
                trim(
                        font,
                        entry.size,
                        Math.max(24, actualWidth - 70)),
                cellX + BlueprintLibraryLayout.ROW_NAME_X,
                rowY + BlueprintLibraryLayout.ROW_SIZE_Y,
                BlueprintLibraryStyle.ROW_SIZE_TEXT.toArgb(),
                false);
    }
}
