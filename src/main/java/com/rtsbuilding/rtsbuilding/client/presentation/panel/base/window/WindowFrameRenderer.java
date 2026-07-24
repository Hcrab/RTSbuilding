package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.component.RtsButton;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public final class WindowFrameRenderer {

    
    private static final int CLOSE_BUTTON_SIZE = 14;
    
    private static final int CLOSE_FRAME_W = 512;
    
    private static final int CLOSE_SHEET_W = 1024;
    
    private static final int CLOSE_SHEET_H = 1024;
    
    private static final int CLOSE_STATE_H = 512;
    
    private static final ResourceLocation CLOSE_BUTTON_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/close_button.png");

    private WindowFrameRenderer() {}

    
    public static RtsButton createCloseButton(Runnable onClose) {
        return new RtsButton(0, 0, CLOSE_BUTTON_SIZE, CLOSE_BUTTON_SIZE,
                Component.empty(), CLOSE_BUTTON_TEXTURE,
                0, 0,
                CLOSE_FRAME_W, CLOSE_STATE_H,
                CLOSE_STATE_H, CLOSE_STATE_H,
                CLOSE_SHEET_W, CLOSE_SHEET_H,
                btn -> onClose.run());
    }

    
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

    

    
    public static void renderFrame(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        renderPanelBackground(g, ctx);
        renderTitleBar(g, mouseX, mouseY, ctx);
    }

    

    
    private static void renderPanelBackground(GuiGraphics g, Context ctx) {
        float t = ctx.hoverAnimProgress();
        int wx = ctx.windowX();
        int wy = ctx.windowY();
        int ww = ctx.windowWidth();
        int wh = ctx.windowHeight();

        if (t <= 0.001f) {
            renderPanelLayer(g, wx, wy, ww, wh, ctx, 1.0f, false);
        } else if (t >= 0.999f) {
            renderPanelLayer(g, wx, wy, ww, wh, ctx, 1.0f, true);
        } else {
            renderPanelLayer(g, wx, wy, ww, wh, ctx, 1.0f, false);
            g.flush();
            renderPanelLayer(g, wx, wy, ww, wh, ctx, t, true);
            g.flush();
        }
    }

    
    private static void renderPanelLayer(GuiGraphics g, int wx, int wy, int ww, int wh,
                                          Context ctx, float alphaMultiplier, boolean hovered) {
        int tint = hovered ? ctx.panelHoverBgColor() : ctx.panelBgColor();
        float a = (float) (tint >> 24 & 0xFF) / 255.0F * alphaMultiplier;
        float r = (float) (tint >> 16 & 0xFF) / 255.0F;
        float gr = (float) (tint >> 8 & 0xFF) / 255.0F;
        float b = (float) (tint & 0xFF) / 255.0F;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, a);
        SpriteRenderer.drawNineSlicePanel(g, wx, wy, ww, wh, hovered);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    

    
    private static void renderTitleBar(GuiGraphics g, int mouseX, int mouseY, Context ctx) {
        int titleH = ctx.titleBarHeight();
        if (titleH <= 0) return;

        
        renderTitleBarBackground(g, ctx, titleH);

        
        renderTitleText(g, ctx, titleH);

        
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
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(r, gr, b, a);
        
        
        
        SpriteRenderer.drawNineSliceDragPanel(g, ctx.windowX() + 3, ctx.windowY() + 3,
                ctx.windowWidth() - 6, titleH, false);
        g.flush();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderTitleText(GuiGraphics g, Context ctx, int titleH) {
        String title = TextRenderer.trimToWidth(Minecraft.getInstance().font, ctx.title().getString(),
                Math.max(8, ctx.windowWidth() - 36));
        int textY = ctx.windowY() + Math.max(1, (titleH - Minecraft.getInstance().font.lineHeight) / 2) + 2;
        TextRenderer.draw(g, title, ctx.windowX() + 8, textY, ctx.titleTextColor());
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
