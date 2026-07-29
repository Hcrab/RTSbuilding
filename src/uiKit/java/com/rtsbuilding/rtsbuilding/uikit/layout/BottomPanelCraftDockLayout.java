package com.rtsbuilding.rtsbuilding.uikit.layout;

/**
 * 底栏合成入口与八个远程 GUI 绑定槽的纯 Java 几何。
 *
 * <p>生产绘制、左右键输入和离屏预览必须共同消费本类，避免中央按钮与外围槽位各自维护一套
 * 偏移表。本类只负责半开区间命中和固定环形排列，不读取绑定内容，也不执行打开、清除或等待
 * 绑定等业务动作。</p>
 */
public final class BottomPanelCraftDockLayout {
    public static final int MAX_BINDING_COUNT = 8;
    public static final int CRAFT_BUTTON_SIZE = 18;
    public static final int BINDING_SLOT_SIZE = 10;
    public static final int BOUNDS_SIZE = 42;

    private static final int CENTER_OFFSET = (BOUNDS_SIZE - CRAFT_BUTTON_SIZE) / 2;
    private static final int BINDING_PITCH = (BOUNDS_SIZE - BINDING_SLOT_SIZE) / 2;
    private static final int[] SLOT_COLUMNS = {0, 1, 2, 0, 2, 0, 1, 2};
    private static final int[] SLOT_ROWS = {0, 0, 0, 1, 1, 2, 2, 2};

    public final Area bounds;
    public final Area craftButton;
    public final int bindingCount;

    private BottomPanelCraftDockLayout(Area bounds, Area craftButton, int bindingCount) {
        this.bounds = bounds;
        this.craftButton = craftButton;
        this.bindingCount = bindingCount;
    }

    /**
     * @param x 环形区域左边界
     * @param y 环形区域上边界
     * @param bindingCount 当前生产线实际提供的绑定槽数量
     */
    public static BottomPanelCraftDockLayout resolve(int x, int y, int bindingCount) {
        if (bindingCount < 0 || bindingCount > MAX_BINDING_COUNT) {
            throw new IllegalArgumentException(
                    "bindingCount must be between 0 and " + MAX_BINDING_COUNT);
        }
        Area bounds = new Area(x, y, BOUNDS_SIZE, BOUNDS_SIZE);
        Area craftButton = new Area(
                x + CENTER_OFFSET, y + CENTER_OFFSET,
                CRAFT_BUTTON_SIZE, CRAFT_BUTTON_SIZE);
        return new BottomPanelCraftDockLayout(bounds, craftButton, bindingCount);
    }

    public int slotX(int slot) {
        requireSlot(slot);
        return bounds.x + SLOT_COLUMNS[slot] * BINDING_PITCH;
    }

    public int slotY(int slot) {
        requireSlot(slot);
        return bounds.y + SLOT_ROWS[slot] * BINDING_PITCH;
    }

    /**
     * 只命中真实的 10px 绑定槽；槽间空白、中央合成按钮及右/下边界都返回 {@code -1}。
     */
    public int slotIndexAt(double mouseX, double mouseY) {
        if (!bounds.contains(mouseX, mouseY) || craftButton.contains(mouseX, mouseY)) {
            return -1;
        }
        for (int slot = 0; slot < bindingCount; slot++) {
            int slotX = slotX(slot);
            int slotY = slotY(slot);
            if (mouseX >= slotX && mouseX < slotX + BINDING_SLOT_SIZE
                    && mouseY >= slotY && mouseY < slotY + BINDING_SLOT_SIZE) {
                return slot;
            }
        }
        return -1;
    }

    private void requireSlot(int slot) {
        if (slot < 0 || slot >= bindingCount) {
            throw new IllegalArgumentException("binding slot out of bounds: " + slot);
        }
    }

    public static final class Area {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Area(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }
}
