package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;

/**
 * 合成终端正式客户端与 Java 8 离屏截图共用的无状态 chrome 渲染器。
 *
 * <p>该类复刻项目美术概念图的槽位密度和面板层级，并借鉴成熟储存终端的模块化职责：
 * 它只画面板、槽位、滚动条和无文字图标；物品、数量、搜索文本、提示与网络动作仍由平台层负责。
 * 任何新按钮必须先进入 {@link CraftTerminalLayout}，不得在正式屏幕中另写坐标。</p>
 */
public final class CraftTerminalChromeRenderer {
    private CraftTerminalChromeRenderer() {
    }

    public static void render(
            UiCanvas2D canvas,
            CraftTerminalLayout.Geometry layout,
            CraftTerminalUiAction hoveredAction,
            int hoveredStorageCell,
            boolean searchFocused,
            boolean searchHasText,
            boolean searchPinned,
            int searchMode,
            int sortMode,
            boolean ascending,
            double scrollFraction,
            double thumbFraction) {
        if (canvas == null || layout == null) {
            throw new IllegalArgumentException("canvas and layout must not be null");
        }

        frame(canvas, layout.header, CraftTerminalStyle.HEADER);
        frame(canvas, new UiRect(0, layout.visualTop + 18,
                CraftTerminalLayout.WIDTH, 111 - layout.visualTop), CraftTerminalStyle.PANEL_ALT);
        frame(canvas, layout.craftingPanel, CraftTerminalStyle.PANEL);
        frame(canvas, layout.craftingContent, CraftTerminalStyle.PANEL);
        frame(canvas, layout.inventoryPanel, CraftTerminalStyle.PANEL_ALT);
        frame(canvas, layout.search, searchFocused
                ? CraftTerminalStyle.SLOT_HOVER : CraftTerminalStyle.SEARCH);

        for (int cell = 0; cell < layout.rows * CraftTerminalLayout.COLUMNS; cell++) {
            slot(canvas, layout.storageCell(cell), cell == hoveredStorageCell);
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                slot(canvas, new UiRect(
                        CraftTerminalLayout.CRAFT_GRID_X - 1 + column * CraftTerminalLayout.SLOT_SIZE,
                        CraftTerminalLayout.CRAFT_GRID_Y - 1 + row * CraftTerminalLayout.SLOT_SIZE,
                        CraftTerminalLayout.SLOT_SIZE, CraftTerminalLayout.SLOT_SIZE), false);
            }
        }
        frame(canvas, layout.resultFrame, CraftTerminalStyle.BORDER_MID);
        slot(canvas, new UiRect(CraftTerminalLayout.RESULT_X - 1,
                CraftTerminalLayout.RESULT_Y - 1,
                CraftTerminalLayout.SLOT_SIZE, CraftTerminalLayout.SLOT_SIZE), false);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                slot(canvas, new UiRect(
                        CraftTerminalLayout.INVENTORY_X - 1 + column * CraftTerminalLayout.SLOT_SIZE,
                        CraftTerminalLayout.INVENTORY_Y - 1 + row * CraftTerminalLayout.SLOT_SIZE,
                        CraftTerminalLayout.SLOT_SIZE, CraftTerminalLayout.SLOT_SIZE), false);
            }
        }
        for (int column = 0; column < 9; column++) {
            slot(canvas, new UiRect(
                    CraftTerminalLayout.INVENTORY_X - 1 + column * CraftTerminalLayout.SLOT_SIZE,
                    CraftTerminalLayout.HOTBAR_Y - 1,
                    CraftTerminalLayout.SLOT_SIZE, CraftTerminalLayout.SLOT_SIZE), false);
        }

        drawCraftArrow(canvas);
        drawScrollbar(canvas, layout.scrollbar, scrollFraction, thumbFraction);
        button(canvas, layout.searchMode, hoveredAction == CraftTerminalUiAction.SEARCH_MODE,
                searchMode != 0);
        drawSearchMode(canvas, layout.searchMode, searchMode);
        button(canvas, layout.searchPin, hoveredAction == CraftTerminalUiAction.SEARCH_PIN,
                searchPinned);
        drawPin(canvas, layout.searchPin);
        if (searchHasText) drawSearchClear(canvas, layout.searchClear);
        button(canvas, layout.cycleRows, hoveredAction == CraftTerminalUiAction.CYCLE_ROWS, false);
        drawRows(canvas, layout.cycleRows);
        button(canvas, layout.sort, hoveredAction == CraftTerminalUiAction.SORT, false);
        drawSort(canvas, layout.sort, sortMode);
        button(canvas, layout.sortDirection,
                hoveredAction == CraftTerminalUiAction.SORT_DIRECTION, false);
        drawDirection(canvas, layout.sortDirection, ascending);

        button(canvas, layout.clearToStorage,
                hoveredAction == CraftTerminalUiAction.CLEAR_TO_STORAGE, false);
        drawGridTransfer(canvas, layout.clearToStorage, true);
        button(canvas, layout.clearToInventory,
                hoveredAction == CraftTerminalUiAction.CLEAR_TO_INVENTORY, false);
        drawGridTransfer(canvas, layout.clearToInventory, false);
        button(canvas, layout.depositAll,
                hoveredAction == CraftTerminalUiAction.DEPOSIT_ALL, false);
        drawDeposit(canvas, layout.depositAll, true);
        button(canvas, layout.depositHotbar,
                hoveredAction == CraftTerminalUiAction.DEPOSIT_HOTBAR, false);
        drawDeposit(canvas, layout.depositHotbar, false);
    }

    private static void frame(UiCanvas2D canvas, UiRect bounds, UiColor fill) {
        UiChromeRenderer.frame(canvas, bounds, 1.0D, fill,
                CraftTerminalStyle.BORDER_LIGHT, CraftTerminalStyle.BORDER_DARK);
    }

    private static void slot(UiCanvas2D canvas, UiRect bounds, boolean hovered) {
        canvas.fill(bounds, CraftTerminalStyle.BORDER_DARK);
        canvas.fill(bounds.getX(), bounds.getY(), bounds.getWidth() - 1, 1,
                CraftTerminalStyle.BORDER_LIGHT);
        canvas.fill(bounds.getX(), bounds.getY(), 1, bounds.getHeight() - 1,
                CraftTerminalStyle.BORDER_LIGHT);
        canvas.fill(bounds.getX() + 1, bounds.getY() + 1,
                bounds.getWidth() - 2, bounds.getHeight() - 2,
                hovered ? CraftTerminalStyle.SLOT_HOVER : CraftTerminalStyle.SLOT);
    }

    private static void button(UiCanvas2D canvas, UiRect bounds, boolean hovered, boolean active) {
        frame(canvas, bounds, active ? CraftTerminalStyle.BUTTON_ACTIVE
                : hovered ? CraftTerminalStyle.BUTTON_HOVER : CraftTerminalStyle.BUTTON);
    }

    private static void drawScrollbar(
            UiCanvas2D canvas, UiRect bounds, double fraction, double thumbFraction) {
        canvas.fill(bounds, CraftTerminalStyle.SCROLL_TRACK);
        double safeThumb = Math.max(8.0D, Math.min(bounds.getHeight(),
                bounds.getHeight() * clamp01(thumbFraction)));
        double travel = Math.max(0.0D, bounds.getHeight() - safeThumb);
        canvas.fill(bounds.getX() + 2, bounds.getY() + travel * clamp01(fraction),
                Math.max(1.0D, bounds.getWidth() - 4), safeThumb,
                CraftTerminalStyle.SCROLL_THUMB);
    }

    private static void drawCraftArrow(UiCanvas2D canvas) {
        canvas.fill(85, 165, 20, 5, CraftTerminalStyle.BORDER_MID);
        canvas.fill(101, 162, 5, 11, CraftTerminalStyle.BORDER_MID);
        canvas.fill(106, 164, 3, 7, CraftTerminalStyle.BORDER_MID);
    }

    private static void drawSearchMode(UiCanvas2D canvas, UiRect b, int mode) {
        canvas.fill(b.getX() + 3, b.getY() + 3, 4, 4, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 6, b.getY() + 6, 2, 4, CraftTerminalStyle.ICON);
        if (mode > 0) canvas.fill(b.getX() + 2, b.getY() + 9, 6, 1, CraftTerminalStyle.ICON_MUTED);
    }

    private static void drawPin(UiCanvas2D canvas, UiRect b) {
        canvas.fill(b.getX() + 3, b.getY() + 3, 5, 2, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 5, b.getY() + 5, 1, 5, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 3, b.getY() + 7, 5, 1, CraftTerminalStyle.ICON);
    }

    private static void drawSearchClear(UiCanvas2D canvas, UiRect b) {
        canvas.fill(b.getX() + 4, b.getY() + 4, 1, 5, CraftTerminalStyle.ICON_MUTED);
        canvas.fill(b.getX() + 8, b.getY() + 4, 1, 5, CraftTerminalStyle.ICON_MUTED);
        canvas.fill(b.getX() + 5, b.getY() + 5, 3, 3, CraftTerminalStyle.ICON_MUTED);
    }

    private static void drawRows(UiCanvas2D canvas, UiRect b) {
        for (int i = 0; i < 3; i++) {
            canvas.fill(b.getX() + 3, b.getY() + 3 + i * 3, 7, 1, CraftTerminalStyle.ICON);
        }
        canvas.fill(b.getX() + 11, b.getY() + 5, 1, 5, CraftTerminalStyle.ICON_MUTED);
        canvas.fill(b.getX() + 9, b.getY() + 7, 5, 1, CraftTerminalStyle.ICON_MUTED);
    }

    private static void drawSort(UiCanvas2D canvas, UiRect b, int sortMode) {
        if (sortMode % 3 == 1) {
            canvas.fill(b.getX() + 2, b.getY() + 3, 2, 2, CraftTerminalStyle.ICON);
            canvas.fill(b.getX() + 6, b.getY() + 3, 2, 2, CraftTerminalStyle.ICON);
            canvas.fill(b.getX() + 2, b.getY() + 7, 2, 2, CraftTerminalStyle.ICON);
            canvas.fill(b.getX() + 6, b.getY() + 7, 2, 2, CraftTerminalStyle.ICON);
        } else {
            int longest = sortMode % 3 == 2 ? 4 : 6;
            canvas.fill(b.getX() + 2, b.getY() + 3, longest, 1, CraftTerminalStyle.ICON);
            canvas.fill(b.getX() + 2, b.getY() + 6, 4, 1, CraftTerminalStyle.ICON);
            canvas.fill(b.getX() + 2, b.getY() + 9, sortMode % 3 == 2 ? 6 : 2, 1,
                    CraftTerminalStyle.ICON);
        }
    }

    private static void drawDirection(UiCanvas2D canvas, UiRect b, boolean ascending) {
        double x = b.getX() + 5;
        canvas.fill(x, b.getY() + 3, 1, 7, CraftTerminalStyle.ICON);
        double y = ascending ? b.getY() + 3 : b.getY() + 8;
        canvas.fill(x - 2, y + (ascending ? 2 : 0), 5, 1, CraftTerminalStyle.ICON);
        canvas.fill(x - 1, y + (ascending ? 1 : 1), 3, 1, CraftTerminalStyle.ICON);
    }

    private static void drawGridTransfer(UiCanvas2D canvas, UiRect b, boolean toStorage) {
        double y = b.getY() + (toStorage ? 4 : 3);
        canvas.fill(b.getX() + 2, y, 3, 3, CraftTerminalStyle.ICON_MUTED);
        canvas.fill(b.getX() + 5, y + 1, 3, 1, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 7, y, 1, 3, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 2, b.getY() + 9, 6, 1, CraftTerminalStyle.ICON);
    }

    private static void drawDeposit(UiCanvas2D canvas, UiRect b, boolean all) {
        canvas.fill(b.getX() + 2, b.getY() + 3, all ? 6 : 2, 2, CraftTerminalStyle.ICON_MUTED);
        canvas.fill(b.getX() + 4, b.getY() + 6, 1, 3, CraftTerminalStyle.ICON);
        canvas.fill(b.getX() + 2, b.getY() + 8, 5, 2, CraftTerminalStyle.ICON);
    }

    private static double clamp01(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
