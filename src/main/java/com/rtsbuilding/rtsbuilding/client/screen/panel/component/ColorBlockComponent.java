package com.rtsbuilding.rtsbuilding.client.screen.panel.component;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 颜色块组件——渲染一个固定尺寸的纯色方块，用于展示当前颜色值。
 *
 * <p>常用于设置面板中作为颜色预览色块。颜色值由外部传入，支持实时更新。</p>
 */
public class ColorBlockComponent {

    public static final int DEFAULT_SIZE = 8;

    /**
     * 渲染颜色块。
     *
     * @param g     渲染上下文
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param size  色块边长（正方形）
     * @param color 填充颜色（ARGB 格式）
     */
    public void render(GuiGraphics g, int x, int y, int size, int color) {
        g.fill(x, y, x + size, y + size, color);
    }

    /**
     * 使用默认尺寸 {@value #DEFAULT_SIZE} 渲染颜色块。
     */
    public void render(GuiGraphics g, int x, int y, int color) {
        render(g, x, y, DEFAULT_SIZE, color);
    }
}
