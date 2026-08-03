package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.blueprint.BlueprintMaterialUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BlueprintWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintDialogStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.rtsbuilding.rtsbuilding.platform.RtsBuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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

    static int renderCoreContent(GuiGraphics g, Font font, BlueprintMaterialUiState state,
            int x, int y, int w, int h, int mouseX, int mouseY, int scroll) {
        MinecraftUiCanvas canvas = new MinecraftUiCanvas(g, font);
        Layout layout = layoutFromBounds(x, y, w, h);
        int visible = visibleRows(layout.listH());
        int columns = columns(layout);
        int clampedScroll = Mth.clamp(scroll, 0, maxScroll(state.rows.size(), visible, columns));
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
        return Mth.clamp(currentScroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll);
    }

    private static void renderCoreRows(GuiGraphics g, Font font, List<BlueprintMaterialUiState.Row> lines,
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
                ResourceLocation id = ResourceLocation.tryParse(line.iconId);
                if (id != null && RtsBuiltInRegistries.ITEM.containsKey(id)) {
                    preview = new ItemStack(RtsBuiltInRegistries.ITEM.get(id));
                }
                if (!preview.isEmpty()) {
                    g.renderItem(preview, rowX + 4, rowY + 2);
                } else {
                    g.fill(rowX + 6, rowY + 4, rowX + 20, rowY + 18,
                            BlueprintDialogStyle.MISSING_ICON_BACKGROUND.toArgb());
                    RtsClientUiUtil.drawCenteredStringNoShadow(g, font, "?", rowX + 13, rowY + 6,
                            BlueprintDialogStyle.MISSING_ICON_TEXT.toArgb());
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

    private static void renderScrollbar(GuiGraphics g, int lineCount, Layout layout, int scroll,
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

    private record Layout(int x, int y, int w, int h, int listX, int listY, int listW, int listH) {
    }
}
