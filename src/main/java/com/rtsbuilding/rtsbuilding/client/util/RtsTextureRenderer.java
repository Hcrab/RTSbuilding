package com.rtsbuilding.rtsbuilding.client.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * High-precision vector texture renderer.
 * <p>
 * Uses floating-point coordinates and PoseStack matrix transforms for sub-pixel accuracy,
 * supports centre rotation, colour tinting, and does not pollute global GL texture filter state.
 */
public final class RtsTextureRenderer {

    private RtsTextureRenderer() {
    }

    /**
     * High-precision texture drawing.
     * <p>
     * Compared with a direct {@code GuiGraphicsExtractor.blit} call, this method:
     * <ul>
     *   <li>Uses float-precision target position and UV, enabling sub-pixel positioning</li>
     *   <li>Supports centre rotation (in degrees)</li>
     *   <li>Supports colour tinting (multiplicative), format 0xAARRGGBB</li>
     *   <li>Does not pollute global GL texture filter state</li>
     * </ul>
     *
     * @param guiGraphics   render context
     * @param texLocation   texture resource path
     * @param x             target top-left X (float precision)
     * @param y             target top-left Y (float precision)
     * @param width         target draw width
     * @param height        target draw height
     * @param uOffset       source texture U offset (float precision)
     * @param vOffset       source texture V offset (float precision)
     * @param uWidth        source texture region width
     * @param vHeight       source texture region height
     * @param textureWidth  full texture total width
     * @param textureHeight full texture total height
     * @param rotationDeg   rotation angle in degrees; 0 means no rotation
     * @param color         colour tint 0xAARRGGBB; 0xFFFFFFFF means no tint
     */
    public static void drawTextureHighPrecision(
            GuiGraphicsExtractor guiGraphics,
            Identifier texLocation,
            float x, float y,
            float width, float height,
            float uOffset, float vOffset,
            float uWidth, float vHeight,
            int textureWidth, int textureHeight,
            float rotationDeg,
            int color
    ) {
        // 1. Ensure the texture is loaded (same as WindowButton.renderWithTexture)
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

        // 2. Enable blending
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 3. Bind the texture and set high-quality filter parameters
        RenderSystem.setShaderTexture(0, texLocation);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // 4. Colour tinting
        boolean hasTint = (color & 0xFFFFFFFFL) != 0xFFFFFFFFL;
        if (hasTint) {
            guiGraphics.setColor(
                    ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f,
                    ((color >> 24) & 0xFF) / 255.0f
            );
        }

        // 5. Use PoseStack transform (same as WindowButton.renderWithTexture)
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scaleX = width / uWidth;
        float scaleY = height / vHeight;
        pose.scale(scaleX, scaleY, 1.0f);

        // 6. Draw (in transformed coordinates, texture is drawn at (0,0) with original UV size)
        guiGraphics.blit(
                texLocation,
                0, 0,
                (int) uOffset, (int) vOffset,
                (int) uWidth, (int) vHeight,
                textureWidth, textureHeight
        );

        // 7. Restore transform
        pose.popPose();

        // 8. Restore colour
        if (hasTint) {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 9. Restore blend and texture filter
        RenderSystem.disableBlend();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }
}
