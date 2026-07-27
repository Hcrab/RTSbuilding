package com.rtsbuilding.rtsbuilding.client.util;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.nio.FloatBuffer;

/**
 * Forge 1.12 GUI 的高精度纹理绘制器。
 *
 * <p>本类只使用 1.12 的固定管线和私有的 Tessellator 绘制批次。每次绘制都会恢复进入方法前的
 * 矩阵、颜色、混合函数、纹理过滤、纹理绑定与纹理启用状态，避免窗口图标污染后续 HUD。</p>
 */
public final class RtsTextureRenderer {
    private RtsTextureRenderer() {
    }

    public static void drawTextureHighPrecision(
            LegacyGuiGraphics ignoredGraphics,
            ResourceLocation textureLocation,
            float x,
            float y,
            float width,
            float height,
            float uOffset,
            float vOffset,
            float uWidth,
            float vHeight,
            int textureWidth,
            int textureHeight,
            float rotationDeg,
            int color) {
        if (textureLocation == null || width <= 0.0F || height <= 0.0F
                || textureWidth <= 0 || textureHeight <= 0) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return;
        }

        boolean textureEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        FloatBuffer previousColor = BufferUtils.createFloatBuffer(4);
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, previousColor);
        float oldRed = previousColor.get(0);
        float oldGreen = previousColor.get(1);
        float oldBlue = previousColor.get(2);
        float oldAlpha = previousColor.get(3);

        int previousMinFilter = GL11.GL_NEAREST;
        int previousMagFilter = GL11.GL_NEAREST;
        boolean targetBound = false;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableTexture2D();
            minecraft.getTextureManager().bindTexture(textureLocation);
            targetBound = true;
            previousMinFilter = GL11.glGetTexParameteri(
                    GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
            previousMagFilter = GL11.glGetTexParameteri(
                    GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.color(
                    ((color >>> 16) & 0xFF) / 255.0F,
                    ((color >>> 8) & 0xFF) / 255.0F,
                    (color & 0xFF) / 255.0F,
                    ((color >>> 24) & 0xFF) / 255.0F);

            GlStateManager.translate(x + width * 0.5F, y + height * 0.5F, 0.0F);
            if (rotationDeg != 0.0F) {
                GlStateManager.rotate(rotationDeg, 0.0F, 0.0F, 1.0F);
            }

            double u0 = uOffset / textureWidth;
            double v0 = vOffset / textureHeight;
            double u1 = (uOffset + uWidth) / textureWidth;
            double v1 = (vOffset + vHeight) / textureHeight;
            double halfWidth = width * 0.5D;
            double halfHeight = height * 0.5D;
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            buffer.pos(-halfWidth, halfHeight, 0.0D).tex(u0, v1).endVertex();
            buffer.pos(halfWidth, halfHeight, 0.0D).tex(u1, v1).endVertex();
            buffer.pos(halfWidth, -halfHeight, 0.0D).tex(u1, v0).endVertex();
            buffer.pos(-halfWidth, -halfHeight, 0.0D).tex(u0, v0).endVertex();
            Tessellator.getInstance().draw();
        } catch (RuntimeException ignored) {
            // 缺失或损坏的可选纹理不应击穿整个 RTS 界面。
        } finally {
            if (targetBound) {
                GL11.glTexParameteri(
                        GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMinFilter);
                GL11.glTexParameteri(
                        GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMagFilter);
            }
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            if (blendEnabled) {
                GlStateManager.enableBlend();
            } else {
                GlStateManager.disableBlend();
            }
            if (textureEnabled) {
                GlStateManager.enableTexture2D();
            } else {
                GlStateManager.disableTexture2D();
            }
            GlStateManager.color(oldRed, oldGreen, oldBlue, oldAlpha);
            GlStateManager.popMatrix();
        }
    }
}
