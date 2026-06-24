package com.rtsbuilding.rtsbuilding.client.screen.panel.base;

import com.rtsbuilding.rtsbuilding.client.screen.panel.util.RtsButton;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 窗口框架渲染器——负责渲染 RTS 面板的窗口框架（九宫格背景、标题栏、关闭按钮）。
 *
 * <p>将窗口外观渲染从 {@link RtsPanel} 中抽离，职责单一：
 * <ul>
 *   <li>面板九宫格背景（含悬浮态交叉淡入淡出）</li>
 *   <li>标题栏贴图 + 标题文字</li>
 *   <li>关闭按钮渲染</li>
 * </ul>
 *
 * <p>渲染所需的所有数据通过 {@link Context} 记录传入，不持有任何可变状态。
 * 通过 {@link #renderFrame} 静态方法调用。</p>
 */
public final class WindowFrameRenderer {

    /** 关闭按钮尺寸（宽高相同） */
    private static final int CLOSE_BUTTON_SIZE = 14;
    /** 关闭按钮精灵图单帧宽度 */
    private static final int CLOSE_FRAME_W = 512;
    /** 关闭按钮精灵图文件总宽度（双主题翻倍） */
    private static final int CLOSE_SHEET_W = 1024;
    /** 关闭按钮精灵图文件总高度 */
    private static final int CLOSE_SHEET_H = 1024;
    /** 关闭按钮各状态帧高度 */
    private static final int CLOSE_STATE_H = 512;
    /** 关闭按钮贴图 */
    private static final ResourceLocation CLOSE_BUTTON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/close_button.png");

    private WindowFrameRenderer() {}

    /**
     * 创建关闭按钮实例。
     * <p>将关闭按钮的贴图参数封装在此，{@link RtsPanel} 只需提供关闭回调。</p>
     *
     * @param onClose 关闭回调（通常是 {@code () -> setOpen(false)}）
     * @return 配置好的关闭按钮
     */
    public static RtsButton createCloseButton(Runnable onClose) {
        return new RtsButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.empty(), CLOSE_BUTTON_TEXTURE,
                0, 0,
                CLOSE_FRAME_W, CLOSE_STATE_H,
                CLOSE_STATE_H, CLOSE_STATE_H,
                CLOSE_SHEET_W, CLOSE_SHEET_H,
                btn -> onClose.run());
    }

    /**
     * 帧渲染上下文——一次渲染所需的所有数据。
     * <p>由 {@link RtsPanel} 在每帧 {@code render()} 中构建。</p>
     */
    public record Context(
            int windowX,
            int windowY,
            int windowWidth,
            int windowHeight,
            int titleBarHeight,
            int panelBgColor,
            int panelHoverBgColor,
            int titleBarBgColor,
            int titleTextColor,
            Component title,
            boolean closable,
            RtsButton closeButton,
            float hoverAnimProgress
    ) {}

    // ======================== 入口 ========================

    /**
     * 渲染完整的窗口框架（背景贴图 + 标题栏）。
     *
     * @param g      GuiGraphics
     * @param mouseX 鼠标 X（用于关闭按钮悬浮检测）
     * @param mouseY 鼠标 Y
     * @param ctx    渲染上下文
     */
    public static void renderFrame(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        renderPanelBackground(g, ctx);
        renderTitleBar(g, mouseX, mouseY, ctx);
    }

    // ======================== 面板背景 ========================

    /**
     * 渲染面板九宫格背景，支持悬浮态交叉淡入淡出。
     */
    private static void renderPanelBackground(GuiGraphics g, Context ctx) {
        int normalTint = ctx.panelBgColor();
        int hoverTint = ctx.panelHoverBgColor();
        float a = (float) (normalTint >> 24 & 0xFF) / 255.0F;
        float nr = (float) (normalTint >> 16 & 0xFF) / 255.0F;
        float ng = (float) (normalTint >> 8 & 0xFF) / 255.0F;
        float nb = (float) (normalTint & 0xFF) / 255.0F;
        float hr = (float) (hoverTint >> 16 & 0xFF) / 255.0F;
        float hg = (float) (hoverTint >> 8 & 0xFF) / 255.0F;
        float hb = (float) (hoverTint & 0xFF) / 255.0F;

        RtsClientUiUtil.renderCrossFade(ctx.hoverAnimProgress(),
                () -> renderPanelLayer(g, ctx.windowX(), ctx.windowY(), ctx.windowWidth(), ctx.windowHeight(),
                        nr, ng, nb, a, false),
                () -> renderPanelLayer(g, ctx.windowX(), ctx.windowY(), ctx.windowWidth(), ctx.windowHeight(),
                        hr, hg, hb, a, true));
    }

    /** 渲染单层面板背景贴图（带色调） */
    private static void renderPanelLayer(GuiGraphics g, int wx, int wy, int ww, int wh,
                                          float r, float green, float b, float alpha, boolean hovered) {
        g.setColor(r, green, b, alpha);
        RtsClientUiUtil.drawNineSlicePanel(g, wx, wy, ww, wh, hovered);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ======================== 标题栏 ========================

    /**
     * 渲染标题栏——贴图背景 + 标题文本 + 关闭按钮（可选）。
     */
    private static void renderTitleBar(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        int titleH = ctx.titleBarHeight();
        if (titleH <= 0) return;

        // 标题栏背景贴图（带色调）
        renderTitleBarBackground(g, ctx, titleH);

        // 标题文本（自动截断以适应窗口宽度）
        renderTitleText(g, ctx, titleH);

        // 关闭按钮
        if (ctx.closable() && ctx.closeButton() != null) {
            renderCloseButton(g, mouseX, mouseY, ctx);
        }
    }

    private static void renderTitleBarBackground(GuiGraphics g, Context ctx, int titleH) {
        int tint = ctx.titleBarBgColor();
        float a = (float) (tint >> 24 & 0xFF) / 255.0F;
        float r = (float) (tint >> 16 & 0xFF) / 255.0F;
        float gr = (float) (tint >> 8 & 0xFF) / 255.0F;
        float b = (float) (tint & 0xFF) / 255.0F;
        g.setColor(r, gr, b, a);
        RtsClientUiUtil.drawNineSliceDragPanel(g, ctx.windowX() + 3, ctx.windowY() + 3,
                ctx.windowWidth() - 6, titleH, false);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderTitleText(GuiGraphics g, Context ctx, int titleH) {
        String title = RtsClientUiUtil.trimToWidth(Minecraft.getInstance().font, ctx.title().getString(),
                Math.max(8, ctx.windowWidth() - 36));
        int textY = ctx.windowY() + Math.max(1, (titleH - Minecraft.getInstance().font.lineHeight) / 2) + 2;
        RtsClientUiUtil.drawUiText(g, title, ctx.windowX() + 8, textY, ctx.titleTextColor());
    }

    private static void renderCloseButton(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        int btnX = ctx.windowX() + ctx.windowWidth() - CLOSE_BUTTON_SIZE - 5;
        int btnY = ctx.windowY() + 4;
        RtsButton btn = ctx.closeButton();
        btn.setX(btnX);
        btn.setY(btnY);
        btn.render(g, mouseX, mouseY, 0.0F);
    }
}
