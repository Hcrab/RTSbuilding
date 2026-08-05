package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.overlay;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public abstract class DownOverlayLayer implements OverlayContext {



    

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



        
        g.flush();
        Screen screen = Minecraft.getInstance().screen;
        int inset = 2;
        if (screen instanceof BuilderScreen bs) {
            bs.enableRtsScissor(g, x + inset, y + inset, x + width - inset, y + height - inset);
        } else {
            g.enableScissor(x + inset, y + inset, x + width - inset, y + height - inset);
        }

        
        renderContent(g);

        g.disableScissor();

        
        int borderColor = ColorAnimation.lerpRGB(DarkUiPalette.accent(), ColorAnimation.scale(DarkUiPalette.accent(), 1.4f), hoverT);
        SdfRenderer.drawRoundedOutline(g, x + 1, y + 1, width - 2, height - 2, 8, borderColor);

        
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
