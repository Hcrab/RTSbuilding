package com.rtsbuilding.rtsbuilding.blueprint.client;

import java.util.List;

import com.rtsbuilding.rtsbuilding.client.ClientRtsController;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.buildStats;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.detailLines;
import static com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintMaterialInspector.isCreativePlayer;
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

    private BlueprintMaterialDialog() {
    }

    static int render(GuiGraphics g, Font font, BlueprintEntry entry, ClientRtsController controller,
            int screenW, int screenH, int mouseX, int mouseY, int scroll) {
        Layout layout = layout(screenW, screenH);
        List<DetailLine> lines = detailLines(entry, controller);
        int visible = visibleRows(layout.listH());
        int clampedScroll = Mth.clamp(scroll, 0, maxScroll(lines.size(), visible));

        g.fill(0, 0, screenW, screenH, 0x66000000);
        drawFrame(g, layout.x(), layout.y(), layout.w(), layout.h(), 0xEE121922, 0xFF6E8799, 0xFF0B0E13);
        g.fill(layout.x() + 1, layout.y() + 1, layout.x() + layout.w() - 1, layout.y() + 26, 0xD8293440);
        g.drawString(font, trim(font, text("screen.rtsbuilding.blueprints.details_title"), layout.w() - 70),
                layout.x() + 10, layout.y() + 9, 0xFFEAF2FF, false);
        drawButton(g, font, layout.closeX(), layout.y() + 4, CLOSE_SIZE, CLOSE_SIZE, "x",
                inside(mouseX, mouseY, layout.closeX(), layout.y() + 4, CLOSE_SIZE, CLOSE_SIZE));

        g.drawString(font, trim(font, entry.name(), layout.w() - 20), layout.x() + 10, layout.y() + 35, 0xFFEAF2FF, false);
        BuildStats stats = buildStats(entry, controller);
        boolean creativeBypass = isCreativePlayer() && lines.isEmpty();
        String summary = creativeBypass
                ? text("screen.rtsbuilding.blueprints.materials_creative")
                : lines.isEmpty()
                        ? text("screen.rtsbuilding.blueprints.materials_all_ready")
                        : text("screen.rtsbuilding.blueprints.details_summary",
                                stats.percent(),
                                stats.buildable(),
                                stats.total(),
                                stats.missingTypes(),
                                stats.unsupportedTypes(),
                                stats.missingBlockTypes());
        int summaryColor = creativeBypass || lines.isEmpty() ? 0xFF8EEA9B : 0xFFFFC06C;
        g.drawString(font, trim(font, summary, layout.w() - 20), layout.x() + 10, layout.y() + 48, summaryColor, false);

        drawFrame(g, layout.listX(), layout.listY(), layout.listW(), layout.listH(), 0x99101620, 0xFF415266, 0xFF0B0E13);
        if (creativeBypass || lines.isEmpty()) {
            String message = creativeBypass
                    ? text("screen.rtsbuilding.blueprints.materials_creative")
                    : text("screen.rtsbuilding.blueprints.materials_all_ready");
            g.drawString(font, trim(font, message, layout.listW() - 14), layout.listX() + 7, layout.listY() + 8,
                    summaryColor, false);
            return clampedScroll;
        }

        renderRows(g, font, lines, layout, mouseX, mouseY, clampedScroll, visible);
        renderScrollbar(g, lines.size(), layout, clampedScroll, visible);
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
        int maxScroll = maxScroll(detailLines(entry, controller).size(), visible);
        return Mth.clamp(currentScroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
    }

    private static void renderRows(GuiGraphics g, Font font, List<DetailLine> lines, Layout layout,
            int mouseX, int mouseY, int scroll, int visible) {
        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= lines.size()) {
                break;
            }
            DetailLine line = lines.get(index);
            int rowY = layout.listY() + 3 + row * ROW_H;
            if (inside(mouseX, mouseY, layout.listX(), rowY, layout.listW(), ROW_H)) {
                g.fill(layout.listX() + 1, rowY, layout.listX() + layout.listW() - 1, rowY + ROW_H, 0x66324126);
            }
            if (!line.preview().isEmpty()) {
                g.renderItem(line.preview(), layout.listX() + 5, rowY + 2);
            } else {
                g.fill(layout.listX() + 7, rowY + 4, layout.listX() + 21, rowY + 18, 0xAA36506A);
                g.drawCenteredString(font, "?", layout.listX() + 14, rowY + 6, 0xFFFFD080);
            }
            g.drawString(font, trim(font, line.label(), layout.listW() - 132), layout.listX() + 27, rowY + 2,
                    0xFFEAF2FF, false);
            g.drawString(font, trim(font, line.detail(), 102), layout.listX() + layout.listW() - 108, rowY + 7,
                    line.color(), false);
        }
    }

    private static void renderScrollbar(GuiGraphics g, int lineCount, Layout layout, int scroll, int visible) {
        int maxScroll = maxScroll(lineCount, visible);
        if (maxScroll <= 0) {
            return;
        }
        int barX = layout.listX() + layout.listW() - 5;
        int barY = layout.listY() + 3;
        int barH = layout.listH() - 6;
        int thumbH = Math.max(12, barH * visible / Math.max(visible, lineCount));
        int thumbY = barY + (barH - thumbH) * scroll / maxScroll;
        g.fill(barX, barY, barX + 2, barY + barH, 0x66566A7C);
        g.fill(barX - 1, thumbY, barX + 3, thumbY + thumbH, 0xFF8EA5B8);
    }

    private static int visibleRows(int listH) {
        return Math.max(1, listH / ROW_H);
    }

    private static int maxScroll(int lineCount, int visible) {
        return Math.max(0, lineCount - visible);
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
