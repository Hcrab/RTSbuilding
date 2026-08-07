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
    /** PR #133 原始终端主体的真实像素尺寸；不得再用近似尺寸重画。 */
    public static final int WIDTH = 195;
    public static final int IMAGE_HEIGHT = 304;
    /** 主面板加右侧 24×24 排序按钮轨道的完整可见宽度。 */
    public static final int VISIBLE_WIDTH = CraftTerminalSortControlsLayout.BUTTON_X
            + CraftTerminalSortControlsLayout.BUTTON_WIDTH;
    public static final int MIN_ROWS = 2;
    public static final int MAX_ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SLOT_SIZE = 18;
    /** 图 2 提供的滚动条滑块本体，位于贡献者 512×512 图集中的原始像素区域。 */
    public static final int SCROLLBAR_HANDLE_WIDTH = 10;
    public static final int SCROLLBAR_HANDLE_HEIGHT = 15;
    private static final UiRect SCROLLBAR_HANDLE_SOURCE = new UiRect(
            197, 20, SCROLLBAR_HANDLE_WIDTH, SCROLLBAR_HANDLE_HEIGHT);

    public static final int CRAFT_GRID_X = 28;
    public static final int CRAFT_GRID_Y = 147;
    public static final int RESULT_X = 122;
    public static final int RESULT_Y = 165;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 222;
    public static final int HOTBAR_Y = 280;

    private CraftTerminalLayout() {
    }

    /**
     * 返回原版工作台菜单槽位在终端皮肤中的 X 坐标。
     *
     * <p>菜单槽位编号保持原版顺序；服务端和客户端在创建替换槽位时都必须经过这里，
     * 不能再在构造后修改 {@code Slot.x}。</p>
     */
    public static int menuSlotX(int menuSlot) {
        if (menuSlot == 0) {
            return RESULT_X;
        }
        if (menuSlot >= 1 && menuSlot <= 9) {
            return CRAFT_GRID_X + (menuSlot - 1) % 3 * SLOT_SIZE;
        }
        if (menuSlot >= 10 && menuSlot <= 36) {
            return INVENTORY_X + (menuSlot - 10) % COLUMNS * SLOT_SIZE;
        }
        if (menuSlot >= 37 && menuSlot <= 45) {
            return INVENTORY_X + (menuSlot - 37) * SLOT_SIZE;
        }
        throw new IllegalArgumentException("unsupported crafting terminal menu slot: " + menuSlot);
    }

    /** 返回原版工作台菜单槽位在终端皮肤中的 Y 坐标。 */
    public static int menuSlotY(int menuSlot) {
        if (menuSlot == 0) {
            return RESULT_Y;
        }
        if (menuSlot >= 1 && menuSlot <= 9) {
            return CRAFT_GRID_Y + (menuSlot - 1) / 3 * SLOT_SIZE;
        }
        if (menuSlot >= 10 && menuSlot <= 36) {
            return INVENTORY_Y + (menuSlot - 10) / COLUMNS * SLOT_SIZE;
        }
        if (menuSlot >= 37 && menuSlot <= 45) {
            return HOTBAR_Y;
        }
        throw new IllegalArgumentException("unsupported crafting terminal menu slot: " + menuSlot);
    }

    public static Geometry geometry(int requestedRows) {
        int rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, requestedRows));
        int visualTop = (MAX_ROWS - rows) * SLOT_SIZE;
        UiRect header = new UiRect(0, visualTop, WIDTH, 18);
        UiRect search = new UiRect(80, visualTop + 3, 89, 12);
        UiRect storageGrid = new UiRect(7, visualTop + 19,
                COLUMNS * SLOT_SIZE, rows * SLOT_SIZE);
        UiRect scrollbar = new UiRect(175, visualTop + 19, 12,
                rows * SLOT_SIZE);

        CraftTerminalSortControlsLayout.Geometry sortControls =
                CraftTerminalSortControlsLayout.resolve(visualTop);
        UiRect utility = new UiRect(197, 190, 8, 12);
        return new Geometry(rows, visualTop, header, search, storageGrid, scrollbar,
                new UiRect(169, visualTop + 2, 11, 13),
                new UiRect(181, visualTop + 2, 11, 13),
                new UiRect(197, visualTop + 2, 15, 13),
                sortControls,
                new UiRect(0, 129, WIDTH, 84),
                new UiRect(3, 132, 188, 78),
                new UiRect(24, 143, 60, 60),
                new UiRect(114, 158, 32, 31),
                utility,
                utility,
                new UiRect(0, 220, WIDTH, 84),
                utility,
                utility,
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
        public final CraftTerminalSortControlsLayout.Geometry sortControls;
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
                CraftTerminalSortControlsLayout.Geometry sortControls,
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
            this.sortControls = sortControls;
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

        /**
         * 返回贡献者终端纹理的 1:1 搬运切片。
         *
         * <p>主体与固定合成区永不拉伸；减少储存行数时，只移动顶栏并裁掉最上方槽行。
         * 新增功能所需、原概念图未画出的按钮，复用原图已有按钮皮肤，避免重新手绘一套
         * 不同质感的 chrome。正式 Minecraft 渲染与离屏预览必须共同消费这组切片。</p>
         */
        public TextureSlice[] skinSlices() {
            int storageBodyHeight = 111 - visualTop;
            return new TextureSlice[] {
                    slice(0, 0, WIDTH, 18, 0, visualTop),
                    slice(0, 18 + visualTop, WIDTH, storageBodyHeight,
                            0, 18 + visualTop),
                    slice(0, 129, WIDTH, 175, 0, 129),
                    slice(197, 2, 15, 13, 197, visualTop + 2),
                    slice(197, 190, 8, 12, 197, 190)
            };
        }

        /**
         * 按当前滚动比例返回轨道内的滑块矩形。
         *
         * <p>滑块保持图 2 的 10×15 原始尺寸，只在轨道内部移动；0 位于顶端，1 位于
         * 底端。短行数布局和六行布局共用同一算法，因此滑块永远不会越出预烘焙轨道。</p>
         */
        public UiRect scrollbarHandle(double fraction) {
            double safeFraction = Math.max(0.0D, Math.min(1.0D, fraction));
            double travel = Math.max(0.0D,
                    scrollbar.getHeight() - SCROLLBAR_HANDLE_HEIGHT);
            double targetY = scrollbar.getY() + Math.round(travel * safeFraction);
            return new UiRect(
                    scrollbar.getX() + (scrollbar.getWidth() - SCROLLBAR_HANDLE_WIDTH) / 2.0D,
                    targetY,
                    SCROLLBAR_HANDLE_WIDTH,
                    SCROLLBAR_HANDLE_HEIGHT);
        }

        /** 把图 2 的滑块原像素映射到当前滚动位置。 */
        public TextureSlice scrollbarHandleSlice(double fraction) {
            return new TextureSlice(SCROLLBAR_HANDLE_SOURCE, scrollbarHandle(fraction));
        }

        /**
         * 把轨道内的鼠标 Y 反解为滚动比例；{@code handleGrabOffset} 保留玩家抓住
         * 滑块内部的位置，避免按下时滑块中心跳到指针下方。
         */
        public double scrollbarFractionForPointer(double relativeMouseY, double handleGrabOffset) {
            double travel = Math.max(1.0D,
                    scrollbar.getHeight() - SCROLLBAR_HANDLE_HEIGHT);
            double fraction = (relativeMouseY - scrollbar.getY() - handleGrabOffset) / travel;
            return Math.max(0.0D, Math.min(1.0D, fraction));
        }

        private static TextureSlice slice(
                int sourceX, int sourceY, int width, int height,
                int targetX, int targetY) {
            return new TextureSlice(
                    new UiRect(sourceX, sourceY, width, height),
                    new UiRect(targetX, targetY, width, height));
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
            CraftTerminalUiAction sortAction = this.sortControls.actionAt(x, y);
            if (sortAction != null) return sortAction;
            if (clearToStorage.contains(x, y)) return CraftTerminalUiAction.CLEAR_TO_STORAGE;
            if (clearToInventory.contains(x, y)) return CraftTerminalUiAction.CLEAR_TO_INVENTORY;
            if (depositAll.contains(x, y)) return CraftTerminalUiAction.DEPOSIT_ALL;
            if (depositHotbar.contains(x, y)) return CraftTerminalUiAction.DEPOSIT_HOTBAR;
            if (scrollbar.contains(x, y)) return CraftTerminalUiAction.SCROLLBAR;
            return null;
        }

        /** 返回动作的正式命中矩形，供平滑视觉反馈与输入共用同一几何。 */
        public UiRect actionBounds(CraftTerminalUiAction action) {
            if (action == null) {
                throw new IllegalArgumentException("action must not be null");
            }
            switch (action) {
                case SEARCH:
                    return search;
                case SEARCH_CLEAR:
                    return searchClear;
                case SEARCH_MODE:
                    return searchMode;
                case SEARCH_PIN:
                    return searchPin;
                case CYCLE_ROWS:
                    return cycleRows;
                case SORT:
                case SORT_DIRECTION:
                    return sortControls.bounds(action);
                case CLEAR_TO_STORAGE:
                    return clearToStorage;
                case CLEAR_TO_INVENTORY:
                    return clearToInventory;
                case DEPOSIT_ALL:
                    return depositAll;
                case DEPOSIT_HOTBAR:
                    return depositHotbar;
                case SCROLLBAR:
                    return scrollbar;
                default:
                    throw new IllegalArgumentException("unsupported action: " + action);
            }
        }
    }

    /** 一块保持原始像素尺寸的终端纹理源/目标映射。 */
    public static final class TextureSlice {
        public final UiRect source;
        public final UiRect target;

        private TextureSlice(UiRect source, UiRect target) {
            this.source = source;
            this.target = target;
        }
    }
}
