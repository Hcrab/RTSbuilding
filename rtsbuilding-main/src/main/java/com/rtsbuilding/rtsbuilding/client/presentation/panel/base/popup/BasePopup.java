package com.rtsbuilding.rtsbuilding.client.presentation.panel.base.popup;

import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.DarkUiPalette;
import com.rtsbuilding.rtsbuilding.client.util.render.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;

public abstract class BasePopup {

    
    protected boolean open;
    
    protected int x;
    
    protected int y;

    
    private AnimFloat[] hoverStates;

    
    private int[] itemContentWidths;
    
    private final int minPopupWidth = 80;

    

    
    protected int getItemHeight() { return 22; }
    
    protected int getPadH() { return 6; }
    
    protected int getPadV() { return 4; }
    
    protected int bgNormal() { return 0x00000000; }
    
    protected int bgHover() { return 0x442A3442; }

    

    
    protected int getPopupWidth() {
        if (itemContentWidths == null || itemContentWidths.length == 0) {
            return minPopupWidth;
        }
        int max = 0;
        for (int w : itemContentWidths) {
            if (w > max) max = w;
        }
        return Math.max(minPopupWidth, max + getPadH() * 2);
    }
    
    protected abstract int getItemCount();
    
    protected abstract void renderItem(GuiGraphics g, int index, int itemY, float hoverT);
    
    protected abstract boolean onItemClick(int index);

    

    
    protected void setItemContentWidths(int... widths) {
        this.itemContentWidths = widths;
    }

    
    protected void initAnims(int count) {
        hoverStates = new AnimFloat[count];
        for (int i = 0; i < count; i++) {
            hoverStates[i] = AnimFloat.hover();
        }
    }

    

    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    
    public void positionFromButton(int btnCenterX, int btnBottomY, int screenWidth) {
        int pw = getPopupWidth();
        boolean isRightSide = btnCenterX > screenWidth / 2;
        if (isRightSide) {
            
            this.x = btnCenterX - pw;
        } else {
            
            this.x = btnCenterX;
        }
        this.y = btnBottomY;
    }

    
    public void positionFromButtonAbove(int btnCenterX, int btnTopY, int screenWidth) {
        int pw = getPopupWidth();
        boolean isRightSide = btnCenterX > screenWidth / 2;
        if (isRightSide) {
            
            this.x = btnCenterX - pw;
        } else {
            
            this.x = btnCenterX;
        }
        this.y = btnTopY - menuHeight();
    }

    

    
    public void toggle() {
        this.open = !this.open;
        if (!this.open) {
            resetAllHoverAnims();
        }
    }

    
    public void open() {
        if (!this.open) toggle();
    }

    
    public void close() {
        if (this.open) toggle();
    }

    
    public boolean isOpen() {
        return open;
    }

    
    private void resetAllHoverAnims() {
        if (hoverStates != null) {
            for (AnimFloat hs : hoverStates) {
                hs.snapTo(0f);
            }
        }
    }

    

    
    private int menuHeight() {
        return getPadV() * 2 + getItemCount() * getItemHeight();
    }

    
    public boolean contains(int mx, int my) {
        if (!open) return false;
        return mx >= x && mx < x + getPopupWidth()
                && my >= y && my < y + menuHeight();
    }

    
    protected int itemY(int index) {
        return y + getPadV() + index * getItemHeight();
    }

    

    
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!open) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int pw = getPopupWidth();
        int ph = menuHeight();

        
        SdfRenderer.drawBorderedRoundedRect(g, x, y, pw, ph, 6, DarkUiPalette.border(), 0xFF2B2B2B, 1);

        
        int hoveredIndex = -1;
        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            boolean inside = mouseX >= x + getPadH() && mouseX < x + pw - getPadH()
                    && mouseY >= iy && mouseY < iy + getItemHeight();
            if (inside) {
                hoveredIndex = i;
            }
        }

        
        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            float t = hoverStates[i].track(i == hoveredIndex);

            
            if (t > 0.001f) {
                int bgColor = ColorAnimation.lerpRGB(bgNormal(), bgHover(), t);
                SdfRenderer.drawRoundedRect(g, x + getPadH(), iy, pw - getPadH() * 2, getItemHeight(), 3, bgColor);
            }

            renderItem(g, i, iy, t);
        }
    }

    

    
    public boolean handleClick(int mx, int my) {
        if (!open) return false;

        for (int i = 0; i < getItemCount(); i++) {
            int iy = itemY(i);
            if (mx >= x + getPadH() && mx < x + getPopupWidth() - getPadH()
                    && my >= iy && my < iy + getItemHeight()) {
                return onItemClick(i);
            }
        }
        return false;
    }
}
