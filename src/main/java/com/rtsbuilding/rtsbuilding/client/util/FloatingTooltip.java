package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 浮窗工具类——管理延迟显示与淡入淡出动画。
 *
 * <p>封装了浮窗的完整生命周期：</p>
 * <ul>
 *   <li><b>延迟显示</b>：悬浮超过 {@code delayMs} 后浮窗才浮现，避免短时误触</li>
 *   <li><b>淡入淡出</b>：显示/隐藏时以 smoothstep 动画过渡透明度</li>
 *   <li><b>抑制条件</b>：支持传入 {@code suppressed} 参数（如弹窗打开时）抑制浮窗显示</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * FloatingTooltip tooltip = new FloatingTooltip(1000);
 *
 * // 每帧：
 * tooltip.tick();
 * tooltip.update(hovered, suppressed);
 * if (tooltip.shouldRender()) {
 *     tooltip.renderBelowButton(g, btnX, btnY, btnW, btnH,
 *             padH, padV, text, color);
 * }
 * }</pre>
 */
public class FloatingTooltip {
    private static final float ALPHA_THRESHOLD = 0.001f;
    private static final float TEXT_SCALE = 0.75f;

    private final long delayMs;
    private final SmoothAnimator anim = AnimationFactory.createHoverAnim();
    private long hoverStartTime = -1L;
    private boolean tooltipShown;
    private boolean prevTooltipShown;

    /** 默认延迟 1000ms */
    public FloatingTooltip() {
        this(1000L);
    }

    /**
     * @param delayMs 悬浮多久后显示浮窗（毫秒）
     */
    public FloatingTooltip(long delayMs) {
        this.delayMs = delayMs;
    }

    /** 每帧调用，推进淡入淡出动画 */
    public void tick() {
        anim.tick();
    }

    /**
     * 每帧更新悬浮状态与浮窗计时。
     *
     * @param hovered    当前是否悬浮在触发区域上
     * @param suppressed 是否抑制显示（如弹窗打开时传 true）
     */
    public void update(boolean hovered, boolean suppressed) {
        boolean active = hovered && !suppressed;

        if (active) {
            if (hoverStartTime == -1L) {
                hoverStartTime = Util.getMillis();
            }
            tooltipShown = Util.getMillis() - hoverStartTime >= delayMs;
        } else {
            hoverStartTime = -1L;
            tooltipShown = false;
        }

        if (tooltipShown != prevTooltipShown) {
            prevTooltipShown = tooltipShown;
            anim.start(tooltipShown ? 1.0f : 0.0f);
        }
    }

    /** 获取当前淡入淡出透明度 [0, 1] */
    public float getAlpha() {
        return anim.getValue();
    }

    /** 浮窗是否应渲染（透明度超过阈值） */
    public boolean shouldRender() {
        return getAlpha() > ALPHA_THRESHOLD;
    }

    /**
     * 在按钮下方渲染浮窗背景与文字（支持多行，以 \n 分隔），自动钳位到屏幕边界内。
     *
     * <p>若浮窗超出屏幕左/右/下边缘，会自动平移到屏幕边界内，
     * 确保浮窗始终完整可见。</p>
     *
     * @param g      GuiGraphics
     * @param btnX   按钮左上 X
     * @param btnY   按钮左上 Y
     * @param btnW   按钮宽度
     * @param btnH   按钮高度
     * @param padH   水平内边距
     * @param padV   垂直内边距
     * @param text   浮窗文字（多行用 \n 分隔）
     * @param color         文字 ARGB 颜色
     * @param shortcutColor 快捷键行的文字颜色（通常比 {@code color} 暗一些）
     * @param screenW       浮窗参考的屏幕宽度（虚拟坐标空间，如 BuilderScreen.width）
     * @param screenH       浮窗参考的屏幕高度（虚拟坐标空间，如 BuilderScreen.height）
     */
    public void renderBelowButton(GuiGraphics g, int btnX, int btnY, int btnW, int btnH,
                                  int padH, int padV, String text, int color, int shortcutColor,
                                  int screenW, int screenH) {
        float alpha = getAlpha();
        var font = Minecraft.getInstance().font;

        // 按行分割，计算最大宽度和总高度（文字按 TEXT_SCALE 缩放）
        String[] lines = text.split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * TEXT_SCALE;
        float scaledLineGap = lineGap * TEXT_SCALE;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int tipW = (int)(maxLineW * TEXT_SCALE) + padH * 2;
        int tipH = (int)(scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        // 先定位到按钮下方居中
        int tipX = btnX + (btnW - tipW) / 2;
        int tipY = btnY + btnH + 2;

        // 钳位到屏幕边界内，防止浮窗超出画面
        tipX = Math.max(0, Math.min(tipX, screenW - tipW));
        tipY = Math.max(0, Math.min(tipY, screenH - tipH));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        RtsClientUiUtil.drawNineSliceFloatingPanel(g, tipX, tipY, tipW, tipH);

        // 逐行绘制文字（末行为快捷键，使用较暗的颜色），按 TEXT_SCALE 缩放
        float textY = tipY + padV;
        for (int i = 0; i < lines.length; i++) {
            int lineColor = (i == lines.length - 1) ? shortcutColor : color;
            g.pose().pushPose();
            g.pose().translate(tipX + padH, textY, 0);
            g.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0f);
            RtsClientUiUtil.drawUiText(g, lines[i], 0, 0, lineColor);
            g.pose().popPose();
            textY += scaledLineH + scaledLineGap;
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
