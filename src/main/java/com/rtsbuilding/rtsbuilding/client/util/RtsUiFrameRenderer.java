package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 生产 UI 的统一矩形边框适配器。
 *
 * <p>屏幕只依赖这个窄入口，不再直接调用历史工具类；几何绘制仍保持
 * 26.1 extractor 的坐标与像素顺序，便于后续替换为 UI Kit chrome primitive。</p>
 */
public final class RtsUiFrameRenderer {
    private RtsUiFrameRenderer() {
    }

    public static void frame(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                             int fill, int light, int dark) {
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, light);
        graphics.fill(x, y + height - 1, x + width, y + height, dark);
        graphics.fill(x, y, x + 1, y + height, light);
        graphics.fill(x + width - 1, y, x + width, y + height, dark);
    }
}
