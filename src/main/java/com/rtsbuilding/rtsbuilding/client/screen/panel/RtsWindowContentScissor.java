package com.rtsbuilding.rtsbuilding.client.screen.panel;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 浮窗内容裁剪的客户端适配器。
 *
 * <p>它只负责把逻辑内容矩形与当前可见动画偏移交给正确的 scissor 入口；不拥有窗口尺寸、
 * 动画状态或关闭生命周期。将这段适配移出基础窗口，可避免渲染 API 细节继续挤占窗口状态机。</p>
 */
final class RtsWindowContentScissor {
    static void enable(GuiGraphics graphics, BuilderScreen screen,
                       int x, int y, int width, int height,
                       double visualOffsetY) {
        int shiftedY = y + (int) Math.round(visualOffsetY);
        if (screen != null) {
            screen.enableRtsScissor(
                    graphics, x, shiftedY, x + width, shiftedY + height);
        } else {
            graphics.enableScissor(
                    x, shiftedY, x + width, shiftedY + height);
        }
    }

    private RtsWindowContentScissor() {
    }
}
