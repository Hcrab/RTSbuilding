package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 下栏右嵌层——以网格统一显示绑定容器内的物品与流体。
 *
 * <p>完全参考 AE2 ME 终端的网格渲染方式：等间距格子排列、物品图标居中显示、
 * 数量文本以缩放方式渲染于格子右下角（参考 {@code StackSizeRenderer} 的 0.666x 缩放逻辑）、
 * 超过可视区域时右侧出现纵向滚动条。
 *
 * <p>物品与流体合并为同一连续列表，按「物品 → 流体」顺序平铺显示。
 */
public final class RightDownOverlayLayer extends DownOverlayLayer {

    // ======================== 布局常量 ========================

    /** 每个格子的尺寸（宽高一致） */
    private static final int SLOT_SIZE = 18;
    /** 格子之间的间距 */
    private static final int SLOT_GAP = 2;
    /** 内边距（距 overlay 左/上边缘） */
    private static final int PAD_LEFT = 4;
    private static final int PAD_TOP = 2;
    /** 右侧为滚动条预留的宽度 */
    private static final int SCROLLBAR_W = 7;
    /** 滚动条右侧留白 */
    private static final int RIGHT_MARGIN = 1;

    /** 物品图标在格子内的偏移（居中，16×16 图标在 18×18 格子中上下各 1px） */
    private static final int ICON_OFFSET = 1;

    /** 数量文本缩放系数（参考 AE2 StackSizeRenderer 默认值 0.666f） */
    private static final float AMOUNT_SCALE = 0.666f;
    /** 缩放倒数 */
    private static final float INV_AMOUNT_SCALE = 1.0f / AMOUNT_SCALE;
    /** 数量文本相对于格子右下角的偏移 X 调整（缩放空间坐标） */
    private static final int AMOUNT_OFFSET_X = -2;
    /** 数量文本相对于格子右下角的偏移 Y 调整 */
    private static final int AMOUNT_OFFSET_Y = -2;

    // ======================== 颜色 ========================

    /** 格子背景色（深色半透明） */
    private static final int SLOT_BG = 0x40_000000;
    /** 格子边框色 */
    private static final int SLOT_BORDER = 0xFF_303030;
    /** 物品数量文本颜色 */
    private static final int AMOUNT_COLOR = 0xFF_FFFFFF;
    /** 流体数量文本颜色（浅蓝色调，与物品区分） */
    private static final int FLUID_AMOUNT_COLOR = 0xFF_80C8FF;
    /** 无数据时提示文本颜色 */
    private static final int HINT_COLOR = 0x60_FFFFFF;

    // ======================== 组件 ========================

    private final ScrollBar scrollBar = new ScrollBar();

    /** 当前帧合并后的显示条目列表（物品+流体） */
    private final List<SlotEntry> slotEntries = new ArrayList<>();
    /** 当前帧的列数 */
    private int cols;
    /** 当前帧的行数 */
    private int rows;
    /** 当前帧每行可用的实际宽度（用于计算列数） */
    private int usableW;

    // ======================== 内部数据结构 ========================

    /**
     * 网格中每个槽位的数据封装。
     */
    private record SlotEntry(ItemStack stack, long count, boolean isFluid) {
    }

    // ======================== 核心渲染入口 ========================

    @Override
    protected void renderContent(GuiGraphics g) {
        // ---- 获取数据源 ----
        StorageModule sm = RtsClientKernel.get().module(StorageModule.class);
        if (sm == null) return;

        var storedItems = sm.getEntries();
        var storedFluids = sm.getFluidEntries();

        // ---- 合并物品与流体为 SlotEntry 列表 ----
        buildSlotEntries(storedItems, storedFluids);
        if (slotEntries.isEmpty()) {
            renderEmptyHint(g);
            return;
        }

        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        // ---- 布局计算 ----
        Minecraft mc = Minecraft.getInstance();
        usableW = w - PAD_LEFT - SCROLLBAR_W - RIGHT_MARGIN;
        cols = Math.max(1, (usableW + SLOT_GAP) / (SLOT_SIZE + SLOT_GAP));
        rows = (slotEntries.size() + cols - 1) / cols;
        int visibleH = h - PAD_TOP * 2;
        int gridH = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        // ---- 更新滚动条 ----
        scrollBar.setContent(gridH, visibleH);
        int scroll = scrollBar.getScroll();

        // ---- 逐格子渲染 ----
        int originX = x + PAD_LEFT;
        int originY = y + PAD_TOP;
        int fontColor = ThemeManager.getTextColor();
        int mouseX = getLastMouseX();
        int mouseY = getLastMouseY();
        int hoveredSlot = findHoveredSlot(mouseX, mouseY, originX, originY, scroll);

        for (int i = 0; i < slotEntries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;

            // 裁剪：跳过完全不可见的行
            if (slotY + SLOT_SIZE < originY || slotY > originY + visibleH) continue;

            SlotEntry entry = slotEntries.get(i);
            boolean hovered = (i == hoveredSlot);
            renderSlot(g, slotX, slotY, entry, hovered, fontColor);
        }

        // ---- 滚动条 ----
        renderScrollbar(g, x, y, h);
    }

    // ======================== 合并条目 ========================

    /**
     * 将 StorageEntry 和 FluidEntry 合并为统一的 SlotEntry 列表。
     */
    @SuppressWarnings("unchecked")
    private void buildSlotEntries(List<?> items, List<?> fluids) {
        slotEntries.clear();
        for (Object obj : items) {
            if (obj instanceof StorageEntry se) {
                if (se.stack() == null || se.stack().isEmpty()) continue;
                slotEntries.add(new SlotEntry(se.stack(), se.count(), false));
            }
        }
        for (Object obj : fluids) {
            if (obj instanceof FluidEntry fe) {
                if (fe.preview() == null || fe.preview().isEmpty()) continue;
                slotEntries.add(new SlotEntry(fe.preview(), fe.amount(), true));
            }
        }
    }

