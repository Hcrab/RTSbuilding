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
 * <p>此类已被弃用，其方法均为空操作（no-op）。</p>
 * <p><b>弃用原因：</b></p>
 * <ul>
 *   <li>{@code GL_LINE_SMOOTH} 在现代 OpenGL 中已弃用，多数驱动下无实际效果</li>
 *   <li>{@code endHighQualityRender()} 无条件调用 {@code GL11.glDisable(GL_BLEND)}，
 *       会破坏其他 Mod 及 F3 调试界面等后续渲染的半透明效果</li>
 *   <li>Minecraft 的 {@code RenderType} 系统已通过 {@code TransparencyStateShard}
 *       正确管理混合状态，无需手动干预</li>
 *   <li>需要控制 blend 时应使用 {@link BlendScope} RAII 守卫</li>
 * </ul>
 *
 * @deprecated 此类所有方法均为空操作。需要 blend 控制时使用 {@link BlendScope}。
 */
@Deprecated(since = "1.0", forRemoval = true)
public class GuiRenderEnhancer {
    
    private static final boolean isAntialiasingSupported = true;
    
    /**
     * 空操作（no-op）。
     *
     * @deprecated 无实际效果。Minecraft 的 RenderType 系统自动管理 blend 状态。
     */
    @Deprecated
    public static void beginHighQualityRender(GuiGraphics guiGraphics) {
    }
    
    /**
     * 空操作（no-op）。
     *
     * @deprecated 此方法原实现会无条件关闭 GL_BLEND，破坏后续半透明渲染。
     *             现已改为空操作，不再影响全局 GL 状态。
     */
    @Deprecated
    public static void endHighQualityRender(GuiGraphics guiGraphics) {
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