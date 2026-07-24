package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;


public class FontRenderEnhancer {
    
    
    public static void renderHighQualityText(GuiGraphics guiGraphics, Font font, String text, float x, float y, int color) {
        
        setupHighQualityFontRendering();
        
        
        guiGraphics.drawString(font, text, (int)x, (int)y, color, false);
        
        
        restoreOriginalFontRendering();
    }
    
    
    public static void renderHighQualityText(GuiGraphics guiGraphics, Font font, Component text, float x, float y, int color) {
        
        setupHighQualityFontRendering();
        
        
        guiGraphics.drawString(font, text, (int)x, (int)y, color, false);
        
        
        restoreOriginalFontRendering();
    }
    
    
    private static void setupHighQualityFontRendering() {
        
        int fontTextureId = RenderSystem.getShaderTexture(0);
        
        
        RenderSystem.bindTexture(fontTextureId);
        
        
        
    }
    
    
    private static void restoreOriginalFontRendering() {
        
        int fontTextureId = RenderSystem.getShaderTexture(0);
        
        RenderSystem.bindTexture(fontTextureId);
        
        
    }
    
    
    public static void withHighQualityFont(Runnable renderOp) {
        setupHighQualityFontRendering();
        try {
            renderOp.run();
        } finally {
            restoreOriginalFontRendering();
        }
    }
    
    
    public static void preloadFontTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font != null) {
            
            int fontTextureId = RenderSystem.getShaderTexture(0);
            
            RenderSystem.bindTexture(fontTextureId);
            
            
        }
    }
}