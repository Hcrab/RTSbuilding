package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

public abstract class DownOverlayLayer implements OverlayContext {

    

    
    private static final ResourceLocation OVERLAY_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/overlay_ui.png");
    
    private static final int OVERLAY_TEX_W = 256;
    
    private static final int OVERLAY_TEX_FILE_H = 256;
    
    private static final int OVERLAY_STATE_H = 128;
    
    private static final int OVERLAY_ACTIVE_V_OFFSET = 128;
    
    private static final int OVERLAY_BORDER = 8;
    private static final TextureInfo OVERLAY_TEX_INFO = new TextureInfo(
            OVERLAY_TEXTURE, OVERLAY_TEX_W, OVERLAY_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion OVERLAY_NINE_SLICE = NineSliceRegion.fullTheme(
            OVERLAY_TEX_INFO, OVERLAY_STATE_H, OVERLAY_BORDER);

    

    
    private static final ResourceLocation SCREEN_UI_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/screen_ui.png");
    private static final int SCREEN_UI_TEX_W = 256;
    private static final int SCREEN_UI_TEX_FILE_H = 256;
    private static final int SCREEN_UI_STATE_H = 128;
    private static final int SCREEN_UI_BORDER = 8;
    private static final TextureInfo SCREEN_UI_TEX_INFO = new TextureInfo(
            SCREEN_UI_TEXTURE, SCREEN_UI_TEX_W, SCREEN_UI_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion SCREEN_UI_NINE_SLICE = NineSliceRegion.fullTheme(
            SCREEN_UI_TEX_INFO, SCREEN_UI_STATE_H, SCREEN_UI_BORDER);

    

    private int x;
    private int y;
    private int width;
    private int height;

    
    private int lastMouseX;
    
    private int lastMouseY;

    
    private boolean dividerDragging;

    
    private final AnimFloat hoverAnim = AnimFloat.fade();
    private boolean prevHovered;

    
    public void setDividerDragging(boolean dragging) {
        this.dividerDragging = dragging;
    }

    
    public boolean isDividerDragging() {
        return this.dividerDragging;
    }
    
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    
    public void setLastMousePos(int mouseX, int mouseY) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }

    public int getLastMouseX() { return lastMouseX; }
    public int getLastMouseY() { return lastMouseY; }

    

    
    public void render(GuiGraphics g, boolean hovered) {
        if (width <= 0 || height <= 0) return;

        
        if (dividerDragging) {
            hovered = false;
        }

        
        if (hovered != prevHovered) {
            hoverAnim.target(hovered ? 1f : 0f);
            prevHovered = hovered;
        }
        float hoverT = hoverAnim.get();

        
        CrossFadeRenderer.render(g, hoverT,
                () -> SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme(), x, y, width, height),
                () -> SpriteRenderer.drawNineSlice(g, OVERLAY_NINE_SLICE.withTheme().withVOffset(OVERLAY_ACTIVE_V_OFFSET), x, y, width, height));

        
        g.flush();
        Screen screen = Minecraft.getInstance().screen;
        int inset = 2;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, x + inset, y + inset, x + width - inset, y + height - inset);
        } else {
            g.enableScissor(x + inset, y + inset, x + width - inset, y + height - inset);
        }

        
        renderContent(g);

        
        CrossFadeRenderer.render(g, hoverT,
                () -> SpriteRenderer.drawNineSlice(g, SCREEN_UI_NINE_SLICE.withTheme(), x, y, width, height),
                () -> SpriteRenderer.drawNineSlice(g, SCREEN_UI_NINE_SLICE.withTheme().withVOffset(SCREEN_UI_STATE_H), x, y, width, height));

        g.disableScissor();

        
        postRenderContent(g);
    }

    
    protected void renderContent(GuiGraphics g) {
        
    }

    
    protected void postRenderContent(GuiGraphics g) {
        
    }

    

    
    public boolean contains(int px, int py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    

    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return false;
    }

    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }
}
