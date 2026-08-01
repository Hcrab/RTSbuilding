package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 合成终端的无状态绘制器。
 *
 * <p>这里只负责把已经确定的布局、储存页快照和按钮显示状态画到屏幕上；它不读取
 * 菜单、不修改滚动位置、不发送网络包，也不持有搜索框。这样终端屏幕仍是唯一的
 * 交互状态机，视觉调整则不会意外改变物品转移语义。</p>
 */
public final class CraftTerminalRenderer {
    public static final int TOOLBAR_X = -18;
    public static final int TOOLBAR_W = 15;
    public static final int TOOLBAR_H = 13;
    public static final int CLEAR_BUTTON_X = 165;
    public static final int CLEAR_STORAGE_Y = 143;
    public static final int CLEAR_PLAYER_Y = 159;
    public static final int SIDE_BUTTON_W = 22;
    public static final int SIDE_BUTTON_H = 13;
    public static final int DEPOSIT_BUTTON_X = 171;
    public static final int DEPOSIT_ALL_Y = 220;
    public static final int DEPOSIT_HOTBAR_Y = 236;

    private static final int CRAFT_GRID_FRAME_X = 23;
    private static final int CRAFT_GRID_FRAME_Y = 137;
    private static final int RESULT_FRAME_X = 132;
    private static final int RESULT_FRAME_Y = 146;

    private CraftTerminalRenderer() {
    }

    /** 绘制终端面板、储存格、滚动条和全部操作按钮。 */
    public static void render(
            GuiGraphics graphics,
            Font font,
            int left,
            int top,
            CraftTerminalLayout layout,
            CraftTerminalScrollState scrollState,
            int totalEntries,
            String searchText,
            boolean searchFocused,
            CraftTerminalSearchMode searchMode,
            boolean searchPinned,
            int maximumRows,
            RtsStorageSort sort,
            boolean ascending,
            int mouseX,
            int mouseY) {
        int visualTop = top + layout.visualTop();

        drawPanelFrame(graphics, font, left, visualTop, CraftTerminalLayout.WIDTH,
                CraftTerminalLayout.TERMINAL_BOTTOM - layout.visualTop(),
                CraftTerminalStyle.LINK_BACKGROUND,
                CraftTerminalStyle.LINK_BORDER_LIGHT,
                CraftTerminalStyle.LINK_BORDER_DARK);
        graphics.fill(left + 1, visualTop + 1,
                left + CraftTerminalLayout.WIDTH - 1,
                visualTop + CraftTerminalLayout.HEADER_HEIGHT,
                CraftTerminalStyle.LINK_TITLE_BACKGROUND.toArgb());

        for (int row = 0; row < layout.rows(); row++) {
            int rowY = top + layout.storageGridY() + row * CraftTerminalLayout.SLOT_SIZE;
            graphics.fill(left + 1, rowY, left + CraftTerminalLayout.WIDTH - 1,
                    rowY + CraftTerminalLayout.SLOT_SIZE,
                    (row & 1) == 0
                            ? CraftTerminalStyle.STORAGE_ROW_EVEN.toArgb()
                            : CraftTerminalStyle.STORAGE_ROW_ODD.toArgb());
        }

        graphics.fill(left + 1, top + CraftTerminalLayout.STORAGE_BOTTOM,
                left + CraftTerminalLayout.WIDTH - 1,
                top + CraftTerminalLayout.TERMINAL_BOTTOM - 1,
                CraftTerminalStyle.CRAFT_BACKGROUND.toArgb());
        graphics.hLine(left + 1, left + CraftTerminalLayout.WIDTH - 2,
                top + CraftTerminalLayout.STORAGE_BOTTOM,
                CraftTerminalStyle.LINK_BORDER_LIGHT.toArgb());

        drawPanelFrame(graphics, font, left, top + CraftTerminalLayout.INVENTORY_TOP,
                CraftTerminalLayout.WIDTH,
                CraftTerminalLayout.IMAGE_HEIGHT - CraftTerminalLayout.INVENTORY_TOP,
                CraftTerminalStyle.INVENTORY_PANEL_BACKGROUND,
                CraftTerminalStyle.INVENTORY_BORDER_LIGHT,
                CraftTerminalStyle.INVENTORY_BORDER_DARK);
        drawPanelFrame(graphics, font, left + CRAFT_GRID_FRAME_X, top + CRAFT_GRID_FRAME_Y,
                62, 62, CraftTerminalStyle.CRAFT_GRID_BACKGROUND,
                CraftTerminalStyle.CRAFT_GRID_BORDER_LIGHT,
                CraftTerminalStyle.CRAFT_GRID_BORDER_DARK);
        drawPanelFrame(graphics, font, left + RESULT_FRAME_X, top + RESULT_FRAME_Y,
                22, 22, CraftTerminalStyle.RESULT_BACKGROUND,
                CraftTerminalStyle.RESULT_BORDER_LIGHT,
                CraftTerminalStyle.RESULT_BORDER_DARK);

        renderHeader(graphics, font, left, top, layout, searchText,
                searchFocused, searchMode, searchPinned, maximumRows, mouseX, mouseY);
        renderStorageGrid(graphics, font, left, top, layout, scrollState, mouseX, mouseY);
        renderScrollbar(graphics, left, top, layout, scrollState, totalEntries);
        renderActionButtons(graphics, font, left, top, layout, sort, ascending, mouseX, mouseY);
    }

