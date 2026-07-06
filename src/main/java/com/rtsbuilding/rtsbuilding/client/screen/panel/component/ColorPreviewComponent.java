package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import com.rtsbuilding.rtsbuilding.client.screen.panel.color.ColorMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 颜色预览条组件——在调色盘面板中显示新旧颜色对照。
 *
 * <p>左半=初始颜色（打开面板时的原色），右半=当前编辑颜色。
 * 点击左半可恢复为初始颜色。</p>
 *
 * <p>纯渲染组件，所有数据由调用方传入，不持有任何可变状态。</p>
 */
public final class ColorPreviewComponent {

    /** 预览条高度 */
    public static final int PREVIEW_BAR_H = 16;

    /** 颜色值显示右侧边距 */
    private static final int VALUE_RIGHT_MARGIN = 4;

    /**
     * 渲染新旧颜色对照预览条。
     *
     * @param g            GuiGraphics
     * @param previewX     预览条左边缘 X
     * @param previewY     预览条顶部 Y
     * @param previewW     预览条宽度
     * @param initialColor 初始颜色（ARGB）
     * @param currentColor 当前颜色（ARGB）
     * @param isHexDisplay 是否以 Hex 格式显示
     */
    public void render(GuiGraphics g, int previewX, int previewY, int previewW,
                       int initialColor, int currentColor, boolean isHexDisplay) {
        int midX = previewX + previewW / 2;
        Font font = Minecraft.getInstance().font;

        // 左半：初始颜色
        g.fill(previewX, previewY, midX, previewY + PREVIEW_BAR_H, initialColor);
        // 右半：当前颜色
        g.fill(midX, previewY, previewX + previewW, previewY + PREVIEW_BAR_H, currentColor);

        int borderColor = 0xFF666666;
        // 外边框
        g.hLine(previewX, previewX + previewW, previewY, borderColor);
        g.hLine(previewX, previewX + previewW, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX, previewY, previewY + PREVIEW_BAR_H, borderColor);
        g.vLine(previewX + previewW, previewY, previewY + PREVIEW_BAR_H, borderColor);
        // 中间分隔线
        g.vLine(midX, previewY, previewY + PREVIEW_BAR_H, borderColor);

        // 在新颜色半区居中显示颜色值
        String currentValueStr = formatColorValue(currentColor, isHexDisplay);
        String initialValueStr = formatColorValue(initialColor, isHexDisplay);
        int newColorTextColor = ColorMath.isDarkColor(currentColor) ? 0xFFFFFFFF : 0xFF000000;
        int oldColorTextColor = ColorMath.isDarkColor(initialColor) ? 0xFFFFFFFF : 0xFF000000;
        int colorValueY = previewY + (PREVIEW_BAR_H - font.lineHeight) / 2 + 1;

        int currentValueX = midX + (previewW / 2 - font.width(currentValueStr)) / 2;
        int initialValueX = previewX + (previewW / 2 - font.width(initialValueStr)) / 2;

        g.drawString(font, initialValueStr, initialValueX, colorValueY, oldColorTextColor, false);
        g.drawString(font, currentValueStr, currentValueX, colorValueY, newColorTextColor, false);
    }

    /**
     * 检测左半（初始颜色）区域的点击。
     *
     * @param mouseX    鼠标 X
     * @param mouseY    鼠标 Y
     * @param previewX  预览条左边缘 X
     * @param previewW  预览条宽度
     * @param previewY  预览条顶部 Y
     * @return true 如果点击在初始颜色区域
     */
    public boolean isClickOnInitialColor(double mouseX, double mouseY,
                                          int previewX, int previewW, int previewY) {
        int midX = previewX + previewW / 2;
        return mouseX >= previewX && mouseX < midX
                && mouseY >= previewY && mouseY < previewY + PREVIEW_BAR_H;
    }

    /** 格式化颜色值为 Hex 或 Dec 字符串 */
    private static String formatColorValue(int color, boolean hexDisplay) {
        return hexDisplay
                ? String.format("#%06X", color & 0xFFFFFF)
                : String.valueOf(color & 0xFFFFFF);
    }
}
