package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 插件管理页的共享几何。
 *
 * <p>这里只负责屏幕内的稳定区域、物品槽和已安装列表窗口，不接触背包、网络或安装规则。
 * 生产绘制与命中检测必须消费同一个 {@link Layout}，避免调整面板尺寸后出现“看得到但点不到”。
 */
public final class PluginManagementLayout {
    public static final int PANEL_MAX_W = 430;
    public static final int PANEL_MAX_H = 246;
    public static final int PANEL_MIN_W = 300;
    public static final int PANEL_MIN_H = 214;
    public static final int PAD = 12;
    public static final int HEADER_H = 27;
    public static final int SLOT = 18;
    public static final int INVENTORY_COLS = 9;
    public static final int INSTALLED_ROW_H = 26;
    public static final int INVENTORY_ROWS = 4;
    public static final int INSTALL_H = 46;
    public static final int FRAME_INSET = 1;
    public static final int HEADER_TITLE_TOP = 10;
    public static final int CURSOR_ITEM_OFFSET = 8;
    public static final int SURFACE_TITLE_X = 7;
    public static final int SURFACE_TITLE_Y = 7;
    public static final int TEAM_TITLE_Y = 18;
    public static final int CONTENT_TEXT_X = 8;
    public static final int CONTENT_HORIZONTAL_INSET = 8;
    public static final int EMPTY_WITH_TEAM_Y = 38;
    public static final int EMPTY_WITHOUT_TEAM_Y = 28;
    public static final int INSTALLED_BOTTOM_INSET = 4;
    public static final int ROW_HORIZONTAL_INSET = 4;
    public static final int ROW_BOTTOM_INSET = 2;
    public static final int ROW_ITEM_X = 7;
    public static final int ROW_ITEM_Y = 4;
    public static final int ROW_TEXT_X = 28;
    public static final int ROW_NAME_Y = 5;
    public static final int ROW_STATUS_Y = 16;
    public static final int ROW_NAME_RIGHT_RESERVE = 76;
    public static final int ROW_STATUS_RIGHT_RESERVE = 82;
    public static final int UNINSTALL_RIGHT_INSET = 50;
    public static final int UNINSTALL_TOP = 5;
    public static final int UNINSTALL_W = 44;
    public static final int UNINSTALL_H = 16;
    public static final int UNINSTALL_TEXT_X = 22;
    public static final int UNINSTALL_TEXT_Y = 9;
    public static final int REFRESH_W = 52;
    public static final int REFRESH_H = 16;
    public static final int REFRESH_RIGHT_INSET = 7;
    public static final int REFRESH_TOP = 5;
    public static final int REFRESH_TEXT_TOP = 4;
    public static final int INSTALL_TITLE_Y = 7;
    public static final int INSTALL_HINT_Y = 22;
    public static final int SLOT_CONTENT_INSET = 1;
    public static final int SCROLL_MIN_H = 12;
    public static final int SCROLL_BOTTOM_INSET = 6;
    public static final int SCROLL_RIGHT_INSET = 8;

    private PluginManagementLayout() {
    }

    public static Layout resolve(int screenWidth, int screenHeight) {
        int panelW = Math.min(PANEL_MAX_W, Math.max(PANEL_MIN_W, screenWidth - 20));
        int panelH = Math.min(PANEL_MAX_H, Math.max(PANEL_MIN_H, screenHeight - 42));
        int panelX = (screenWidth - panelW) / 2;
        int panelY = Math.max(10, (screenHeight - panelH) / 2 - 6);
        Rect panel = new Rect(panelX, panelY, panelW, panelH);

        int leftX = panelX + PAD;
        int contentY = panelY + HEADER_H + 8;
        int splitWidth = panelW - PAD * 3;
        int inventoryGridW = INVENTORY_COLS * SLOT;
        // 窄屏优先保证九列背包格完整留在面板内，已安装列表仍保留可读的最小宽度。
        int leftW = Math.min(184, Math.max(96, splitWidth - inventoryGridW));
        int rightX = leftX + leftW + PAD;
        int rightW = panelX + panelW - PAD - rightX;
        Rect installed = new Rect(leftX, contentY, leftW, panelH - HEADER_H - 48);
        Rect install = new Rect(rightX, contentY, rightW, INSTALL_H);
        int gridW = inventoryGridW;
        int gridX = rightX + Math.max(0, (rightW - gridW) / 2);
        int inventoryTitleY = contentY + 60;
        Rect inventoryGrid = new Rect(gridX, inventoryTitleY + 14,
                gridW, INVENTORY_ROWS * SLOT);
        Rect back = new Rect(screenWidth - 86, screenHeight - 28, 74, 20);
        return new Layout(panel, installed, install, inventoryGrid, back, rightX, rightW, inventoryTitleY);
    }