    private static void renderHeader(
            GuiGraphics graphics, Font font, int left, int top,
            CraftTerminalLayout layout, String searchText,
            boolean searchFocused,
            CraftTerminalSearchMode searchMode, boolean searchPinned,
            int maximumRows,
            int mouseX, int mouseY) {
        int searchY = top + layout.searchY();
        drawPanelFrame(graphics, font, left + CraftTerminalLayout.SEARCH_X, searchY,
                CraftTerminalLayout.SEARCH_WIDTH, CraftTerminalLayout.SEARCH_HEIGHT,
                CraftTerminalStyle.SEARCH_BACKGROUND,
                CraftTerminalStyle.SEARCH_BORDER_LIGHT,
                CraftTerminalStyle.SEARCH_BORDER_DARK);
        if ((searchText == null || searchText.isEmpty()) && !searchFocused) {
            graphics.drawString(font,
                    Component.translatable("screen.rtsbuilding.craft_terminal.search"),
                    left + CraftTerminalLayout.SEARCH_X + 3, searchY + 2,
                    CraftTerminalStyle.UNEDITABLE_TEXT.toArgb(), false);
        }
        if (searchText != null && !searchText.isEmpty()) {
            drawCenteredNoShadow(graphics, font, "×",
                    left + CraftTerminalLayout.SEARCH_X + CraftTerminalLayout.SEARCH_WIDTH - 7,
                    searchY + 2, CraftTerminalStyle.MUTED_TEXT.toArgb());
        }

        renderSideButton(graphics, font, left, top,
                CraftTerminalLayout.MODE_X, layout.visualTop() + 3,
                CraftTerminalLayout.HEADER_BUTTON_SIZE,
                CraftTerminalLayout.HEADER_BUTTON_SIZE,
                searchMode.shortLabel(), false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                CraftTerminalLayout.PIN_X, layout.visualTop() + 3,
                CraftTerminalLayout.HEADER_BUTTON_SIZE,
                CraftTerminalLayout.HEADER_BUTTON_SIZE,
                "P", searchPinned, mouseX, mouseY);
        renderRowButton(graphics, font, left, top, layout, "+", layout.visualTop() + 1,
                layout.rows() < maximumRows, mouseX, mouseY);
        renderRowButton(graphics, font, left, top, layout, "−", layout.visualTop() + 9,
                layout.rows() > CraftTerminalLayout.MIN_ROWS, mouseX, mouseY);
    }

    private static void renderStorageGrid(
            GuiGraphics graphics, Font font, int left, int top,
            CraftTerminalLayout layout, CraftTerminalScrollState scrollState,
            int mouseX, int mouseY) {
        int cellCount = layout.rows() * CraftTerminalLayout.COLUMNS;
        for (int cell = 0; cell < cellCount; cell++) {
            int column = cell % CraftTerminalLayout.COLUMNS;
            int row = cell / CraftTerminalLayout.COLUMNS;
            int x = left + CraftTerminalLayout.GRID_X + column * CraftTerminalLayout.SLOT_SIZE;
            int y = top + layout.storageGridY() + row * CraftTerminalLayout.SLOT_SIZE;
            if (UiRect.contains(x, y, CraftTerminalLayout.SLOT_SIZE,
                    CraftTerminalLayout.SLOT_SIZE, mouseX, mouseY)) {
                graphics.fill(x, y, x + CraftTerminalLayout.SLOT_SIZE,
                        y + CraftTerminalLayout.SLOT_SIZE,
                        CraftTerminalStyle.SLOT_HOVER_BACKGROUND.toArgb());
            }
            StorageEntry entry = scrollState.entryAtVisibleCell(cell);
            if (entry == null) {
                continue;
            }
            graphics.renderItem(entry.stack(), x + 1, y + 1);
            RtsClientUiUtil.drawSlotCountOverlay(
                    graphics, font, x, y, CraftTerminalLayout.SLOT_SIZE,
                    RtsClientUiUtil.compactCount(entry.count()),
                    CraftTerminalStyle.COUNT_TEXT.toArgb());
        }
    }

