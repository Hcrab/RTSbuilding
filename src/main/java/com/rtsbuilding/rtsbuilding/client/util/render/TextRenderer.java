package com.rtsbuilding.rtsbuilding.client.util.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 文字渲染工具——统一管理 UI 文字绘制。
 *
 * <p>将所有文字渲染方法从 {@code RtsClientUiUtil} 中提取至此，
 * 提供统一的文字绘制入口。</p>
 *
 * <p>所有 UI 组件的文字渲染应通过此类进行，以保证：</p>
 * <ul>
 *   <li>一致的渲染风格（无阴影、标准颜色处理）</li>
 *   <li>统一的文字截断逻辑</li>
 *   <li>集中的缩放/对齐处理</li>
 * </ul>
 */
public final class TextRenderer {

    private TextRenderer() {}

    /**
     * 绘制 UI 文字（无阴影）。
     *
     * @param g     GuiGraphics
     * @param text  文字内容
     * @param x     左上 X 坐标
     * @param y     左上 Y 坐标
     * @param color ARGB 颜色
     */
    public static void draw(GuiGraphics g, String text, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        g.drawString(font, text, x, y, color, false);
    }

    /**
     * 绘制 UI 文字（无阴影），接受 Component 参数。
     *
     * @param g     GuiGraphics
     * @param text  文字内容
     * @param x     左上 X 坐标
     * @param y     左上 Y 坐标
     * @param color ARGB 颜色
     */
    public static void draw(GuiGraphics g, Component text, int x, int y, int color) {
        draw(g, text.getString(), x, y, color);
    }

    /**
     * 居中绘制文字（无阴影），以 centerX 为水平中心点。
     *
     * @param g        GuiGraphics
     * @param font     字体
     * @param text     文字内容
     * @param centerX  水平居中 X 坐标
     * @param y        左上 Y 坐标
     * @param color    ARGB 颜色
     */
    public static void drawCentered(GuiGraphics g, Font font, String text, int centerX, int y, int color) {
        String display = text == null ? "" : text;
        draw(g, display, centerX - font.width(display) / 2, y, color);
    }

    /**
     * 居中绘制文字（无阴影），接受 Component 参数。
     *
     * @param g        GuiGraphics
     * @param font     字体
     * @param text     文字内容
     * @param centerX  水平居中 X 坐标
     * @param y        左上 Y 坐标
     * @param color    ARGB 颜色
     */
    public static void drawCentered(GuiGraphics g, Font font, Component text, int centerX, int y, int color) {
        drawCentered(g, font, text == null ? "" : text.getString(), centerX, y, color);
    }

    /**
     * 将文字截断到指定像素宽度，超出的部分替换为 "..."。
     *
     * @param font     字体
     * @param text     原始文字
     * @param maxWidth 最大像素宽度
     * @return 截断后的文字（不会比原始文字长）
     */
    public static String trimToWidth(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || font == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }

    /**
     * 在物品格右下角绘制数量叠加层（深色背景 + 缩放文字）。
     *
     * @param g         GuiGraphics
     * @param font      字体
     * @param slotX     格子左上 X
     * @param slotY     格子左上 Y
     * @param slotSize  格子尺寸（宽高相同）
     * @param countText 数量文字
     * @param color     文字颜色
     */
    public static void drawSlotCountOverlay(GuiGraphics g, Font font,
                                             int slotX, int slotY, int slotSize,
                                             String countText, int color) {
        if (font == null || countText == null || countText.isEmpty()) return;

        float slotCountScale = 0.65F;

        // 背景在原始坐标空间绘制
        g.fill(slotX + 1, slotY + slotSize - 7, slotX + slotSize - 1, slotY + slotSize - 1, 0xB0000000);
        // 文字在缩放坐标空间绘制：将原点移到目标右下角，缩放后文字右对齐
        g.pose().pushPose();
        g.pose().translate(slotX + slotSize - 2, slotY + slotSize - 7, 300.0F);
        g.pose().scale(slotCountScale, slotCountScale, 1.0F);
        g.drawString(font, countText, -font.width(countText), 0, color, true);
        g.pose().popPose();
    }
}
