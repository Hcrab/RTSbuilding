package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.input.RtsWidgetCompat;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiEntry;
import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiState;
import com.rtsbuilding.rtsbuilding.uicore.control.UiControlState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.animation.SystemUiClock;
import com.rtsbuilding.rtsbuilding.uikit.animation.UiControlAnimationRegistry;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCompactFrameRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.BottomPanelCraftLayout;
import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

import java.util.List;

/**
 * 26.1 工艺区的生产绘制适配器。
 *
 * <p>本类只将 Core 快照、真实配方预览和 Kit 布局绘制到 Extractor；搜索提交、可用性
 * 切换、滚动和数量窗口仍由输入适配器经由原有控制器处理。</p>
 */
public final class BottomPanelCraftRenderer {
    private static final UiControlAnimationRegistry<String> ANIMATIONS =
            new UiControlAnimationRegistry<>(SystemUiClock.INSTANCE, 64);

    private BottomPanelCraftRenderer() {
    }

    /** 绘制工艺区并返回当前悬停的真实条目索引。 */
    public static int render(
            GuiGraphicsExtractor graphics,
            Font font,
            EditBox searchBox,
            BottomBarUiState state,
            List<CraftableEntry> sourceEntries,
            BottomPanelCraftLayout layout,
            int mouseX,
            int mouseY,
            float partialTick) {
        UiCanvas2D canvas = new MinecraftUiCanvas(graphics, font);
        UiCompactFrameRenderer.frame(
                canvas,
                new UiRect(layout.panel.x, layout.panel.y,
                        layout.panel.width, layout.panel.height),
                BottomPanelCraftStyle.PANEL_BACKGROUND,
                BottomPanelCraftStyle.PANEL_BORDER_LIGHT,
                BottomPanelCraftStyle.PANEL_BORDER_DARK);
        graphics.text(font, "Craft",
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
            searchBox.setX(layout.search.x + BottomPanelCraftLayout.SEARCH_CONTENT_INSET);
            searchBox.setY(layout.search.y + BottomPanelCraftLayout.SEARCH_CONTENT_INSET);
            searchBox.setWidth(Math.max(
                    BottomPanelCraftLayout.SEARCH_MIN_CONTENT_W,
                    layout.search.width - BottomPanelCraftLayout.SEARCH_CONTENT_INSET * 2));
            searchBox.setHeight(8);
            RtsWidgetCompat.render(searchBox, graphics, mouseX, mouseY, partialTick);
        }

        boolean searchDirty = state.craftSearchDirty();
        double applyHover = hover(
                "apply", layout.apply.contains(mouseX, mouseY), searchDirty);
        double toggleHover = hover(
                "toggle", layout.toggle.contains(mouseX, mouseY),
                state.craftShowUnavailable);
        drawButton(graphics, canvas, font, layout.apply, "OK",
                BottomPanelCraftStyle.applyBackground(searchDirty, applyHover),
                BottomPanelCraftStyle.BUTTON_BORDER_LIGHT,
                searchDirty ? argb(BottomPanelCraftStyle.BUTTON_TEXT)
                        : argb(BottomPanelCraftStyle.BUTTON_TEXT_IDLE));
        drawButton(graphics, canvas, font, layout.toggle,
                state.craftShowUnavailable ? "ALL" : "MAKE",
                BottomPanelCraftStyle.toggleBackground(
                        state.craftShowUnavailable, toggleHover),
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
                    graphics.item(sourceEntries.get(entry.sourceIndex).stack(),
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
                double slotHover = hover(
                        "slot." + (row * RtsMainlineLayout.CRAFT_PANEL_COLS + column),
                        index == hoveredIndex, false);
                if (slotHover > 0.0D) {
                    graphics.fill(slotX + 1, slotY + 1,
                            slotX + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            slotY + RtsMainlineLayout.CRAFT_PANEL_SLOT - 1,
                            argb(BottomPanelCraftStyle.slotHoverOverlay(slotHover)));
                }
            }
        }
        return hoveredIndex;
    }

    private static void drawButton(
            GuiGraphicsExtractor graphics,
            UiCanvas2D canvas,
            Font font,
            BottomPanelCraftLayout.Area area,
            String label,
            UiColor background,
            UiColor lightBorder,
            int textColor) {
        UiCompactFrameRenderer.frame(
                canvas, new UiRect(area.x, area.y, area.width, area.height),
                background, lightBorder, BottomPanelCraftStyle.BUTTON_BORDER_DARK);
        int textX = area.x + (area.width - font.width(label)) / 2;
        graphics.text(font, label, textX,
                area.y + BottomPanelCraftLayout.BUTTON_TEXT_TOP,
                textColor, false);
    }

    private static int argb(UiColor color) {
        return color.toArgb();
    }

    private static double hover(String id, boolean hovered, boolean selected) {
        UiControlState state = new UiControlState(
                true, selected, false, false, "")
                .withInteraction(hovered, false, false);
        return ANIMATIONS.update(
                id, state, Config.isUiAnimationsEnabled()).hover();
    }
}
