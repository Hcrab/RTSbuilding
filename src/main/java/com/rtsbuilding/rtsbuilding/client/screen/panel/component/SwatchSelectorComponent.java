package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.screen.panel.model.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 色块选择器组件——在调色盘面板中显示多条目颜色组的色块列表。
 *
 * <p>当颜色组内有多个条目时显示此组件，每个条目显示为一个色块 + 文字标签。
 * 支持点击切换当前编辑的条目。</p>
 *
 * <p>纯渲染组件，所有数据由调用方传入。</p>
 */
public final class SwatchSelectorComponent {

    /** 色块选择器行高 */
    public static final int ROW_H = 20;
    /** 每个色块的尺寸 */
    private static final int SWATCH_SIZE = 14;
    /** 色块之间的间距 */
    private static final int SWATCH_GAP = 12;
    /** 色块与文字标签的间距 */
    private static final int SWATCH_TEXT_GAP = 3;
    /** 左内边距 */
    private static final int LEFT_PADDING = 6;

    /**
     * 渲染色块选择器。
     *
     * @param g                GuiGraphics
     * @param mouseX           鼠标 X（用于 tooltip 等未使用，保留接口一致性）
     * @param mouseY           鼠标 Y
     * @param group            颜色组
     * @param activeSlotIndex  当前选中的色块索引
     * @param sectionTop       选择器区域顶部 Y
     */
    public void render(GuiGraphics g, int mouseX, int mouseY,
                       ColorGroup group, int activeSlotIndex, int sectionTop) {
        if (group == null || group.size() <= 1) return;

        Font font = Minecraft.getInstance().font;
        int textColor = ThemeManager.getTextColor();
        int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
        int itemX = LEFT_PADDING;

        for (int i = 0; i < group.size(); i++) {
            String name = group.slot(i).displayName();
            int slotColor = group.slot(i).source().getColor();

            // 色块背景
            g.fill(itemX, swatchY, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, slotColor);
            // 选中态：白色边框；非选中态：深灰边框
            int swatchBorder = (i == activeSlotIndex) ? 0xFFFFFFFF : 0xFF444444;
            g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY - 1, swatchBorder);
            g.hLine(itemX - 1, itemX + SWATCH_SIZE, swatchY + SWATCH_SIZE, swatchBorder);
            g.vLine(itemX - 1, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);
            g.vLine(itemX + SWATCH_SIZE, swatchY - 1, swatchY + SWATCH_SIZE, swatchBorder);

            // 色块右侧绘制文字标签
            TextRenderer.draw(g, name, itemX + SWATCH_SIZE + SWATCH_TEXT_GAP,
                    sectionTop + (ROW_H - font.lineHeight) / 2 + 1, textColor);

            // 移动到下一个条目
            itemX += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name) + SWATCH_GAP;
        }
    }

    /**
     * 检测色块选择器的点击。
     *
     * @param mouseX         鼠标 X
     * @param mouseY         鼠标 Y
     * @param group          颜色组
     * @param sectionTop     选择器区域顶部 Y
     * @return 被点击的色块索引，或 -1 表示未命中
     */
    public int hitTest(double mouseX, double mouseY, ColorGroup group, int sectionTop) {
        if (group == null || group.size() <= 1) return -1;

        Font font = Minecraft.getInstance().font;
        int swatchY = sectionTop + (ROW_H - SWATCH_SIZE) / 2;
        int itemX = LEFT_PADDING;

        for (int i = 0; i < group.size(); i++) {
            String name = group.slot(i).displayName();
            int itemW = SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(name);
            if (mouseX >= itemX && mouseX < itemX + itemW
                    && mouseY >= swatchY && mouseY < swatchY + SWATCH_SIZE) {
                return i;
            }
            itemX += itemW + SWATCH_GAP;
        }
        return -1;
    }

    /**
     * 计算色块选择器（含文字标签）所需的最小宽度。
     *
     * @param group 颜色组
     * @return 所需最小宽度（像素）
     */
    public int computeMinWidth(ColorGroup group) {
        if (group == null || group.size() <= 1) return 0;

        Font font = Minecraft.getInstance().font;
        int total = LEFT_PADDING + 4; // 左右内边距
        for (int i = 0; i < group.size(); i++) {
            if (i > 0) total += SWATCH_GAP;
            total += SWATCH_SIZE + SWATCH_TEXT_GAP + font.width(group.slot(i).displayName());
        }
        return total;
    }
}
