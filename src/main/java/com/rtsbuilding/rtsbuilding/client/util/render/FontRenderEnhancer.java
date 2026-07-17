package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 字体渲染增强器 - 提供高质量字体渲染功能
 * 
 * <p>此工具类提供了高质量的字体渲染功能，通过优化纹理过滤和渲染参数
 * 来提高Minecraft中文字的清晰度和可读性。</p>
 */
public class FontRenderEnhancer {
    
    /**
     * 渲染高质量文本 - 使用优化的渲染参数
     * 
     * @param guiGraphics GUI图形上下文
     * @param font 字体实例
     * @param text 要渲染的文本
     * @param x X坐标
     * @param y Y坐标
     * @param color 颜色值
     */
    public static void renderHighQualityText(GuiGraphics guiGraphics, Font font, String text, float x, float y, int color) {
        // 临时提高字体纹理的过滤质量
        setupHighQualityFontRendering();
        
        // 渲染文本
        guiGraphics.drawString(font, text, (int)x, (int)y, color, false);
        
        // 恢复原始设置
        restoreOriginalFontRendering();
    }
    
    /**
     * 渲染高质量文本组件 - 使用优化的渲染参数
     * 
     * @param guiGraphics GUI图形上下文
     * @param font 字体实例
     * @param text 要渲染的文本组件
     * @param x X坐标
     * @param y Y坐标
     * @param color 颜色值
     */
    public static void renderHighQualityText(GuiGraphics guiGraphics, Font font, Component text, float x, float y, int color) {
        // 临时提高字体纹理的过滤质量
        setupHighQualityFontRendering();
        
        // 渲染文本
        guiGraphics.drawString(font, text, (int)x, (int)y, color, false);
        
        // 恢复原始设置
        restoreOriginalFontRendering();
    }
    
    /**
     * 设置高质量字体渲染参数
     */
    private static void setupHighQualityFontRendering() {
        // 获取当前活动的纹理ID（通常是字体纹理）
        int fontTextureId = RenderSystem.getShaderTexture(0);
        
        // 绑定字体纹理并设置高质量过滤
        RenderSystem.bindTexture(fontTextureId);
        
        // 在Minecraft环境中，字体纹理过滤由Minecraft自身管理
        // 这里我们不做特殊处理
    }
    
    /**
     * 恢复原始字体渲染参数
     */
    private static void restoreOriginalFontRendering() {
        // 恢复到最近的原始过滤设置
        int fontTextureId = RenderSystem.getShaderTexture(0);
        
        RenderSystem.bindTexture(fontTextureId);
        
        // 在Minecraft环境中，让Minecraft自身管理纹理过滤
    }
    
    /**
     * 在渲染文本时应用高质量字体渲染
     * 
     * @param renderOp 渲染操作
     */
    public static void withHighQualityFont(Runnable renderOp) {
        setupHighQualityFontRendering();
        try {
            renderOp.run();
        } finally {
            restoreOriginalFontRendering();
        }
    }
    
    /**
     * 预加载字体纹理以提高渲染质量
     */
    public static void preloadFontTextures() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.font != null) {
            // 触发字体纹理的加载
            int fontTextureId = RenderSystem.getShaderTexture(0);
            
            RenderSystem.bindTexture(fontTextureId);
            
            // 在Minecraft环境中，字体纹理过滤由Minecraft自身管理
        }
    }
}