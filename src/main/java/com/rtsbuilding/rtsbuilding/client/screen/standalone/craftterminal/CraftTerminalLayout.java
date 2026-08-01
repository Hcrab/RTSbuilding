package com.rtsbuilding.rtsbuilding.client.screen.standalone.craftterminal;

/**
 * 一体化合成终端的纯布局模型。
 *
 * <p>该类只计算坐标，不读取 Minecraft 状态、不渲染也不处理输入。菜单槽位按六行
 * 储存区固定在下半部；减少显示行数时，终端从顶部向下收缩，因此合成格和玩家背包
 * 不需要在运行中修改原版 {@code Slot} 的最终坐标。</p>
 */
public final class CraftTerminalLayout {
    public static final int WIDTH = 195;
    public static final int IMAGE_HEIGHT = 304;
    public static final int MIN_ROWS = 2;
    public static final int MAX_ROWS = 6;
    public static final int COLUMNS = 9;
    public static final int SLOT_SIZE = 18;
    public static final int HEADER_HEIGHT = 17;
    public static final int STORAGE_BOTTOM = HEADER_HEIGHT + MAX_ROWS * SLOT_SIZE;
    public static final int TERMINAL_BOTTOM = 209;
    public static final int INVENTORY_TOP = 211;

    public static final int GRID_X = 7;
    public static final int SEARCH_X = 57;
    public static final int SEARCH_WIDTH = 79;
    public static final int SEARCH_HEIGHT = 12;
    public static final int MODE_X = 139;
    public static final int PIN_X = 151;
    public static final int HEADER_BUTTON_SIZE = 10;
    public static final int ROW_BUTTON_X = 164;
    public static final int SCROLLBAR_X = 178;
    public static final int SCROLLBAR_WIDTH = 9;

    private int rows;

    public CraftTerminalLayout(int rows) {
        setRows(rows);
    }

    public int rows() {
        return this.rows;
    }

    public void setRows(int rows) {
        this.rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
    }

    public int visualTop() {
        return (MAX_ROWS - this.rows) * SLOT_SIZE;
    }

    public int storageGridY() {
        return visualTop() + HEADER_HEIGHT;
    }

    public int storageHeight() {
        return HEADER_HEIGHT + this.rows * SLOT_SIZE;
    }

    /** 从当前可见顶边到画布底边的真实高度，用于小窗口重新居中。 */
    public int visibleHeight() {
        return IMAGE_HEIGHT - visualTop();
    }

    public int searchY() {
        return visualTop() + 3;
    }

    public int scrollbarY() {
        return storageGridY();
    }

    public int scrollbarHeight() {
        return this.rows * SLOT_SIZE;
    }
}
