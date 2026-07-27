package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintDialogStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.text;
import static com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanelUi.trim;

/**
 * 绘制正式蓝图材料窗口的内容区。
 *
 * <p>窗口框架、标题栏和关闭行为由 {@link BlueprintMaterialWindowPanel} 管理；
 * 本类只消费 Core 材料快照，并把物品 id 还原为 Minecraft 图标。</p>
 */
final class BlueprintMaterialDialog {
    private static final int ROW_H = BlueprintWindowLayout.MATERIAL_ROW_H;
    private static final int COLUMN_GAP = BlueprintWindowLayout.MATERIAL_COLUMN_GAP;

    private BlueprintMaterialDialog() {
    }

    static int renderCoreContent(LegacyGuiGraphics g, FontRenderer font, BlueprintMaterialUiState state,
            int x, int y, int w, int h, int mouseX, int mouseY, int scroll) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);
        Layout layout = layoutFromBounds(x, y, w, h);
        int visible = visibleRows(layout.listH());
        int columns = columns(layout);
        int clampedScroll = MathHelper.clamp(scroll, 0, maxScroll(state.rows.size(), visible, columns));
        g.drawString(font, trim(font, state.blueprintName, layout.w() - 20),
                layout.x() + 10, layout.y() + 8, BlueprintDialogStyle.PRIMARY_TEXT.toArgb(), false);
        String summary = state.rows.isEmpty()
                ? text("screen.rtsbuilding.blueprints.materials_all_ready")
                : text("screen.rtsbuilding.blueprints.details_summary",
                        state.percent, state.buildable, state.total, state.missingTypes,
                        state.unsupportedTypes, state.missingBlockTypes);
        int summaryColor = (state.allReady() ? BlueprintDialogStyle.READY
                : BlueprintDialogStyle.WARNING).toArgb();
        g.drawString(font, trim(font, summary, layout.w() - 20),
                layout.x() + 10, layout.y() + 21, summaryColor, false);
        UiChromeRenderer.frame(canvas, new UiRect(layout.listX(), layout.listY(),
                        layout.listW(), layout.listH()), 1.0D,
                BlueprintDialogStyle.LIST_BACKGROUND, BlueprintDialogStyle.LIST_BORDER,
                BlueprintDialogStyle.DARK_BORDER);
        if (state.rows.isEmpty()) {
            String message = text("screen.rtsbuilding.blueprints.materials_all_ready");
            g.drawString(font, trim(font, message, layout.listW() - 14),
                    layout.listX() + 7, layout.listY() + 8, summaryColor, false);
            return clampedScroll;
        }
        renderCoreRows(g, font, state.rows, layout, mouseX, mouseY, clampedScroll, visible, columns);
        renderScrollbar(g, state.rows.size(), layout, clampedScroll, visible, columns);
        return clampedScroll;
    }

    static int scrolledCore(int currentScroll, double scrollY, BlueprintMaterialUiState state, int w, int h) {
        Layout layout = layoutFromBounds(0, 0, w, h);
        int visible = visibleRows(layout.listH());
        int maxScroll = maxScroll(state.rows.size(), visible, columns(layout));
        return MathHelper.clamp(currentScroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
    }

    private static void renderCoreRows(LegacyGuiGraphics g, FontRenderer font, List<BlueprintMaterialUiState.Row> lines,
            Layout layout, int mouseX, int mouseY, int scroll, int visible, int columns) {
        int cellW = (layout.listW() - 8 - (columns - 1) * COLUMN_GAP) / columns;
        for (int row = 0; row < visible; row++) {
            for (int column = 0; column < columns; column++) {
                int index = (scroll + row) * columns + column;
                if (index >= lines.size()) {
                    return;
                }
                BlueprintMaterialUiState.Row line = lines.get(index);
                int rowX = layout.listX() + 4 + column * (cellW + COLUMN_GAP);
                int rowY = layout.listY() + 3 + row * ROW_H;
                if (UiRect.contains(rowX, rowY, cellW, ROW_H, mouseX, mouseY)) {
                    g.fill(rowX, rowY, rowX + cellW, rowY + ROW_H,
                            BlueprintDialogStyle.ROW_HOVER.toArgb());
                }
                ItemStack preview = ItemStack.EMPTY;
                ResourceLocation id = parseResourceLocation(line.iconId);
                Item item = id == null ? null : Item.REGISTRY.getObject(id);
                if (item != null) {
                    preview = new ItemStack(item);
                }
                if (!preview.isEmpty()) {
                    g.renderItem(preview, rowX + 4, rowY + 2);
                } else {
                    g.fill(rowX + 6, rowY + 4, rowX + 20, rowY + 18,
                            BlueprintDialogStyle.MISSING_ICON_BACKGROUND.toArgb());
                    g.drawString(font, "?", rowX + 13 - font.getStringWidth("?") / 2, rowY + 6,
                            BlueprintDialogStyle.MISSING_ICON_TEXT.toArgb(), false);
                }
                int detailW = Math.min(86, Math.max(54, cellW / 3));
                int detailX = rowX + cellW - detailW - 4;
                g.drawString(font, trim(font, line.label, Math.max(24, detailX - rowX - 28)),
                        rowX + 26, rowY + 2, BlueprintDialogStyle.PRIMARY_TEXT.toArgb(), false);
                g.drawString(font, trim(font, line.detail, detailW), detailX, rowY + 7,
                        BlueprintDialogStyle.materialTone(line.tone).toArgb(), false);
            }
        }
    }

    private static void renderScrollbar(LegacyGuiGraphics g, int lineCount, Layout layout, int scroll,
            int visible, int columns) {
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
        g.fill(barX, barY, barX + 2, barY + barH, BlueprintDialogStyle.SCROLL_TRACK.toArgb());
        g.fill(barX - 1, thumbY, barX + 3, thumbY + thumbH,
                BlueprintDialogStyle.SCROLL_THUMB.toArgb());
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

    private static Layout layoutFromBounds(int x, int y, int width, int height) {
        BlueprintWindowLayout.MaterialDialogGeometry shared =
                BlueprintWindowLayout.materialDialog(x, y, width, height);
        return new Layout(shared.x, shared.y, shared.width, shared.height,
                shared.listX, shared.listY, shared.listW, shared.listH);
    }

    private static ResourceLocation parseResourceLocation(String value) {
        try {
            return value == null || value.isEmpty() ? null : new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class Layout {
        private final int x, y, w, h, listX, listY, listW, listH;
        private Layout(int x, int y, int w, int h, int listX, int listY, int listW, int listH) {
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.listX = listX; this.listY = listY; this.listW = listW; this.listH = listH;
        }
        int x() { return x; } int y() { return y; } int w() { return w; } int h() { return h; }
        int listX() { return listX; } int listY() { return listY; }
        int listW() { return listW; } int listH() { return listH; }
    }
}
