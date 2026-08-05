package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import java.util.List;

/**
 * 底栏合成区的 Minecraft 绘制适配器。
 *
 * <p>本类只把 Core 快照、正式物品栈与 Kit 布局画到 {@link LegacyGuiGraphics}。它不维护滚动或搜索状态，
 * 不读取控制器，也不发送合成请求；这些生命周期和副作用继续由 {@link BottomPanel} 编排。</p>
 */
public final class BottomPanelCraftRenderer {
    private BottomPanelCraftRenderer() {
    }

    /**
     * 绘制合成区并返回当前悬停的 Core 条目索引；没有真实条目时返回 -1。
     */
    public static int render(LegacyGuiGraphics graphics, FontRenderer font, GuiTextField searchBox,
                             BottomBarUiState state, List<CraftableEntry> sourceEntries,
                             BottomPanelCraftLayout layout,
                             int mouseX, int mouseY, float partialTick) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(layout.panel.x, layout.panel.y,
                        layout.panel.width, layout.panel.height),
                BottomPanelCraftStyle.PANEL_BACKGROUND,
                BottomPanelCraftStyle.PANEL_BORDER_LIGHT,
                BottomPanelCraftStyle.PANEL_BORDER_DARK);
        graphics.drawString(font, "Craft",
                layout.panel.x + BottomPanelCraftLayout.TITLE_X,
                layout.panel.y + BottomPanelCraftLayout.TITLE_Y,
                argb(BottomPanelCraftStyle.TITLE), false);

        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(layout.search.x, layout.search.y,
                        layout.search.width, layout.search.height),
                BottomPanelCraftStyle.SEARCH_BACKGROUND,
                BottomPanelCraftStyle.SEARCH_BORDER_LIGHT,
                BottomPanelCraftStyle.SEARCH_BORDER_DARK);
        if (searchBox != null) {
            searchBox.xPosition = layout.search.x + BottomPanelCraftLayout.SEARCH_CONTENT_INSET;
            searchBox.yPosition = layout.search.y + BottomPanelCraftLayout.SEARCH_CONTENT_INSET;
            searchBox.width = Math.max(
                    BottomPanelCraftLayout.SEARCH_MIN_CONTENT_W,
                    layout.search.width - BottomPanelCraftLayout.SEARCH_CONTENT_INSET * 2);
            searchBox.height = 8;
            searchBox.drawTextBox();
        }

        boolean searchDirty = state.craftSearchDirty();
        drawButton(graphics, canvas, font, layout.apply, "OK",
                BottomPanelCraftStyle.applyBackground(searchDirty),
                BottomPanelCraftStyle.BUTTON_BORDER_LIGHT,
                searchDirty ? argb(BottomPanelCraftStyle.BUTTON_TEXT)
                        : argb(BottomPanelCraftStyle.BUTTON_TEXT_IDLE));
        drawButton(graphics, canvas, font, layout.toggle,
                state.craftShowUnavailable ? "ALL" : "MAKE",
                BottomPanelCraftStyle.toggleBackground(state.craftShowUnavailable),
                BottomPanelCraftStyle.TOGGLE_BORDER_LIGHT,
                argb(BottomPanelCraftStyle.BUTTON_TEXT));

        int hoveredIndex = layout.entryIndexAt(mouseX, mouseY);
        List<BottomBarUiEntry> entries = state.craftableEntries;
        for (int row = 0; row < layout.visibleRows; row++) {
            for (int column = 0; column < RtsMainlineLayout.CRAFT_PANEL_COLS; column++) {
                int index = layout.startIndex
                        + row * RtsMainlineLayout.CRAFT_PANEL_COLS + column;
                int slotX = layout.slotX(column);
                int slotY = layout.slotY(row);
                boolean present = index < entries.size();
                BottomBarUiEntry entry = present ? entries.get(index) : null;
                UiCompactFrameRenderer.frame(
                        canvas,
                        new UiRect(slotX, slotY,
                                RtsMainlineLayout.CRAFT_PANEL_SLOT,
                                RtsMainlineLayout.CRAFT_PANEL_SLOT),
                        BottomPanelCraftStyle.slotBackground(
                                present, present && entry.available),
                        BottomPanelCraftStyle.SLOT_BORDER_LIGHT,
                        BottomPanelCraftStyle.SLOT_BORDER_DARK);
                if (!present) {
                    continue;
                }

                if (entry.sourceIndex >= 0 && entry.sourceIndex < sourceEntries.size()) {
                    graphics.renderItem(sourceEntries.get(entry.sourceIndex).stack(),
                            slotX + 1, slotY + 1);
                }
                if (entry.amount > 1) {
                    RtsClientUiUtil.drawSlotCountOverlay(graphics, font, slotX, slotY,
                            RtsMainlineLayout.CRAFT_PANEL_SLOT,
                            RtsClientUiUtil.compactCount(entry.amount),
                            argb(BottomPanelCraftStyle.SLOT_COUNT));
                }
                if (!entry.available) {
                    graphics.fill(slotX + 1, slotY + 1,
                            slotX + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            slotY + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            argb(BottomPanelCraftStyle.SLOT_UNAVAILABLE_OVERLAY));
                }
                if (index == hoveredIndex) {
                    graphics.fill(slotX + 1, slotY + 1,
                            slotX + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            slotY + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            argb(BottomPanelCraftStyle.SLOT_HOVER_OVERLAY));
                }
            }
        }
        return hoveredIndex;
    }

    private static void drawButton(
                                   LegacyGuiGraphics graphics, UiCanvas2D canvas, FontRenderer font,
                                   BottomPanelCraftLayout.Area area, String label,
                                   UiColor background, UiColor lightBorder, int textColor) {
        UiCompactFrameRenderer.frame(
                canvas, new UiRect(area.x, area.y, area.width, area.height),
                background, lightBorder, BottomPanelCraftStyle.BUTTON_BORDER_DARK);
        int textX = area.x + (area.width - font.getStringWidth(label)) / 2;
        graphics.drawString(font, label, textX,
                area.y + BottomPanelCraftLayout.BUTTON_TEXT_TOP,
                textColor, false);
    }

    private static int argb(com.rtsbuilding.rtsbuilding.uikit.theme.UiColor color) {
        return color.toArgb();
    }
}
