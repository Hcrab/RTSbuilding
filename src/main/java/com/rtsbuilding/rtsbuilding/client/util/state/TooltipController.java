package com.rtsbuilding.rtsbuilding.client.util.state;

import com.rtsbuilding.rtsbuilding.client.util.render.BlendScope;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;


public class TooltipController {

    
    public enum Direction {
        
        BELOW,
        
        RIGHT,
        
        LEFT,
        
        ABOVE
    }

    

    
    public static final class Builder {
        private long delayMs = 1000L;
        private Direction direction = Direction.BELOW;
        private float textScale = 0.75f;
        private int padH = 6;
        private int padV = 3;

        private Builder() {}

        
        public Builder delayMs(long ms) { this.delayMs = ms; return this; }

        
        public Builder direction(Direction dir) { this.direction = dir; return this; }

        
        public Builder textScale(float scale) { this.textScale = scale; return this; }

        
        public Builder padH(int padH) { this.padH = padH; return this; }

        
        public Builder padV(int padV) { this.padV = padV; return this; }

        
        public TooltipController build() { return new TooltipController(this); }
    }

    
    public static Builder builder() { return new Builder(); }

    

    
    public record RenderContext(
            GuiGraphics g,
            int anchorX, int anchorY, int anchorW, int anchorH,
            String text,
            int color,
            int shortcutColor,
            int screenW, int screenH
    ) {}

    

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

    
    public float getAlpha() {
        return hoverState.getValue();
    }

    
    public boolean shouldRender() {
        return getAlpha() > ALPHA_THRESHOLD;
    }

    

    
    public void render(RenderContext ctx) {
        float alpha = getAlpha();
        var font = Minecraft.getInstance().font;

        
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

        
        tipX = Math.max(0, Math.min(tipX, ctx.screenW() - tipW));
        tipY = Math.max(0, Math.min(tipY, ctx.screenH() - tipH));

        
        try (BlendScope blend = BlendScope.normal()) {
            com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            SpriteRenderer.drawNineSliceFloatingPanel(ctx.g(), tipX, tipY, tipW, tipH, false);

            
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

    
    @Deprecated
    public void render(GuiGraphics g, int anchorX, int anchorY, int anchorW, int anchorH,
                        String text, int color, int shortcutColor,
                        int screenW, int screenH) {
        render(new RenderContext(g, anchorX, anchorY, anchorW, anchorH,
                text, color, shortcutColor, screenW, screenH));
    }
}
