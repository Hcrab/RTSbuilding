package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.util.List;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.buildStats;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.detailLines;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.drawButton;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.drawFrame;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.inside;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanelUi.trim;

/**
 * Renders the modal list of missing materials and blueprint compatibility notes.
 */
final class BlueprintMaterialDialog {
    private static final int CLOSE_SIZE = 18;
    private static final int ROW_H = 22;
    private static final int COLUMN_GAP = 6;

    private BlueprintMaterialDialog() {
    }

    static int render(GuiGraphics g, Font font, BlueprintEntry entry, ClientRtsController controller,
            int screenW, int screenH, int mouseX, int mouseY, int scroll) {
        Layout layout = layout(screenW, screenH);
        List<DetailLine> lines = detailLines(entry, controller);
        int visible = visibleRows(layout.listH());
        int columns = columns(layout);
        int clampedScroll = Mth.clamp(scroll, 0, maxScroll(lines.size(), visible, columns));

        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + 26, 0xD8293440);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.details_title"), layout.w() - 70),
                layout.x() + 10, layout.y() + 9, 0xFFEAF2FF, false);
        drawButton(g, font, layout.closeX(), layout.y() + 4, CLOSE_SIZE, CLOSE_SIZE, "x",
                inside(mouseX, mouseY, layout.closeX(), layout.y() + 4, CLOSE_SIZE, CLOSE_SIZE));

        g.drawString(font, trim(font, entry.name(), layout.w() - 20), layout.x() + 10, layout.y() + 35, 0xFFEAF2FF, false);
        BuildStats stats = buildStats(entry, controller);
        String summary = lines.isEmpty()
                ? text("screen.rtsbuilding.blueprints.materials_all_ready")
                : text("screen.rtsbuilding.blueprints.details_summary",
                        stats.percent(),
                        stats.buildable(),
                        stats.total(),
                        stats.missingTypes(),
                        stats.unsupportedTypes(),
                        stats.missingBlockTypes());
        int summaryColor = lines.isEmpty() || stats.percent() >= 100 ? 0xFF8EEA9B : 0xFFFFC06C;
        g.drawString(font, trim(font, summary, layout.w() - 20), layout.x() + 10, layout.y() + 48, summaryColor, false);

        drawFrame(g, layout.listX(), layout.listY(), layout.listW(), layout.listH(), 0x99101620, 0xFF415266, 0xFF0B0E13);
        if (lines.isEmpty()) {
            String message = text("screen.rtsbuilding.blueprints.materials_all_ready");
            g.drawString(font, trim(font, message, layout.listW() - 14), layout.listX() + 7, layout.listY() + 8,
                    summaryColor, false);
            return clampedScroll;
        }

        renderRows(g, font, lines, layout, mouseX, mouseY, clampedScroll, visible, columns);
        renderScrollbar(g, lines.size(), layout, clampedScroll, visible, columns);
        return clampedScroll;
    }

    static boolean shouldClose(double mouseX, double mouseY, int screenW, int screenH) {
        Layout layout = layout(screenW, screenH);
        return !inside(mouseX, mouseY, layout.x(), layout.y(), layout.w(), layout.h())
                || inside(mouseX, mouseY, layout.closeX(), layout.y() + 4, CLOSE_SIZE, CLOSE_SIZE);
    }

    static int scrolled(int currentScroll, double scrollY, BlueprintEntry entry, ClientRtsController controller, int screenH) {
        Layout layout = layout(0, screenH);
        int visible = visibleRows(layout.listH());
        int maxScroll = maxScroll(detailLines(entry, controller).size(), visible, columns(layout));
        return Mth.clamp(currentScroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
    }

    private static void renderRows(GuiGraphics g, Font font, List<DetailLine> lines, Layout layout,
            int mouseX, int mouseY, int scroll, int visible, int columns) {
        int cellW = (layout.listW() - 8 - (columns - 1) * COLUMN_GAP) / columns;
        for (int row = 0; row < visible; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scroll + row) * columns + column;
                if (index >= lines.size()) {
                    return;
                }
                DetailLine line = lines.get(index);
                int rowX = layout.listX() + 4 + column * (cellW + COLUMN_GAP);
                int rowY = layout.listY() + 3 + row * ROW_H;
                if (inside(mouseX, mouseY, rowX, rowY, cellW, ROW_H)) {
                    g.fill(rowX, rowY, rowX + cellW, rowY + ROW_H, 0x66324126);
                }
                if (!line.preview().isEmpty()) {
                    g.renderItem(line.preview(), rowX + 4, rowY + 2);
                } else {
                    g.fill(rowX + 6, rowY + 4, rowX + 20, rowY + 18, 0xAA36506A);
                    g.drawCenteredString(font, "?", rowX + 13, rowY + 6, 0xFFFFD080);
                }
                int detailW = Math.min(86, Math.max(54, cellW / 3));
                int detailX = rowX + cellW - detailW - 4;
                g.drawString(font, trim(font, line.label(), Math.max(24, detailX - rowX - 28)), rowX + 26, rowY + 2,
                        0xFFEAF2FF, false);
                g.drawString(font, trim(font, line.detail(), detailW), detailX, rowY + 7,
                        line.color(), false);
            }
        }
    }

    private static void renderScrollbar(GuiGraphics g, int lineCount, Layout layout, int scroll, int visible, int columns) {
        int maxScroll = maxScroll(lineCount, visible, columns);
        if (maxScroll <= 0) {
            return;
        }
        int barX = layout.listX() + layout.listW() - 5;
        int barY = layout.listY() + 3;
        int barH = layout.listH() - 6;
        int rowCount = rowCount(lineCount, columns);
        int thumbH = Math.max(12, barH * visible / Math.max(visible, rowCount));
        int thumbY = barY + (barH - thumbH) * scroll / maxScroll;
        g.fill(barX, barY, barX + 2, barY + barH, 0x66566A7C);
        g.fill(barX - 1, thumbY, barX + 3, thumbY + thumbH, 0xFF8EA5B8);
    }

    private static int visibleRows(int listH) {
        return Math.max(1, listH / ROW_H);
    }

    private static int maxScroll(int lineCount, int visible, int columns) {
        return Math.max(0, rowCount(lineCount, columns) - visible);
    }

    private static int rowCount(int lineCount, int columns) {
        return (lineCount + Math.max(1, columns) - 1) / Math.max(1, columns);
    }

    private static int columns(Layout layout) {
        return layout.listW() >= 390 ? 2 : 1;
    }

    private static Layout layout(int screenW, int screenH) {
        int width = screenW <= 0 ? 560 : Math.min(560, Math.max(300, screenW - 48));
        int height = Math.min(320, Math.max(188, screenH - 70));
        int x = screenW <= 0 ? 0 : (screenW - width) / 2;
        int y = Math.max(24, (screenH - height) / 2);
        int listX = x + 10;
        int listY = y + 64;
        int listW = width - 20;
        int listH = height - 76;
        return new Layout(x, y, width, height, listX, listY, listW, listH, x + width - CLOSE_SIZE - 6);
    }

    private record Layout(int x, int y, int w, int h, int listX, int listY, int listW, int listH, int closeX) {
    }
}
