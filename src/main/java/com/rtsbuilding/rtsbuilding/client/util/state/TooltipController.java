package com.rtsbuilding.rtsbuilding.client.util.state;

import com.rtsbuilding.rtsbuilding.client.util.render.BlendScope;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 浮窗控制类——管理延迟显示、淡入淡出动画及渲染。
 *
 * <p><b>相比 {@code TooltipController} 的改进：</b></p>
 * <ul>
 *   <li>Builder 模式构建，参数清晰可读</li>
 *   <li>支持多种弹出方向（下方、右侧、左侧、上方）</li>
 *   <li>无 {@code tick()} 空方法污染</li>
 *   <li>与 {@link HoverStateManager} 集成</li>
 * </ul>
 *
 * <p><b>用法：</b></p>
 * <pre>{@code
 * TooltipController tooltip = TooltipController.builder()
 *         .delayMs(800)
 *         .direction(TooltipController.Direction.BELOW)
 *         .build();
 *
 * // 每帧：
 * tooltip.update(mouseOver, suppressed);
 * if (tooltip.shouldRender()) {
 *     var ctx = new TooltipController.RenderContext(
 *             g, btnX, btnY, btnW, btnH,
 *             "功能描述\n快捷键: Ctrl+T", color, shortcutColor,
 *             screenW, screenH);
 *     tooltip.render(ctx);
 * }
 * }</pre>
 */
public class TooltipController {

    /** 浮窗弹出方向 */
    public enum Direction {
        /** 在触发区域下方弹出 */
        BELOW,
        /** 在触发区域右侧弹出 */
        RIGHT,
        /** 在触发区域左侧弹出 */
        LEFT,
        /** 在触发区域上方弹出 */
        ABOVE
    }

    // ======================== Builder ========================

    /**
     * TooltipController 构建器。
     */
    public static final class Builder {
        private long delayMs = 1000L;
        private Direction direction = Direction.BELOW;
        private float textScale = 0.75f;
        private int padH = 6;
        private int padV = 3;

        private Builder() {}

        /** 悬浮多久后显示浮窗（毫秒，默认 1000） */
        public Builder delayMs(long ms) { this.delayMs = ms; return this; }

        /** 浮窗弹出方向（默认 BELOW） */
        public Builder direction(Direction dir) { this.direction = dir; return this; }

        /** 文字缩放比例（默认 0.75） */
        public Builder textScale(float scale) { this.textScale = scale; return this; }

        /** 水平内边距（默认 6） */
        public Builder padH(int padH) { this.padH = padH; return this; }

        /** 垂直内边距（默认 3） */
        public Builder padV(int padV) { this.padV = padV; return this; }

        /** 构建 TooltipController 实例 */
        public TooltipController build() { return new TooltipController(this); }
    }

    /** 创建构建器 */
    public static Builder builder() { return new Builder(); }

    // ======================== 渲染上下文 ========================

    /**
     * 浮窗渲染上下文——将所有渲染参数聚合成一个不可变值对象，
     * 避免 {@link #render(RenderContext)} 方法签名参数爆炸。
     */
    public record RenderContext(
            GuiGraphics g,
            int anchorX, int anchorY, int anchorW, int anchorH,
            String text,
            int color,
            int shortcutColor,
            int screenW, int screenH
    ) {}

    // ======================== 实例 ========================

    private static final float ALPHA_THRESHOLD = 0.001f;

    private final long delayMs;
    private final Direction direction;
    private final float textScale;
    private final int padH;
    private final int padV;

    private final HoverStateManager hoverState = new HoverStateManager();
    private long hoverStartTime = -1L;
    private boolean tooltipShown;

    private TooltipController(Builder builder) {
        this.delayMs = builder.delayMs;
        this.direction = builder.direction;
        this.textScale = builder.textScale;
        this.padH = builder.padH;
        this.padV = builder.padV;
    }

    // ======================== 生命周期 ========================

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

        this.hoverState.update(tooltipShown);
    }

    /**
     * 获取当前淡入淡出透明度 [0, 1]。
     */
    public float getAlpha() {
        return hoverState.getValue();
    }

    /**
     * 浮窗是否应渲染（透明度超过阈值）。
     */
    public boolean shouldRender() {
        return getAlpha() > ALPHA_THRESHOLD;
    }

    // ======================== 渲染 ========================

    /**
     * 渲染浮窗背景与文字（自动定位到触发区域相应方向，钳位到屏幕边界）。
     *
     * @param ctx 渲染上下文（含触发区域、文字、颜色、屏幕尺寸等参数）
     */
    public void render(RenderContext ctx) {
        float alpha = getAlpha();
        var font = Minecraft.getInstance().font;

        // 按行分割，计算最大宽度和总高度
        String[] lines = ctx.text().split("\n");
        int lineHeight = font.lineHeight;
        int lineGap = 1;
        float scaledLineH = lineHeight * textScale;
        float scaledLineGap = lineGap * textScale;
        int maxLineW = 0;
        for (String line : lines) {
            maxLineW = Math.max(maxLineW, font.width(line));
        }
        int tipW = (int) (maxLineW * textScale) + padH * 2;
        int tipH = (int) (scaledLineH * lines.length + scaledLineGap * (lines.length - 1)) + padV * 2;

        // 根据方向定位
        int tipX, tipY;
        switch (direction) {
            case BELOW -> {
                tipX = ctx.anchorX() + (ctx.anchorW() - tipW) / 2;
                tipY = ctx.anchorY() + ctx.anchorH() + 2;
            }
            case RIGHT -> {
                tipX = ctx.anchorX() + ctx.anchorW() + 2;
                tipY = ctx.anchorY();
            }
            case LEFT -> {
                tipX = ctx.anchorX() - tipW - 2;
                tipY = ctx.anchorY();
            }
            case ABOVE -> {
                tipX = ctx.anchorX() + (ctx.anchorW() - tipW) / 2;
                tipY = ctx.anchorY() - tipH - 2;
            }
            default -> throw new AssertionError("unexpected direction: " + direction);
        }

        // 钳位到屏幕边界
        tipX = Math.max(0, Math.min(tipX, ctx.screenW() - tipW));
        tipY = Math.max(0, Math.min(tipY, ctx.screenH() - tipH));

        // 渲染背景 + 文字（使用 BlendScope 自动管理混合状态）
        try (BlendScope blend = BlendScope.normal()) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            SpriteRenderer.drawNineSliceFloatingPanel(ctx.g(), tipX, tipY, tipW, tipH, false);

            // 逐行绘制文字
            float textY = tipY + padV;
            for (int i = 0; i < lines.length; i++) {
                int lineColor = (i == lines.length - 1) ? ctx.shortcutColor() : ctx.color();
                ctx.g().pose().pushPose();
                ctx.g().pose().translate(tipX + padH, textY, 0);
                ctx.g().pose().scale(textScale, textScale, 1.0f);
                TextRenderer.draw(ctx.g(), lines[i], 0, 0, lineColor);
                ctx.g().pose().popPose();
                textY += scaledLineH + scaledLineGap;
            }
        }
    }

    /**
     * 渲染浮窗（旧 API，使用散列参数）。
     *
     * @deprecated 使用 {@link #render(RenderContext)} 代替，
     *     将渲染参数包装为 {@link RenderContext} record 以避免参数爆炸。
     */
    @Deprecated
    public void render(GuiGraphics g, int anchorX, int anchorY, int anchorW, int anchorH,
                        String text, int color, int shortcutColor,
                        int screenW, int screenH) {
        render(new RenderContext(g, anchorX, anchorY, anchorW, anchorH,
                text, color, shortcutColor, screenW, screenH));
    }
}
