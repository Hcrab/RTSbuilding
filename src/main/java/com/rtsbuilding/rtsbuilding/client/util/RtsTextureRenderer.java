package com.rtsbuilding.rtsbuilding.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

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
        if (guiGraphics == null || texLocation == null || uWidth <= 0.0F || vHeight <= 0.0F) {
            return;
        }
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(x + width / 2.0F, y + height / 2.0F);
        if (rotationDeg != 0.0F) {
            pose.rotate((float) Math.toRadians(rotationDeg));
        }
        float scaleX = width / uWidth;
        float scaleY = height / vHeight;
        pose.scale(scaleX, scaleY);
        pose.translate(-uWidth / 2.0F, -vHeight / 2.0F);

        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texLocation,
                0, 0,
                uOffset, vOffset,
                (int) uWidth, (int) vHeight,
                (int) uWidth, (int) vHeight,
                textureWidth, textureHeight,
                color
        );
        pose.popMatrix();
    }
}
