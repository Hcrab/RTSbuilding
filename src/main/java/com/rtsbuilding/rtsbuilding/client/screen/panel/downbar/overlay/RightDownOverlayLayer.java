package com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.overlay;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.component.ScrollBar;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.overlay.DownOverlayLayer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
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
    private static final int SLOT_GAP = 0;
    /** 内边距（距 overlay 左/上边缘） */
    private static final int PAD_LEFT = 4;
    private static final int PAD_TOP = 2;
    /** 网格起始绘制高度额外下移量，增加与嵌层顶部的视觉呼吸空间 */
    private static final int GRID_TOP_OFFSET = 20;
    /** 右侧为滚动条预留的宽度 */
    private static final int SCROLLBAR_W = 7;
    /** 滚动条右侧与嵌层右边缘的间距 */
    private static final int RIGHT_MARGIN = 4;

    /** 物品图标在格子内的偏移（居中，16×16 图标在 18×18 格子中上下各 1px） */
    private static final int ICON_OFFSET = 1;

    /** 数量文本缩放系数（参考 AE2 StackSizeRenderer 默认值 0.666f） */
    private static final float AMOUNT_SCALE = 0.666f;
    /** 缩放倒数 */
    private static final float INV_AMOUNT_SCALE = 1.0f / AMOUNT_SCALE;

    // ======================== 格子贴图（slots.png）=======================

    /** slots.png：32×48，水平双主题，垂直 0-16=正常，16-32=悬浮，32-48=选中 */
    private static final ResourceLocation SLOTS_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots.png");
    private static final int SLOTS_TEX_W = 32;
    private static final int SLOTS_TEX_H = 48;
    private static final int SLOTS_STATE_H = 16;
    /** 选中态垂直偏移（y=32-48） */
    private static final int SLOTS_SELECTED_V_OFFSET = 32;
    private static final TextureInfo SLOTS_TEX_INFO = new TextureInfo(
            SLOTS_TEXTURE, SLOTS_TEX_W, SLOTS_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    /** 正常态精灵（v=0~16，半区宽=16） */
    private static final SpriteRegion SLOT_NORMAL = new SpriteRegion(
            SLOTS_TEX_INFO, 0, 0, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 悬浮态精灵（v=16~32） */
    private static final SpriteRegion SLOT_HOVER = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_STATE_H, SLOTS_TEX_W / 2, SLOTS_STATE_H);
    /** 选中态精灵（v=32~48）——半透明覆盖层，盖在图标之上指示已选中 */
    private static final SpriteRegion SLOT_SELECTED = new SpriteRegion(
            SLOTS_TEX_INFO, 0, SLOTS_SELECTED_V_OFFSET, SLOTS_TEX_W / 2, SLOTS_STATE_H);

    // ======================== 网格外围装饰贴图（slots_overlay.png）=======================

    /** slots_overlay.png：32×16，水平双主题，每个半区 16×16，九宫格边框 2px */
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/down/slots_overlay.png");
    private static final int OVERLAY_TEX_W = 32;
    private static final int OVERLAY_TEX_H = 16;
    private static final int OVERLAY_STATE_H = 16;
    private static final int OVERLAY_BORDER = 2;
    private static final TextureInfo OVERLAY_TEX_INFO = new TextureInfo(
            OVERLAY_TEXTURE, OVERLAY_TEX_W, OVERLAY_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion OVERLAY_NINE_SLICE = NineSliceRegion.fullTheme(
            OVERLAY_TEX_INFO, OVERLAY_STATE_H, OVERLAY_BORDER);

    // ======================== 颜色 ========================

    /** 格子背景色（深色半透明，当前未使用） */
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

    /** 当前选中的格子索引（-1=无选中），鼠标点击切换 */
    private int selectedSlotIndex = -1;

    /** 当前帧合并后的显示条目列表（物品+流体） */
    private final List<SlotEntry> slotEntries = new ArrayList<>();
    /** 当前帧的列数 */
    private int cols;
    /** 当前帧的行数 */
    private int rows;
    /** 当前帧每行可用的实际宽度（用于计算列数） */
    private int usableW;
    /** 当前帧悬浮的格子索引，用于外部渲染 tooltip */
    private int tooltipSlotIndex = -1;

    /**
     * 获取当前悬浮格子的物品堆，供 BuilderScreen 在缩放通道外渲染 tooltip。
     *
     * @return 悬浮物品的 ItemStack，无悬浮时返回空栈
     */
    public ItemStack getHoveredSlotStack() {
        if (tooltipSlotIndex < 0 || tooltipSlotIndex >= slotEntries.size()) return ItemStack.EMPTY;
        return slotEntries.get(tooltipSlotIndex).stack();
    }

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
        // 固定网格行数：基于面板内网格实际可视高度决定，+2 行作为滚动缓冲
        rows = Math.max(1, (h - PAD_TOP - GRID_TOP_OFFSET) / (SLOT_SIZE + SLOT_GAP) + 2);
        // 实际物品行数（用于滚动内容范围）
        int itemRows = (slotEntries.size() + cols - 1) / cols;
        int visibleH = h - PAD_TOP * 2;
        int gridVisibleH = visibleH - GRID_TOP_OFFSET;
        int gridH = itemRows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;

        // ---- 更新滚动条（仅在网格高度严格超出可视高度时显示）----
        scrollBar.setContent(gridH, gridVisibleH + 6);
        int scroll = scrollBar.getScroll();

        int originX = x + PAD_LEFT;
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridW = cols * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int frameH = rows * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int bottomY = originY + gridVisibleH;
        int mouseX = getLastMouseX();
        int mouseY = getLastMouseY();
        int hoveredSlot = findHoveredSlot(mouseX, mouseY, originX, originY, scroll);
        this.tooltipSlotIndex = hoveredSlot;

        // ---- 启用 GPU Scissor 精确裁剪网格区域，替代 CPU 跳过逻辑 ----
        g.flush();
        Screen screen = mc.screen;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, originX, originY, originX + gridW, bottomY);
        } else {
            g.enableScissor(originX, originY, originX + gridW, bottomY);
        }

        // ---- 第一遍：批量绘制整个网格区域（rows × cols）的所有格子背景，与 AE2 行为一致 ----
        // 先绘制网格外围装饰框（固定位置，不随滚动偏移）
        SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme(), originX, originY, gridW, frameH);
        // 再逐个绘制格子背景
        int totalCells = rows * cols;
        for (int i = 0; i < totalCells; i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            if (slotY > bottomY) continue;
            SpriteRenderer.drawSprite(g, SLOT_NORMAL.withTheme(), slotX, slotY, SLOT_SIZE, SLOT_SIZE);
        }

        // ---- 第二遍：逐格渲染物品图标 + 数量文字 + 悬浮层 ----
        for (int i = 0; i < slotEntries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int slotX = originX + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = originY + row * (SLOT_SIZE + SLOT_GAP) - scroll;
            if (slotY > bottomY) continue;

            SlotEntry entry = slotEntries.get(i);
            boolean hovered = (i == hoveredSlot);

            // ---- 物品图标（居中 16×16）----
            RenderSystem.disableDepthTest();
            int iconX = slotX + ICON_OFFSET;
            int iconY = slotY + ICON_OFFSET;
            ItemStack stack = entry.stack();
            if (!stack.isEmpty()) {
                var pose = g.pose();
                pose.pushPose();
                pose.translate(iconX, iconY, 0);  // 物品图标 Z=0
                g.renderItem(stack, 0, 0);
                g.flush(); // 立即提交物品渲染，确保层级正确
                pose.popPose();
            }

            // ---- 数量文本（以格子右下角为参考点，后渲染于图标之上）----
            RenderSystem.disableDepthTest();
            long count = entry.count;
            if (count > 1) {
                String text = formatAmount(count);
                int textW = mc.font.width(text);

                float scale = AMOUNT_SCALE;
                float invScale = INV_AMOUNT_SCALE;
                // 以物品背景框右/下边缘为基准，缩放坐标系中右对齐文本
                float refRight = slotX + SLOT_SIZE;
                float refBottom = slotY + SLOT_SIZE;
                int tx = (int) (refRight * invScale - textW);
                int ty = (int) (refBottom * invScale - mc.font.lineHeight);

                // 切换到下一个渲染层级，确保数量文字在物品图标之上
                g.pose().pushPose();
                g.pose().scale(scale, scale, 1.0f);
                g.pose().translate(tx, ty, 200); // 数量文字 Z=200，高于物品图标

                int color = entry.isFluid() ? FLUID_AMOUNT_COLOR : AMOUNT_COLOR;
                // 阴影文字（确保在任何图标颜色上都清晰）
                g.drawString(mc.font, text, 1, 1, 0xFF_000000, false);
                g.drawString(mc.font, text, 0, 0, color, false);
                g.flush(); // 立即提交文字渲染，确保层级正确

                g.pose().popPose();
            }

            // ---- 选中覆盖层 / 悬浮叠加层（盖在图标和数量文字之上）----
            RenderSystem.disableDepthTest();
            var pose = g.pose();
            pose.pushPose();
            pose.translate(slotX, slotY, 300); // 覆盖层 Z=300，最高层级
            boolean isSelected = (i == selectedSlotIndex);
            if (isSelected) {
                SpriteRenderer.drawSprite(g, SLOT_SELECTED.withTheme(), 0, 0, SLOT_SIZE, SLOT_SIZE);
                g.flush(); // 立即提交选中状态渲染
            } else if (hovered) {
                SpriteRenderer.drawSprite(g, SLOT_HOVER.withTheme(), 0, 0, SLOT_SIZE, SLOT_SIZE);
                g.flush(); // 立即提交悬浮状态渲染
            }
            pose.popPose();
        }

        // ---- 恢复为嵌层 Scissor 边界（弹出网格 Scissor，让 DownOverlayLayer 的嵌层 Scissor 自然恢复）----
        g.flush();
        g.disableScissor();

        // ---- 滚动条 ----
        renderScrollbar(g, x, y, h);
    }

    // ======================== 合成条目 ========================

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

    // ======================== 单格子渲染（已内联到 renderContent 的两遍式循环中）========================

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

    /** 渲染纵向滚动条（首尾各缩 6px 视觉边距），坐标与网格起始位置对齐。 */
    private void renderScrollbar(GuiGraphics g, int x, int y, int h) {
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        scrollBar.render(g, barX, originY + 6, gridVisibleH - 12);
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
        int originY = y + PAD_TOP + GRID_TOP_OFFSET;
        int gridVisibleH = h - PAD_TOP * 2 - GRID_TOP_OFFSET;
        int barX = x + getWidth() - SCROLLBAR_W - RIGHT_MARGIN;
        if (scrollBar.handleClick(mouseX, mouseY, barX,
                originY + 6, gridVisibleH - 12)) {
            return true;
        }

        // ---- 格子点击选中/取消 ----
        if (!contains((int) mouseX, (int) mouseY)) return false;
        int w = getWidth();
        int originX = x + PAD_LEFT;
        int usableW = w - PAD_LEFT - SCROLLBAR_W - RIGHT_MARGIN;
        int cols = Math.max(1, (usableW + SLOT_GAP) / (SLOT_SIZE + SLOT_GAP));
        int relX = (int) mouseX - originX;
        int relY = (int) mouseY - originY + scrollBar.getScroll();
        if (relX < 0 || relY < 0) {
            selectedSlotIndex = -1;
            return false;
        }
        int col = relX / (SLOT_SIZE + SLOT_GAP);
        int row = relY / (SLOT_SIZE + SLOT_GAP);
        if (col < 0 || col >= cols || row < 0) {
            selectedSlotIndex = -1;
            return false;
        }
        int idx = row * cols + col;
        if (idx >= slotEntries.size()) {
            selectedSlotIndex = -1;
            return false;
        }
        // 点击同一格子取消选中，否则切换选中
        selectedSlotIndex = (selectedSlotIndex == idx) ? -1 : idx;
        return true;
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
            int originY = getY() + PAD_TOP + GRID_TOP_OFFSET;
            int gridVisibleH = getHeight() - PAD_TOP * 2 - GRID_TOP_OFFSET;
            return scrollBar.handleDrag(mouseY, originY, gridVisibleH);
        }
        return false;
    }
}
