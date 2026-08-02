package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏工具行的纯 Java 布局与槽位命中模型。
 *
 * <p>本类统一热栏、空手槽、固定槽和固定槽翻页按钮的坐标。它只返回稳定索引，不读取玩家背包、
 * 物品数量或控制器状态，也不执行选择、导入、清除或存流体动作。</p>
 */
public final class BottomPanelToolLayout {
    public static final int HOTBAR_ITEM_SLOTS = 9;
    public static final int EMPTY_HAND_INDEX = HOTBAR_ITEM_SLOTS;
    public static final int PIN_GAP = 12;

    private final int x;
    private final int y;
    private final int width;
    private final int slotSize;
    private final int pitch;
    private final int hotbarCellCount;
    private final int hotbarWidth;
    private final int pinStartX;
    private final int visiblePinCells;
    private final boolean pinPager;
    private final int pinSlotsPerPage;
    private final int pinPageCount;
    private final int pinPage;
    private final int pinStartIndex;
    private final int totalPins;

    private BottomPanelToolLayout(int x, int y, int width, int slotSize, int pitch,
                                  int hotbarCellCount, int hotbarWidth,
                                  int pinStartX, int visiblePinCells,
                                  boolean pinPager, int pinSlotsPerPage,
                                  int pinPageCount, int pinPage,
                                  int pinStartIndex, int totalPins) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.slotSize = slotSize;
        this.pitch = pitch;
        this.hotbarCellCount = hotbarCellCount;
        this.hotbarWidth = hotbarWidth;
        this.pinStartX = pinStartX;
        this.visiblePinCells = visiblePinCells;
        this.pinPager = pinPager;
        this.pinSlotsPerPage = pinSlotsPerPage;
        this.pinPageCount = pinPageCount;
        this.pinPage = pinPage;
        this.pinStartIndex = pinStartIndex;
        this.totalPins = totalPins;
    }

    public static BottomPanelToolLayout resolve(int x, int y, int width,
                                                int hotbarItemSlots,
                                                int slotSize, int pitch,
                                                int pinGap, int totalPins,
                                                int requestedPinPage) {
        requirePositive(width, "width");
        requireNonNegative(hotbarItemSlots, "hotbarItemSlots");
        requirePositive(slotSize, "slotSize");
        if (pitch < slotSize) {
            throw new IllegalArgumentException("pitch must be at least slotSize");
        }
        requireNonNegative(pinGap, "pinGap");
        requireNonNegative(totalPins, "totalPins");

        int hotbarCellCount = hotbarItemSlots + 1;
        int hotbarWidth = pitch * hotbarCellCount - (pitch - slotSize);
        int pinStartX = x + hotbarWidth + pinGap;
        int rightBound = x + width;
        int pinCapacity = rightBound - pinStartX < slotSize
                ? 0
                : 1 + (rightBound - pinStartX - slotSize) / pitch;
        int visiblePinCells = Math.min(totalPins, pinCapacity);
        boolean pinPager = visiblePinCells >= 2 && totalPins > visiblePinCells;
        int pinSlotsPerPage = visiblePinCells <= 0
                ? 1
                : pinPager ? visiblePinCells - 1 : visiblePinCells;
        int pinPageCount = Math.max(1,
                (int) Math.ceil(totalPins / (double) pinSlotsPerPage));
        int pinPage = clamp(requestedPinPage, 0, pinPageCount - 1);
        return new BottomPanelToolLayout(x, y, width, slotSize, pitch,
                hotbarCellCount, hotbarWidth, pinStartX, visiblePinCells,
                pinPager, pinSlotsPerPage, pinPageCount, pinPage,
                 pinPage * pinSlotsPerPage, totalPins);
    }

    /**
     * 使用主线底栏约定解析工具行，避免生产绘制、输入和离屏预览各自重复槽位参数。
     */
    public static BottomPanelToolLayout standard(
            int x, int y, int width, int totalPins, int requestedPinPage) {
        return resolve(
                x, y, width,
                HOTBAR_ITEM_SLOTS,
                RtsMainlineLayout.HOTBAR_SLOT,
                RtsMainlineLayout.HOTBAR_PITCH,
                PIN_GAP,
                totalPins,
                requestedPinPage);
    }

    public int y() {
        return y;
    }

    public int slotSize() {
        return slotSize;
    }

    /**
     * 工具行空白区域也会阻止点击穿透到世界；该命中范围与具体槽位命中分开维护。
     */
    public boolean containsRow(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + slotSize;
    }

    public int hotbarCellCount() {
        return hotbarCellCount;
    }

    public int hotbarCellX(int cell) {
        requireCell(cell, hotbarCellCount, "hotbar");
        return x + cell * pitch;
    }

    public int hotbarIndexAt(double mouseX, double mouseY) {
        return cellAt(x, hotbarCellCount, mouseX, mouseY);
    }

    public int hotbarWidth() {
        return hotbarWidth;
    }

    public int visiblePinCells() {
        return visiblePinCells;
    }

    public int pinCellX(int cell) {
        requireCell(cell, visiblePinCells, "pin");
        return pinStartX + cell * pitch;
    }

    public int pinCellAt(double mouseX, double mouseY) {
        return cellAt(pinStartX, visiblePinCells, mouseX, mouseY);
    }

    public boolean isPinPagerCell(int cell) {
        return pinPager && cell == visiblePinCells - 1;
    }

    public int pinIndexForCell(int cell) {
        if (cell < 0 || cell >= visiblePinCells || isPinPagerCell(cell)) {
            return -1;
        }
        int index = pinStartIndex + cell;
        return index < totalPins ? index : -1;
    }

    public int pinPage() {
        return pinPage;
    }

    public int pinPageCount() {
        return pinPageCount;
    }

    public int pinSlotsPerPage() {
        return pinSlotsPerPage;
    }

    private int cellAt(int startX, int cellCount, double mouseX, double mouseY) {
        if (cellCount <= 0 || mouseX < startX || mouseY < y || mouseY >= y + slotSize) {
            return -1;
        }
        int cell = (int) ((mouseX - startX) / pitch);
        if (cell < 0 || cell >= cellCount) {
            return -1;
        }
        int cellX = startX + cell * pitch;
        return mouseX < cellX + slotSize ? cell : -1;
    }

    private static void requireCell(int cell, int count, String name) {
        if (cell < 0 || cell >= count) {
            throw new IllegalArgumentException(name + " cell out of bounds: " + cell);
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
