package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintLibraryUiState;
import com.rtsbuilding.rtsbuilding.uikit.canvas.BlueprintLibraryChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintLibraryLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.drawCentered;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintLibraryRenderSupport.trim;

/** 将可见的 Core 蓝图行映射为 Kit 卡片与无阴影文字。 */
final class BlueprintLibraryRowRenderer {
    private BlueprintLibraryRowRenderer() {
    }

    static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            MinecraftUiCanvas canvas,
            BlueprintLibraryLayout.Geometry geometry,
            BlueprintLibraryUiState state,
            BlueprintLibraryLayout.ActionTextWidths actionWidths,
            int mouseX,
            int mouseY) {
        List<BlueprintLibraryUiEntry> filtered = state.filteredEntries();
        if (filtered.isEmpty()) {
            String key = state.entries.isEmpty()
                    ? "screen.rtsbuilding.blueprints.empty"
                    : "screen.rtsbuilding.blueprints.no_results";
            graphics.text(font, trim(font, text(key), geometry.listW - 12),
                    geometry.x + BlueprintLibraryLayout.FRAME_TEXT_X,
                    geometry.listY + BlueprintLibraryLayout.EMPTY_TEXT_Y,
                    BlueprintLibraryStyle.SECONDARY_TEXT.toArgb(), false);
            return;
        }
        BlueprintLibraryLayout.VisibleWindow window = BlueprintLibraryLayout.visibleWindow(
                filtered.size(), state.scrollRows, geometry.listW, geometry.listH);
        for (int row = 0; row < window.visibleRows; row++) {
            for (int column = 0; column < window.columns; column++) {
                int index = (window.scrollRows + row) * window.columns + column;
                if (index >= filtered.size()) {
                    break;
                }
                BlueprintLibraryUiEntry entry = filtered.get(index);
                BlueprintLibraryLayout.RowGeometry rowGeometry =
                        BlueprintLibraryLayout.rowGeometry(
                                geometry.x, geometry.listY, geometry.listW,
                                row, column, actionWidths);
                boolean selected = entry.fileName.equals(state.selectedFileName);
                boolean showActions = selected
                        || rowGeometry.hitBounds.contains(mouseX, mouseY);
                String id = "row." + row + "." + column;
                BlueprintLibraryChromeRenderer.renderRow(
                        canvas, rowGeometry, entry, selected, showActions,
                        BlueprintLibraryPanelRenderer.hover(
                                id, rowGeometry.hitBounds.contains(mouseX, mouseY), selected),
                        BlueprintLibraryPanelRenderer.hover(
                                id + ".save", rowGeometry.save.contains(mouseX, mouseY), false),
                        BlueprintLibraryPanelRenderer.hover(
                                id + ".rename", rowGeometry.rename.contains(mouseX, mouseY), false),
                        BlueprintLibraryPanelRenderer.hover(
                                id + ".delete", rowGeometry.delete.contains(mouseX, mouseY), false));
                drawText(graphics, font, rowGeometry, entry, showActions);
            }
        }
    }

    private static void drawText(
            GuiGraphicsExtractor graphics,
            Font font,
            BlueprintLibraryLayout.RowGeometry geometry,
            BlueprintLibraryUiEntry entry,
            boolean showActions) {
        int x = (int) geometry.hitBounds.getX();
        int y = (int) geometry.hitBounds.getY();
        int width = (int) geometry.hitBounds.getWidth();
        int rightTextX = showActions ? (int) geometry.save.getX() - 4
                : x + width - BlueprintLibraryLayout.ROW_PERCENT_RIGHT;
        graphics.text(font, trim(font, entry.name, Math.max(32, rightTextX - x - RtsMainlineLayout.D8)),
                x + BlueprintLibraryLayout.ROW_NAME_X,
                y + BlueprintLibraryLayout.ROW_NAME_Y,
                entry.valid() ? BlueprintLibraryStyle.ROW_NAME_TEXT.toArgb()
                        : BlueprintLibraryStyle.ROW_INVALID_TEXT.toArgb(), false);
        if (showActions) {
            if (entry.valid()) {
                drawCentered(graphics, font, geometry.save,
                        text("screen.rtsbuilding.blueprints.save_as_short"));
                drawCentered(graphics, font, geometry.rename,
                        text("screen.rtsbuilding.blueprints.rename"));
            }
            drawCentered(graphics, font, geometry.delete,
                    text("screen.rtsbuilding.blueprints.delete"));
        } else {
            graphics.text(font, entry.buildPercent + "%",
                    x + width - BlueprintLibraryLayout.ROW_PERCENT_RIGHT,
                    y + BlueprintLibraryLayout.ROW_NAME_Y,
                    entry.buildPercent >= 100
                            ? BlueprintLibraryStyle.ROW_PERCENT_READY_TEXT.toArgb()
                            : BlueprintLibraryStyle.ROW_PERCENT_TEXT.toArgb(), false);
        }
        graphics.text(font, trim(font, entry.size, Math.max(24, width - RtsMainlineLayout.D70)),
                x + BlueprintLibraryLayout.ROW_NAME_X,
                y + BlueprintLibraryLayout.ROW_SIZE_Y,
                BlueprintLibraryStyle.ROW_SIZE_TEXT.toArgb(), false);
    }
}