    public static InstalledRows installedRows(Layout layout, boolean hasTeam, int totalRows, int requestedScroll) {
        int firstRowY = layout.installed.y + (hasTeam ? 34 : 24);
        int visibleRows = Math.max(1,
                (layout.installed.bottom() - INSTALLED_BOTTOM_INSET - firstRowY) / INSTALLED_ROW_H);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        int scroll = Math.max(0, Math.min(requestedScroll, maxScroll));
        return new InstalledRows(firstRowY, visibleRows, maxScroll, scroll);
    }

    public static Rect inventorySlot(Layout layout, int displayIndex) {
        if (displayIndex < 0 || displayIndex >= INVENTORY_COLS * INVENTORY_ROWS) {
            throw new IllegalArgumentException("物品槽显示序号越界: " + displayIndex);
        }
        return new Rect(
                layout.inventoryGrid.x + (displayIndex % INVENTORY_COLS) * SLOT,
                layout.inventoryGrid.y + (displayIndex / INVENTORY_COLS) * SLOT,
                SLOT,
                SLOT);
    }

    public static int contentWidth(int width) {
        return width - CONTENT_HORIZONTAL_INSET * 2;
    }

    public static boolean installedRowFits(Rect installed, int rowY) {
        return rowY + INSTALLED_ROW_H <= installed.bottom() - INSTALLED_BOTTOM_INSET;
    }

    public static final class Layout {
        public final Rect panel;
        public final Rect installed;
        public final Rect install;
        public final Rect inventoryGrid;
        public final Rect back;
        public final int inventoryTitleX;
        public final int inventoryTitleWidth;
        public final int inventoryTitleY;

        Layout(Rect panel, Rect installed, Rect install, Rect inventoryGrid, Rect back,
               int inventoryTitleX, int inventoryTitleWidth, int inventoryTitleY) {
            this.panel = panel;
            this.installed = installed;
            this.install = install;
            this.inventoryGrid = inventoryGrid;
            this.back = back;
            this.inventoryTitleX = inventoryTitleX;
            this.inventoryTitleWidth = inventoryTitleWidth;
            this.inventoryTitleY = inventoryTitleY;
        }
    }

    /** 仅承载像素对齐后的整数区域，命中语义采用右下半开区间。 */
    public static final class Rect {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        Rect(int x, int y, int width, int height) {
            if (width < 0 || height < 0) {
                throw new IllegalArgumentException("区域尺寸不能为负数");
            }
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Rect)) return false;
            Rect rect = (Rect) other;
            return x == rect.x && y == rect.y && width == rect.width && height == rect.height;
        }

        @Override
        public int hashCode() {
            int result = x;
            result = 31 * result + y;
            result = 31 * result + width;
            return 31 * result + height;
        }
    }

    public static final class InstalledRows {
        public final int firstRowY;
        public final int visibleRows;
        public final int maxScroll;
        public final int scroll;

        InstalledRows(int firstRowY, int visibleRows, int maxScroll, int scroll) {
            this.firstRowY = firstRowY;
            this.visibleRows = visibleRows;
            this.maxScroll = maxScroll;
            this.scroll = scroll;
        }
    }
}
