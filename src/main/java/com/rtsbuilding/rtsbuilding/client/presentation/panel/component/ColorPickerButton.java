package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.window.RtsPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorGroup;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorPickerPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.color.ColorSource;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.NineSliceRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import com.rtsbuilding.rtsbuilding.client.util.state.HoverStateManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;


public class ColorPickerButton {

    

    
    private static final ResourceLocation FOLD_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/base/base_ui/base_ui_3.png");
    private static final int FOLD_TEX_W = 32;
    private static final int FOLD_TEX_FILE_H = 32;
    
    private static final int FOLD_TEX_STATE_H = 16;
    
    private static final int FOLD_BORDER = 4;
    private static final TextureInfo FOLD_TEX_INFO = new TextureInfo(
            FOLD_TEXTURE, FOLD_TEX_W, FOLD_TEX_FILE_H,
            TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
            TextureInfo.FilterMode.PIXEL);
    private static final NineSliceRegion FOLD_NINE_SLICE = NineSliceRegion.fullTheme(
            FOLD_TEX_INFO, FOLD_TEX_STATE_H, FOLD_BORDER);

    
    private static final ResourceLocation COLOR_WHEEL_TEXTURE = ResourceLocation.tryParse(
            "rtsbuilding:textures/gui/color/colorwheel.png");
    private static final int COLOR_WHEEL_TEX_W = 89;
    private static final int COLOR_WHEEL_TEX_H = 89;
    
    private static final int COLOR_WHEEL_FRAME = 12;

    
    private static final TextureInfo COLOR_WHEEL_TEX_INFO = new TextureInfo(
            COLOR_WHEEL_TEXTURE, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H,
            TextureInfo.ThemeLayout.NONE, TextureInfo.FilterMode.NORMAL);

    
    public static final int BTN_SIZE = 16;

    

    
    private final HoverStateManager hoverState = new HoverStateManager();

    
    private int areaX, areaY;

    
    private ColorPickerPanel colorPickerPanel;

    
    @Nullable
    private ColorSource colorSource;

    
    @Nullable
    private ColorGroup colorGroup;

    
    private RtsPanel parentPanel;

    

    
    public void setColorPickerPanel(ColorPickerPanel panel) {
        this.colorPickerPanel = panel;
    }

    
    public void setColorSource(@Nullable ColorSource source) {
        this.colorSource = source;
        this.colorGroup = null;
    }

    
    public void setColorGroup(@Nullable ColorGroup group) {
        this.colorGroup = group;
        this.colorSource = null;
    }

    
    public void setParentPanel(RtsPanel parent) {
        this.parentPanel = parent;
    }

    

    
    public void render(GuiGraphics g, int mouseX, int mouseY, int btnX, int btnY) {
        this.areaX = btnX;
        this.areaY = btnY;

        
        boolean hovering = mouseX >= btnX && mouseX < btnX + BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + BTN_SIZE;
        float t = this.hoverState.update(hovering);

        
        CrossFadeRenderer.render(g, t,
                () -> renderBackground(g, btnX, btnY, 0),
                () -> renderBackground(g, btnX, btnY, FOLD_TEX_STATE_H));

        
        int iconX = btnX + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        int iconY = btnY + (BTN_SIZE - COLOR_WHEEL_FRAME) / 2;
        SpriteRegion wheelRegion = new SpriteRegion(COLOR_WHEEL_TEX_INFO, 0, 0, COLOR_WHEEL_TEX_W, COLOR_WHEEL_TEX_H);
        SpriteRenderer.drawSprite(g, wheelRegion,
                iconX, iconY, COLOR_WHEEL_FRAME, COLOR_WHEEL_FRAME);
    }

    
    private void renderBackground(GuiGraphics g, int btnX, int btnY, int vOffset) {
        SpriteRenderer.drawNineSlice(g,
                FOLD_NINE_SLICE.withTheme().withVOffset(vOffset),
                btnX, btnY, BTN_SIZE, BTN_SIZE);
    }

    

    
    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseX >= areaX && mouseX < areaX + BTN_SIZE
                && mouseY >= areaY && mouseY < areaY + BTN_SIZE) {
            if (colorPickerPanel != null) {
                if (!colorPickerPanel.isOpen()) {
                    
                    applyColor();
                    if (parentPanel != null) {
                        parentPanel.openChild(colorPickerPanel);
                    } else {
                        colorPickerPanel.setOpen(true);
                    }
                } else {
                    
                    applyColor();
                }
            }
            return true;
        }
        return false;
    }

    
    private void applyColor() {
        if (colorGroup != null) {
            colorPickerPanel.setColorGroup(colorGroup);
        } else if (colorSource != null) {
            colorPickerPanel.setColorSource(colorSource);
        }
    }
}
