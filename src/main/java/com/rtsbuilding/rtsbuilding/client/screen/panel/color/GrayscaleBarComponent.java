package com.rtsbuilding.rtsbuilding.client.screen.panel.color;

import com.rtsbuilding.rtsbuilding.client.util.animate.FloatAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 灰度条组件——管理灰度条渐变渲染、指示器绘制与取色交互。
 *
 * <p>无状态组件，布局由调用者（面板）统一编排。</p>
 */
public class GrayscaleBarComponent {

    // ======================== 灰度条布局 ========================

    /** 灰度条绘制宽度 */
    public static final int BAR_W = 8;
    /** 灰度条绘制高度（与颜色轮盘等高） */
    public static final int BAR_H = 95;
    /** 灰度条与轮盘的间距 */
    public static final int GAP = 4;

    // ======================== 灰度条指示器贴图 ========================

    /** base_ui_5.png：32×48，水平双主题，垂直三态各 16px（y 0-16/16-32/32-48） */
    private static final ResourceLocation GRAYSCALE_INDICATOR_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_5.png");
    private static final int GRAYSCALE_INDICATOR_TEX_W = 32;
    private static final int GRAYSCALE_INDICATOR_TEX_H = 48;
    /** 每个状态的贴图高度（16px，48/3=16） */
    private static final int GRAYSCALE_INDICATOR_STATE_H = 16;

    private static final TextureInfo GRAYSCALE_INDICATOR_TEX_INFO = new TextureInfo(
            GRAYSCALE_INDICATOR_TEXTURE, GRAYSCALE_INDICATOR_TEX_W, GRAYSCALE_INDICATOR_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);

    /** 灰度条指示器在屏幕上的绘制尺寸 */
    private static final int INDICATOR_DRAW_W = 12;
    private static final int INDICATOR_DRAW_H = 12;

    // ======================== 渲染 ========================

    /**
     * 绘制灰度条渐变（从基色渐变到黑色）。
     *
     * @param g          绘制上下文
     * @param barX       灰度条左上角屏幕 X
     * @param barY       灰度条左上角屏幕 Y
     * @param baseColor  基色 ARGB（灰度条从此色渐变到黑色）
     */
    public void renderBar(GuiGraphics g, int barX, int barY, int baseColor) {
        int br = (baseColor >> 16) & 0xFF;
        int bg = (baseColor >> 8) & 0xFF;
        int bb = baseColor & 0xFF;

        for (int row = 0; row < BAR_H; row++) {
            float t = row / (float) (BAR_H - 1);
            int r = (int) (br * (1 - t));
            int gn = (int) (bg * (1 - t));
            int bn = (int) (bb * (1 - t));
            g.fill(barX, barY + row, barX + BAR_W, barY + row + 1,
                    0xFF000000 | (r << 16) | (gn << 8) | bn);
        }
    }

    /**
     * 在灰度条上绘制指示器，支持三态平滑动画。
     *
     * @param g          绘制上下文
     * @param barX       灰度条左上角屏幕 X
     * @param barY       灰度条左上角屏幕 Y
     * @param relY       指示器归一化 Y [0,1]（0=顶部基色，1=底部黑色）
     * @param animator   状态过渡动画器
     * @param mouseX     鼠标屏幕 X
     * @param mouseY     鼠标屏幕 Y
     * @param dragging   是否正在拖拽
     */
    public void renderIndicator(GuiGraphics g, int barX, int barY,
                                 float relY, FloatAnimation animator,
                                 int mouseX, int mouseY, boolean dragging) {
        int targetState;
        if (dragging) {
            targetState = 2;
        } else if (mouseX >= barX && mouseX < barX + BAR_W
                && mouseY >= barY && mouseY < barY + BAR_H) {
            targetState = 1;
        } else {
            targetState = 0;
        }

        animator.start(targetState);
        animator.tick();

        float stateF = animator.getValue();
        int stateVOffset = Math.round(stateF * GRAYSCALE_INDICATOR_STATE_H);
        stateVOffset = Math.max(0, Math.min(
                GRAYSCALE_INDICATOR_TEX_H - GRAYSCALE_INDICATOR_STATE_H, stateVOffset));

        int drawX = barX - (INDICATOR_DRAW_W - BAR_W) / 2;
        int indicatorCenterY = barY + Math.round(relY * (BAR_H - 1));
        int drawY = indicatorCenterY - INDICATOR_DRAW_H / 2;

        int minY = barY - INDICATOR_DRAW_H / 2;
        int maxY = barY + BAR_H - INDICATOR_DRAW_H / 2;
        drawY = Math.max(minY, Math.min(maxY, drawY));

        SpriteRegion region = new SpriteRegion(
                GRAYSCALE_INDICATOR_TEX_INFO, 0, stateVOffset,
                GRAYSCALE_INDICATOR_TEX_INFO.halfWidth(), GRAYSCALE_INDICATOR_STATE_H);
        SpriteRenderer.drawSprite(g, region.withTheme(), drawX, drawY,
                INDICATOR_DRAW_W, INDICATOR_DRAW_H);
    }

    // ======================== 取色交互 ========================

    /**
     * 根据鼠标 Y 位置计算灰度条上的归一化位置。
     *
     * @param mouseY  鼠标屏幕 Y
     * @param barY    灰度条左上角屏幕 Y
     * @return 归一化 Y [0,1]（0=顶部基色，1=底部黑色）
     */
    public float pickColor(double mouseY, int barY) {
        double relY = (mouseY - barY) / (double) BAR_H;
        relY = Math.max(0.0, Math.min(1.0, relY));
        return (float) relY;
    }
}

