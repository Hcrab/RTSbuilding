package com.rtsbuilding.rtsbuilding.client.presentation.panel.background;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.topbar.TopBarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ScreenBackgroundPanel implements RtsPanelApi {

    private BuilderScreen screen;

    private final AnimFloat hoverAnim = AnimFloat.hover();

    

    
    private static final ResourceLocation SCREEN_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/screen_ui.png");
    
    private static final int TEX_W = 256;
    
    private static final int TEX_FILE_H = 256;
    
    private static final int HALF_W = TEX_W / 2;       
    
    private static final int STATE_H = 128;
    
    private static final int ACTIVE_V_OFFSET = 128;
    
    private static final int BORDER = 8;
    private static final TextureInfo SCREEN_TEX_INFO = new TextureInfo(
            SCREEN_UI_TEXTURE, TEX_W, TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SCREEN_NINE_SLICE = NineSliceRegion.fullTheme(
            SCREEN_TEX_INFO, STATE_H, BORDER);

    
    public static final int BACKGROUND_TOP_Y = TopBarLayoutHelper.TOP_BAR_HEIGHT;

    
    public static final double CAPTURE_SCALE = 1.24;
    @Override
    public void init(BuilderScreen screen) {
        this.screen = screen;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.screen == null) return;
        
        if (ViewCaptureService.hasValidFrame()) {
            renderCapturedFrameAt(g, 0, 0, this.screen.width, this.screen.height);
        }
    }

    
    @Override
    public void renderOverlays(GuiGraphics g, int mouseX, int mouseY) {
        if (this.screen == null) return;

        renderNineSliceFallback(g, mouseX, mouseY);
    }

    
    public void renderCapturedFrameAt(GuiGraphics g, int destX, int destY, int destW, int destH) {
        int capW = ViewCaptureService.getCaptureWidth();
        int capH = ViewCaptureService.getCaptureHeight();
        if (capW <= 0 || capH <= 0 || destW <= 0 || destH <= 0) return;

        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        
        double capAspect = (double) capW / capH;
        double destAspect = (double) destW / destH;

        int renderW, renderH, renderX, renderY;
        if (capAspect > destAspect) {
            
            renderW = destW;
            renderH = (int) Math.round(destW / capAspect);
            renderX = destX;
            renderY = destY + (destH - renderH) / 2;
        } else {
            
            renderH = destH;
            renderW = (int) Math.round(destH * capAspect);
            renderX = destX + (destW - renderW) / 2;
            renderY = destY;
        }

        
        renderW = (int) Math.round(renderW * CAPTURE_SCALE);
        renderH = (int) Math.round(renderH * CAPTURE_SCALE);
        renderX = destX + (destW - renderW) / 2;
        renderY = destY + (destH - renderH) / 2;

        
        if (renderX > destX || renderY > destY
                || renderX + renderW < destX + destW
                || renderY + renderH < destY + destH) {
            g.fill(destX, destY, destX + destW, destY + destH, 0xFF000000);
        }

        
        
        RenderSystem.disableBlend();

        
        g.blit(ViewCaptureService.getCapturedFrameLocation(),
                renderX, renderY, renderW, renderH,
                0, 0, capW, capH,
                capW, capH);

        
        RenderSystem.enableBlend();
    }

    
    private void renderNineSliceFallback(GuiGraphics g, int mouseX, int mouseY) {
        
        int contentW = this.screen.width - this.screen.getRightSidebarWidth();
        int contentH = this.screen.height - BACKGROUND_TOP_Y - this.screen.getDownSidebarHeight();
        if (contentW <= 0 || contentH <= 0) return;

        
        int leftW = this.screen.getLeftSidebarWidth();
        boolean hovered = (this.screen == null || !this.screen.isMouseOverUI(mouseX, mouseY))
                && mouseX >= leftW && mouseX < contentW
                && mouseY >= BACKGROUND_TOP_Y && mouseY < BACKGROUND_TOP_Y + contentH;

        float t = hoverAnim.track(hovered);

        NineSliceRegion normalSpec = SCREEN_NINE_SLICE.withTheme();
        NineSliceRegion activeSpec = SCREEN_NINE_SLICE.withVOffset(ACTIVE_V_OFFSET).withTheme();
        CrossFadeRenderer.render(g, t,
                () -> SpriteRenderer.drawNineSlice(g, normalSpec, 0, BACKGROUND_TOP_Y, contentW, contentH),
                () -> SpriteRenderer.drawNineSlice(g, activeSpec, 0, BACKGROUND_TOP_Y, contentW, contentH));
    }

    
    public static ContentBounds contentBounds(BuilderScreen screen) {
        int contentW = screen.width - screen.getRightSidebarWidth();
        int contentH = screen.height - BACKGROUND_TOP_Y - screen.getDownSidebarHeight();
        return new ContentBounds(0, BACKGROUND_TOP_Y, Math.max(contentW, 0), Math.max(contentH, 0));
    }

    
    public record ContentBounds(int left, int top, int width, int height) {
        public int right() { return left + width; }
        public int bottom() { return top + height; }
    }

    

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
