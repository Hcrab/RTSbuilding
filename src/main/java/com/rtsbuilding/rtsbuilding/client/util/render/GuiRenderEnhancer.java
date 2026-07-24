package com.rtsbuilding.rtsbuilding.client.util.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;


@Deprecated(since = "1.0", forRemoval = true)
public class GuiRenderEnhancer {
    
    private static final boolean isAntialiasingSupported = true;
    
    
    @Deprecated
    public static void beginHighQualityRender(GuiGraphics guiGraphics) {
    }
    
    
    @Deprecated
    public static void endHighQualityRender(GuiGraphics guiGraphics) {
    }
    
    
    public static void beginAntialiasingRender() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    
    
    public static void endAntialiasingRender() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    
    public static void renderSmoothRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        beginAntialiasingRender();
        
        
        guiGraphics.fill(x, y, x + width, y + height, color);
        
        endAntialiasingRender();
    }
    
    
    public static void renderSmoothRectBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderWidth, int color) {
        beginAntialiasingRender();
        
        
        
        guiGraphics.fill(x, y, x + width, y + borderWidth, color);
        
        guiGraphics.fill(x, y + height - borderWidth, x + width, y + height, color);
        
        guiGraphics.fill(x, y + borderWidth, x + borderWidth, y + height - borderWidth, color);
        
        guiGraphics.fill(x + width - borderWidth, y + borderWidth, x + width, y + height - borderWidth, color);
        
        endAntialiasingRender();
    }
    
    
    public static void applyHighQualityTextureFiltering(ResourceLocation texture) {
        
        
    }
    
    
    public static void resetTextureFiltering() {
        
    }
    
    
    public static boolean isAntialiasingSupported() {
        return isAntialiasingSupported;
    }
}