    private static void renderScrollbar(
            GuiGraphics graphics, int left, int top,
            CraftTerminalLayout layout, CraftTerminalScrollState scrollState,
            int totalEntries) {
        int x = left + CraftTerminalLayout.SCROLLBAR_X;
        int y = top + layout.scrollbarY();
        int height = layout.scrollbarHeight();
        graphics.fill(x, y, x + CraftTerminalLayout.SCROLLBAR_WIDTH, y + height,
                CraftTerminalStyle.SCROLL_TRACK.toArgb());
        int thumbHeight = Math.max(10, (int) Math.round(height
                * scrollState.thumbFraction(totalEntries, layout.rows())));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + (int) Math.round(travel
                * scrollState.fraction(totalEntries, layout.rows()));
        graphics.fill(x + 1, thumbY, x + CraftTerminalLayout.SCROLLBAR_WIDTH - 1,
                thumbY + thumbHeight, CraftTerminalStyle.SCROLL_THUMB.toArgb());
    }

    private static void renderActionButtons(
            GuiGraphics graphics, Font font, int left, int top,
            CraftTerminalLayout layout, RtsStorageSort sort, boolean ascending,
            int mouseX, int mouseY) {
        renderSideButton(graphics, font, left, top,
                CLEAR_BUTTON_X, CLEAR_STORAGE_Y, SIDE_BUTTON_W, SIDE_BUTTON_H,
                "S", false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                CLEAR_BUTTON_X, CLEAR_PLAYER_Y, SIDE_BUTTON_W, SIDE_BUTTON_H,
                "I", false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                DEPOSIT_BUTTON_X, DEPOSIT_ALL_Y, SIDE_BUTTON_W, SIDE_BUTTON_H,
                "ALL", false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                DEPOSIT_BUTTON_X, DEPOSIT_HOTBAR_Y, SIDE_BUTTON_W, SIDE_BUTTON_H,
                "BAR", false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                TOOLBAR_X, layout.visualTop() + 1, TOOLBAR_W, TOOLBAR_H,
                sortLabel(sort), false, mouseX, mouseY);
        renderSideButton(graphics, font, left, top,
                TOOLBAR_X, layout.visualTop() + 16, TOOLBAR_W, TOOLBAR_H,
                ascending ? "↑" : "↓", false, mouseX, mouseY);
    }

    private static void renderRowButton(
            GuiGraphics graphics, Font font, int left, int top,
            CraftTerminalLayout layout, String label, int y, boolean enabled,
            int mouseX, int mouseY) {
        int x = CraftTerminalLayout.ROW_BUTTON_X;
        boolean hovered = UiRect.contains(left + x, top + y, 10, 7, mouseX, mouseY);
        UiColor fill = !enabled
                ? CraftTerminalStyle.CLEAR_BACKGROUND
                : (hovered ? CraftTerminalStyle.BUTTON_HOVER_BACKGROUND
                : CraftTerminalStyle.MINI_BUTTON_BACKGROUND);
        drawPanelFrame(graphics, font, left + x, top + y, 10, 7,
                fill, CraftTerminalStyle.BUTTON_BORDER_LIGHT, CraftTerminalStyle.BUTTON_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, label, left + x + 5, top + y - 1,
                enabled ? CraftTerminalStyle.BUTTON_TEXT.toArgb()
                        : CraftTerminalStyle.UNEDITABLE_TEXT.toArgb());
    }

    private static void renderSideButton(
            GuiGraphics graphics, Font font, int left, int top,
            int x, int y, int width, int height, String label, boolean active,
            int mouseX, int mouseY) {
        boolean hovered = UiRect.contains(left + x, top + y, width, height, mouseX, mouseY);
        UiColor fill = active
                ? CraftTerminalStyle.BUTTON_ACTIVE_BACKGROUND
                : (hovered ? CraftTerminalStyle.BUTTON_HOVER_BACKGROUND
                : CraftTerminalStyle.MINI_BUTTON_BACKGROUND);
        drawPanelFrame(graphics, font, left + x, top + y, width, height,
                fill, CraftTerminalStyle.BUTTON_BORDER_LIGHT, CraftTerminalStyle.BUTTON_BORDER_DARK);
        drawCenteredNoShadow(graphics, font, label,
                left + x + width / 2,
                top + y + Math.max(1, (height - font.lineHeight) / 2),
                CraftTerminalStyle.BUTTON_TEXT.toArgb());
    }

    private static void drawCenteredNoShadow(
            GuiGraphics graphics, Font font, String text, int centerX, int y, int color) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
    }

    private static void drawPanelFrame(
            GuiGraphics graphics, Font font, int x, int y, int width, int height,
            UiColor fill, UiColor light, UiColor dark) {
        UiChromeRenderer.frame(new MinecraftUiCanvas(graphics, font),
                new UiRect(x, y, width, height), 1.0D, fill, light, dark);
    }

    private static String sortLabel(RtsStorageSort sort) {
        return switch (sort) {
            case QUANTITY -> "Q";
            case MOD -> "M";
            case NAME -> "N";
        };
    }
}
