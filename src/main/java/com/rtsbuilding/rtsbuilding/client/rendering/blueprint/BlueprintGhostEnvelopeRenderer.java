package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * 蓝图整体包围盒线框。
 */
public final class BlueprintGhostEnvelopeRenderer {
    private static final double ENVELOPE_PADDING = 0.02D;

    private BlueprintGhostEnvelopeRenderer() {
    }

    public static void render(PoseStack poseStack, VertexConsumer lineBuffer,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            float r, float g, float b,
            float alpha) {
        if (minX == Integer.MAX_VALUE) {
            return;
        }

        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX - ENVELOPE_PADDING, minY - ENVELOPE_PADDING, minZ - ENVELOPE_PADDING,
                maxX + ENVELOPE_PADDING, maxY + ENVELOPE_PADDING, maxZ + ENVELOPE_PADDING,
                r, g, b, alpha);
    }
}
