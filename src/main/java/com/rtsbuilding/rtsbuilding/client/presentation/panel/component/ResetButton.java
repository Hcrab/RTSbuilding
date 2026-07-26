package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;


public class ResetButton {

    

    
    private static final ResourceLocation BASE_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_2.png");
    private static final int BASE_TEX_W = 32;
    private static final int BASE_TEX_H = 48;
    
    private static final int BASE_STATE_H = 16;
    private static final int BASE_BORDER = 4;
    private static final TextureInfo BASE_TEX_INFO = new TextureInfo(
            BASE_TEXTURE, BASE_TEX_W, BASE_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR, TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion BASE_NINE_SLICE = NineSliceRegion.fullTheme(
            BASE_TEX_INFO, BASE_STATE_H, BASE_BORDER);

    

    
    private static final ResourceLocation RESET_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/reset.png");
    private static final int RESET_TEX_W = 128;
    private static final int RESET_TEX_H = 64;
    private static final TextureInfo RESET_TEX_INFO = new TextureInfo(
            RESET_TEXTURE, RESET_TEX_W, RESET_TEX_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.NORMAL);
    
    private static final SpriteRegion RESET_SPRITE = new SpriteRegion(
            RESET_TEX_INFO, 0, 0, RESET_TEX_W / 2, RESET_TEX_H);

    

    
    public static final int BTN_SIZE = 16;

    

    
    private final AnimFloat hoverState = AnimFloat.hover();

    
    private int areaX, areaY;

    
    private Runnable resetAction;

    

    
    public void setResetAction(Runnable action) {
        this.resetAction = action;
    }

    

    
    public void render(GuiGraphics g, int mx, int my, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        
        boolean hovering = mx >= btnX && mx < btnX + BTN_SIZE
                && my >= btnY && my < btnY + BTN_SIZE;
        float t = this.hoverState.track(hovering);

        
        CrossFadeRenderer.render(g, t,
                () -> SpriteRenderer.drawNineSlice(g, BASE_NINE_SLICE.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE),
                () -> SpriteRenderer.drawNineSlice(g, BASE_NINE_SLICE.withTheme().withVOffset(BASE_STATE_H), btnX, btnY, BTN_SIZE, BTN_SIZE));

        
        SpriteRenderer.drawSprite(g, RESET_SPRITE.withTheme(), btnX, btnY, BTN_SIZE, BTN_SIZE);
    }

    

    
    public boolean handleClick(double mx, double my) {
        if (mx >= areaX && mx < areaX + BTN_SIZE
                && my >= areaY && my < areaY + BTN_SIZE) {
            if (resetAction != null) {
                resetAction.run();
            }
            return true;
        }
        return false;
    }
}