    // ======================== 单格子渲染 ========================

    /**
     * 渲染单个格子——背景 + 物品图标 + 数量叠加层（参考 AE2 StackSizeRenderer 风格）。
     *
     * <p>AE2 参考：{@code MEStorageScreen.extractSlot()} 和
     * {@code StackSizeRenderer.renderSizeLabel()} 的实现思路。</p>
     */
    private void renderSlot(GuiGraphics g, int slotX, int slotY, SlotEntry entry,
                            boolean hovered, int fontColor) {
        Minecraft mc = Minecraft.getInstance();

        // ---- 格子背景 + 边框 ----
        int bg = hovered ? (SLOT_BG | 0x40_000000) : SLOT_BG;
        g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, bg);
        // 上边框
        g.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + 1, SLOT_BORDER);
        // 下边框
        g.fill(slotX, slotY + SLOT_SIZE - 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BORDER);
        // 左边框
        g.fill(slotX, slotY, slotX + 1, slotY + SLOT_SIZE, SLOT_BORDER);
        // 右边框
        g.fill(slotX + SLOT_SIZE - 1, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, SLOT_BORDER);

        // ---- 物品图标（居中 16×16） ----
        ItemStack stack = entry.stack();
        if (!stack.isEmpty()) {
            var pose = g.pose();
            pose.pushPose();
            pose.translate(slotX + ICON_OFFSET, slotY + ICON_OFFSET, 0);
            g.renderItem(stack, 0, 0);
            pose.popPose();
        }

        // ---- 数量文本（参考 AE2 StackSizeRenderer 缩放逻辑） ----
        long count = entry.count;
        if (count > 1) {
            String text = formatAmount(count);
            int textW = mc.font.width(text);

            // 缩放空间坐标计算（与 AE2 StackSizeRenderer 一致）
            float scale = AMOUNT_SCALE;
            float invScale = INV_AMOUNT_SCALE;
            // 文本在缩放后的位置：格子右下角向内偏移
            int tx = (int) ((slotX + SLOT_SIZE + AMOUNT_OFFSET_X - textW * scale) * invScale);
            int ty = (int) ((slotY + SLOT_SIZE + AMOUNT_OFFSET_Y - 5.0f * scale) * invScale);

            var pose = g.pose();
            pose.pushPose();
            pose.scale(scale, scale, 1.0f);

            int color = entry.isFluid() ? FLUID_AMOUNT_COLOR : AMOUNT_COLOR;
            // 文字阴影（与 AE2 风格一致）
            g.drawString(mc.font, text, tx + 1, ty + 1, 0xFF_000000, false);
            g.drawString(mc.font, text, tx, ty, color, false);

            pose.popPose();
        }
    }

    // ======================== 数量格式化 ========================

    /**
     * 将数量格式化为紧凑形式（参考 AE2 AmountFormat.SLOT 风格）。
     *
     * <ul>
     *   <li>&ge; 1,000,000,000 → "1.0B"（十亿）</li>
     *   <li>&ge; 1,000,000 → "1.0M"（百万）</li>
     *   <li>&ge; 1,000 → "1.0K"（千）</li>
     *   <li>其他 → 原样输出</li>
     * </ul>
     */
    private static String formatAmount(long count) {
        if (count >= 1_000_000_000L) {
            double val = count / 100_000_000.0;
            return String.format("%.1fB", val / 10.0);
        } else if (count >= 1_000_000L) {
            double val = count / 100_000.0;
            return String.format("%.1fM", val / 10.0);
        } else if (count >= 1_000L) {
            double val = count / 100.0;
            return String.format("%.1fK", val / 10.0);
        }
        return String.valueOf(count);
    }

    // ======================== 空状态提示 ========================

    /** 无数据时显示提示文本。 */
    private void renderEmptyHint(GuiGraphics g) {
        String hint = "No storage";
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight;
        TextRenderer.drawCentered(g, mc.font, hint,
                getX() + getWidth() / 2, getY() + (getHeight() - lineH) / 2, HINT_COLOR);
    }

    // ======================== 滚动条渲染 ========================

    /** 渲染纵向滚动条（首尾各缩 6px 视觉边距）。 */
    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int visibleH = h - PAD_TOP * 2;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, y + PAD_TOP + 6, visibleH - 12);
    }

    // ======================== 悬浮检测 ========================

    /** 查找鼠标悬浮的格子索引，-1 表示无。 */
    private int findHoveredSlot(int mx, int my, int originX, int originY, int scroll) {
        if (!contains(mx, my)) return -1;
        int relX = mx - originX;
        int relY = my - originY + scroll;
        if (relX < 0 || relY < 0) return -1;
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col < 0 || col >= cols || row < 0 || row >= rows) return -1;
        int idx = row * cols + col;
        if (idx >= slotEntries.size()) return -1;
        return idx;
    }

    // ======================== 鼠标事件 ========================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!contains((int) mouseX, (int) mouseY)) return false;
        return scrollBar.handleScroll(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int x = getX(), y = getY(), h = getHeight();
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        return scrollBar.handleClick(mouseX, mouseY, barX,
                y + PAD_TOP + 6, h - PAD_TOP * 2 - 12);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            scrollBar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        if (scrollBar.isDragging()) {
            return scrollBar.handleDrag(mouseY, getY() + PAD_TOP, getHeight() - PAD_TOP * 2);
        }
        return false;
    }
}
