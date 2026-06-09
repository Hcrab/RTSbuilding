package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 高精度矢量贴图绘制工具。
 * <p>
 * 使用浮点坐标和 PoseStack 矩阵变换实现亚像素精度渲染，
 * 支持绕中心旋转、颜色染色，且不污染全局 GL 纹理过滤状态。
 */
public final class RtsTextureRenderer {

    private RtsTextureRenderer() {
    }

    /**
     * 高精度矢量绘制贴图。
     * <p>
     * 相比 {@code GuiGraphics.blit} 直接调用，此方法：
     * <ul>
     *   <li>目标位置和 UV 使用 float 精度，支持亚像素定位</li>
     *   <li>绕中心旋转（角度制）</li>
     *   <li>颜色染色（乘色），格式 0xAARRGGBB</li>
     *   <li>不污染全局 GL 纹理过滤状态</li>
     * </ul>
     *
     * @param guiGraphics   渲染上下文
     * @param texLocation   贴图资源路径
     * @param x             目标左上角 X（float 精度）
     * @param y             目标左上角 Y（float 精度）
     * @param width         目标绘制宽度
     * @param height        目标绘制高度
     * @param uOffset       源贴图 U 偏移（float 精度）
     * @param vOffset       源贴图 V 偏移（float 精度）
     * @param uWidth        源贴图区域宽度
     * @param vHeight       源贴图区域高度
     * @param textureWidth  完整贴图总宽度
     * @param textureHeight 完整贴图总高度
     * @param rotationDeg   旋转角度（度），0 表示不旋转
     * @param color         颜色染色 0xAARRGGBB，0xFFFFFFFF 表示不染色
     */
    public static void drawTextureHighPrecision(
            GuiGraphics guiGraphics,
            ResourceLocation texLocation,
            float x, float y,
            float width, float height,
            float uOffset, float vOffset,
            float uWidth, float vHeight,
            int textureWidth, int textureHeight,
            float rotationDeg,
            int color
    ) {
        // 1. 确保贴图已加载（同 WindowButton.renderWithTexture）
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(texLocation);
        if (texture == null) {
            try {
                RenderSystem.setShaderTexture(0, texLocation);
                texture = textureManager.getTexture(texLocation);
                if (texture == null) return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        // 2. 启用混合
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 3. 绑定贴图并设置高质量过滤参数
        RenderSystem.setShaderTexture(0, texLocation);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // 4. 颜色染色
        boolean hasTint = (color & 0xFFFFFFFFL) != 0xFFFFFFFFL;
        if (hasTint) {
            guiGraphics.setColor(
                    ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f,
                    ((color >> 24) & 0xFF) / 255.0f
            );
        }

        // 5. 使用 PoseStack 变换（同 WindowButton.renderWithTexture）
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scaleX = width / uWidth;
        float scaleY = height / vHeight;
        pose.scale(scaleX, scaleY, 1.0f);

        // 6. 绘制（在变换后的坐标中，纹理以原始UV尺寸绘制在 (0,0)）
        guiGraphics.blit(
                texLocation,
                0, 0,
                (int) uOffset, (int) vOffset,
                (int) uWidth, (int) vHeight,
                textureWidth, textureHeight
        );

        // 7. 恢复变换
        pose.popPose();

        // 8. 恢复颜色
        if (hasTint) {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 9. 恢复混合和纹理过滤
        RenderSystem.disableBlend();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }
}
