package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 一体化合成终端唯一的纯 Java 几何契约。
 *
 * <p>尺寸取自美术概念图的 194×303 主面板，并采用 AE2/Tom's 已验证的可拼接终端结构：
 * 顶部标题与搜索、可变行储存网格、固定合成区、固定玩家背包。该类明确不渲染物品、
 * 不发送网络包，也不读取 Minecraft 状态；正式客户端、输入命中和 Java 8 离屏预览必须
 * 共用同一个 {@link Geometry}。</p>
 */
public final class CraftTerminalLayout {
    public static final int WIDTH = 194;
    public static final int IMAGE_HEIGHT = 303;
    public static final int VISIBLE_WIDTH = 212;
    public static final int MIN_ROWS = 2;
    public static final int MAX_ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SLOT_SIZE = 18;

    public static final int CRAFT_GRID_X = 26;
    public static final int CRAFT_GRID_Y = 146;
    public static final int RESULT_X = 116;
    public static final int RESULT_Y = 162;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 222;
    public static final int HOTBAR_Y = 279;

    private CraftTerminalLayout() {
    }

    public static Geometry geometry(int requestedRows) {
        int rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, requestedRows));
        int visualTop = (MAX_ROWS - rows) * SLOT_SIZE;
        UiRect header = new UiRect(0, visualTop, WIDTH, 17);
        UiRect search = new UiRect(80, visualTop + 2, 89, 13);
        UiRect storageGrid = new UiRect(7, visualTop + 19,
                COLUMNS * SLOT_SIZE, rows * SLOT_SIZE);
        UiRect scrollbar = new UiRect(176, visualTop + 20, 13,
                Math.max(16, rows * SLOT_SIZE - 2));

        return new Geometry(rows, visualTop, header, search, storageGrid, scrollbar,
                new UiRect(169, visualTop + 2, 11, 13),
                new UiRect(181, visualTop + 2, 11, 13),
                new UiRect(197, visualTop + 2, 15, 13),
                new UiRect(197, visualTop + 20, 10, 13),
                new UiRect(197, visualTop + 35, 10, 13),
                new UiRect(0, 129, WIDTH, 83),
                new UiRect(3, 132, 188, 78),
                new UiRect(25, 145, 58, 58),
                new UiRect(113, 159, 32, 30),
                new UiRect(197, 174, 10, 13),
                new UiRect(197, 190, 10, 13),
                new UiRect(0, 220, WIDTH, 83),
                new UiRect(197, 222, 10, 13),
                new UiRect(197, 238, 10, 13),
                new UiRect(0, visualTop, VISIBLE_WIDTH, IMAGE_HEIGHT - visualTop));
    }

    public static final class Geometry {
        public final int rows;
        public final int visualTop;
        public final UiRect header;
        public final UiRect search;
        public final UiRect searchClear;
        public final UiRect storageGrid;
        public final UiRect scrollbar;
        public final UiRect searchMode;
        public final UiRect searchPin;
        public final UiRect cycleRows;
        public final UiRect sort;
        public final UiRect sortDirection;
        public final UiRect craftingPanel;
        public final UiRect craftingContent;
        public final UiRect craftingGridFrame;
        public final UiRect resultFrame;
        public final UiRect clearToStorage;
        public final UiRect clearToInventory;
        public final UiRect inventoryPanel;
        public final UiRect depositAll;
        public final UiRect depositHotbar;
        public final UiRect visibleTerminal;

        private Geometry(
                int rows,
                int visualTop,
                UiRect header,
                UiRect search,
                UiRect storageGrid,
                UiRect scrollbar,
                UiRect searchMode,
                UiRect searchPin,
                UiRect cycleRows,
                UiRect sort,
                UiRect sortDirection,
                UiRect craftingPanel,
                UiRect craftingContent,
                UiRect craftingGridFrame,
                UiRect resultFrame,
                UiRect clearToStorage,
                UiRect clearToInventory,
                UiRect inventoryPanel,
                UiRect depositAll,
                UiRect depositHotbar,
                UiRect visibleTerminal) {
            this.rows = rows;
            this.visualTop = visualTop;
            this.header = header;
            this.search = search;
            this.searchClear = new UiRect(search.right() - 12, search.getY(), 12, search.getHeight());
            this.storageGrid = storageGrid;
            this.scrollbar = scrollbar;
            this.searchMode = searchMode;
            this.searchPin = searchPin;
            this.cycleRows = cycleRows;
            this.sort = sort;
            this.sortDirection = sortDirection;
            this.craftingPanel = craftingPanel;
            this.craftingContent = craftingContent;
            this.craftingGridFrame = craftingGridFrame;
            this.resultFrame = resultFrame;
            this.clearToStorage = clearToStorage;
            this.clearToInventory = clearToInventory;
            this.inventoryPanel = inventoryPanel;
            this.depositAll = depositAll;
            this.depositHotbar = depositHotbar;
            this.visibleTerminal = visibleTerminal;
        }

        public int visibleHeight() {
            return IMAGE_HEIGHT - visualTop;
        }

        public UiRect storageCell(int cell) {
            if (cell < 0 || cell >= rows * COLUMNS) {
                throw new IllegalArgumentException("storage cell outside visible grid: " + cell);
            }
            return new UiRect(storageGrid.getX() + (cell % COLUMNS) * SLOT_SIZE,
                    storageGrid.getY() + (cell / COLUMNS) * SLOT_SIZE,
                    SLOT_SIZE, SLOT_SIZE);
        }

        public int storageCellAt(double x, double y) {
            if (!storageGrid.contains(x, y)) {
                return -1;
            }
            int column = (int) ((x - storageGrid.getX()) / SLOT_SIZE);
            int row = (int) ((y - storageGrid.getY()) / SLOT_SIZE);
            return row * COLUMNS + column;
        }

        /** 按从最具体到最宽泛的顺序解析半开命中区域。 */
        public CraftTerminalUiAction actionAt(double x, double y) {
            if (searchClear.contains(x, y)) return CraftTerminalUiAction.SEARCH_CLEAR;
            if (search.contains(x, y)) return CraftTerminalUiAction.SEARCH;
            if (searchMode.contains(x, y)) return CraftTerminalUiAction.SEARCH_MODE;
            if (searchPin.contains(x, y)) return CraftTerminalUiAction.SEARCH_PIN;
            if (cycleRows.contains(x, y)) return CraftTerminalUiAction.CYCLE_ROWS;
            if (sort.contains(x, y)) return CraftTerminalUiAction.SORT;
            if (sortDirection.contains(x, y)) return CraftTerminalUiAction.SORT_DIRECTION;
            if (clearToStorage.contains(x, y)) return CraftTerminalUiAction.CLEAR_TO_STORAGE;
            if (clearToInventory.contains(x, y)) return CraftTerminalUiAction.CLEAR_TO_INVENTORY;
            if (depositAll.contains(x, y)) return CraftTerminalUiAction.DEPOSIT_ALL;
            if (depositHotbar.contains(x, y)) return CraftTerminalUiAction.DEPOSIT_HOTBAR;
            if (scrollbar.contains(x, y)) return CraftTerminalUiAction.SCROLLBAR;
            return null;
        }
    }
}
