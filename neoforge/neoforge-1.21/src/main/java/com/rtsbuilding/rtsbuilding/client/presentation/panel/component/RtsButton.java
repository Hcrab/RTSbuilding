package com.rtsbuilding.rtsbuilding.client.presentation.panel.component;

import com.rtsbuilding.rtsbuilding.client.util.animate.AnimFloat;
import com.rtsbuilding.rtsbuilding.client.util.animate.ColorAnimation;
import com.rtsbuilding.rtsbuilding.client.util.render.CrossFadeRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.SpriteRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.TextRenderer;
import com.rtsbuilding.rtsbuilding.client.util.render.model.SpriteRegion;
import com.rtsbuilding.rtsbuilding.client.util.render.model.TextureInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class RtsButton extends AbstractButton {

    public interface OnPress {
        void onPress(RtsButton button);
    }

    private final OnPress onPress;
    
    private final TextureInfo texInfo;
    
    private final SpriteRegion normalRegion;
    
    private final SpriteRegion hoveredRegion;

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_HOVER = 0xFFE8F0FA;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    
    private final AnimFloat hoverState = AnimFloat.hover();

    
    public RtsButton(int x, int y, int width, int height, Component message,
                     ResourceLocation textureLocation, int textureU, int textureV,
                     int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
                     int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        
        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            this.texInfo = new TextureInfo(
                    textureLocation, fullTextureWidth, fullTextureHeight,
                    TextureInfo.ThemeLayout.HORIZONTAL_PAIR,
                    TextureInfo.FilterMode.PIXEL);
            this.normalRegion = new SpriteRegion(texInfo, textureU, textureV, textureWidth, textureHeight);
            this.hoveredRegion = new SpriteRegion(texInfo, textureU, hoverTextureV, textureWidth, hoverTextureHeight);
        } else {
            this.texInfo = null;
            this.normalRegion = null;
            this.hoveredRegion = null;
        }
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        
        if (normalRegion != null && hoveredRegion != null) {
            
            renderWithSprite(guiGraphics);
        } else {
            
            renderWithSolidColor(guiGraphics);
        }

        
        float hoverT = this.hoverState.track(isHovered);
        int textColor = this.active
                ? ColorAnimation.lerpRGB(TEXT_COLOR, TEXT_COLOR_HOVER, hoverT)
                : TEXT_COLOR_DISABLED;
        String label = TextRenderer.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        
        if (!label.isEmpty()) {
            TextRenderer.draw(guiGraphics, label, textX, textY, textColor);
        }
    }

    
    private void renderWithSprite(GuiGraphics guiGraphics) {
        float t = this.hoverState.get();
        CrossFadeRenderer.render(guiGraphics, t,
                () -> SpriteRenderer.drawSprite(guiGraphics, normalRegion.withTheme(), this.getX(), this.getY(), this.width, this.height),
                () -> SpriteRenderer.drawSprite(guiGraphics, hoveredRegion.withTheme(), this.getX(), this.getY(), this.width, this.height));
    }

    
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        
        float t = this.hoverState.get();
        int backgroundColor = ColorAnimation.lerpRGB(BUTTON_BACKGROUND, BUTTON_HOVER, t);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, backgroundColor);
        guiGraphics.hLine(this.getX(), this.getX() + this.width, this.getY(), BORDER_LIGHT);
        guiGraphics.hLine(this.getX(), this.getX() + this.width, this.getY() + this.height, BORDER_DARK);
        guiGraphics.vLine(this.getX(), this.getY(), this.getY() + this.height, BORDER_LIGHT);
        guiGraphics.vLine(this.getX() + this.width, this.getY(), this.getY() + this.height, BORDER_DARK);
    }

    

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

}
