package com.rtsbuilding.rtsbuilding.client.util.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * GUI渲染增强器 - 提供抗锯齿和高质量渲染功能
 * 
 * <p>此工具类提供了多种渲染增强功能，包括抗锯齿、平滑线条和高质量纹理过滤，
 * 以提升Minecraft GUI界面的视觉质量。</p>
 */
public class GuiRenderEnhancer {
    
    // Minecraft本身控制抗锯齿设置，我们只在渲染时启用相应的OpenGL状态
    private static boolean isAntialiasingSupported = true; // 假设支持，因为Minecraft运行环境一般都支持基础功能
    
    /**
     * 开始高质量GUI渲染
     * 
     * <p>启用平滑线条和其他渲染增强功能</p>
     */
    public static void beginHighQualityRender(GuiGraphics guiGraphics) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
    }
    
    /**
     * 结束高质量GUI渲染
     * 
     * <p>恢复到标准渲染设置</p>
     */
    public static void endHighQualityRender(GuiGraphics guiGraphics) {
        // 禁用高级渲染特性
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    /**
     * 开始抗锯齿渲染通道
     * 
     * <p>使用OpenGL状态实现更高级的平滑效果</p>
     */
    public static void beginAntialiasingRender() {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * 结束抗锯齿渲染通道
     */
    public static void endAntialiasingRender() {
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_BLEND);
    }
    
    /**
     * 渲染平滑矩形
     */
    public static void renderSmoothRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        beginAntialiasingRender();
        
        // 使用GuiGraphics的标准填充方法，但在启用抗锯齿的情况下
        guiGraphics.fill(x, y, x + width, y + height, color);
        
        endAntialiasingRender();
    }
    
    /**
     * 渲染平滑边框矩形
     */
    public static void renderSmoothRectBorder(GuiGraphics guiGraphics, int x, int y, int width, int height, int borderWidth, int color) {
        beginAntialiasingRender();
        
        // 渲染四个边
        // 顶边
        guiGraphics.fill(x, y, x + width, y + borderWidth, color);
        // 底边
        guiGraphics.fill(x, y + height - borderWidth, x + width, y + height, color);
        // 左边
        guiGraphics.fill(x, y + borderWidth, x + borderWidth, y + height - borderWidth, color);
        // 右边
        guiGraphics.fill(x + width - borderWidth, y + borderWidth, x + width, y + height - borderWidth, color);
        
        endAntialiasingRender();
    }
    
    /**
     * 应用高质量纹理过滤
     */
    public static void applyHighQualityTextureFiltering(ResourceLocation texture) {
        // 在Minecraft环境中，纹理过滤通常由Minecraft自身管理
        // 这里我们只做最小的干预
    }
    
    /**
     * 重置纹理过滤到默认值
     */
    public static void resetTextureFiltering() {
        // 在Minecraft环境中，让Minecraft自身管理纹理过滤
    }
    
    /**
     * 获取当前是否支持抗锯齿
     */
    public static boolean isAntialiasingSupported() {
        return isAntialiasingSupported;
    }
}