package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * Blueprint ghost envelope renderer.
 * <p>
 * Renders a translucent bounding box outline around the entire blueprint
 * preview area, helping the player visualise the total space occupied.
 */
public final class BlueprintGhostEnvelopeRenderer {

    /** Padding between envelope lines and the outermost blocks */
    private static final double ENVELOPE_PADDING = 0.02D;

    private BlueprintGhostEnvelopeRenderer() {
    }

    /**
     * Renders the overall bounding box outline for blueprint ghosts.
     *
     * @param poseStack  Pose stack
     * @param lineBuffer Line vertex buffer
     * @param minX       Bounding box min X
     * @param minY       Bounding box min Y
     * @param minZ       Bounding box min Z
     * @param maxX       Bounding box max X
     * @param maxY       Bounding box max Y
     * @param maxZ       Bounding box max Z
     * @param r          Red component
     * @param g          Green component
     * @param b          Blue component
     * @param alpha      Opacity
     */
    public static void render(
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            float r, float g, float b,
            float alpha) {

        if (minX == Integer.MAX_VALUE) {
            return; // Skip when there are no valid blocks
        }

        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX - ENVELOPE_PADDING, minY - ENVELOPE_PADDING, minZ - ENVELOPE_PADDING,
                maxX + ENVELOPE_PADDING, maxY + ENVELOPE_PADDING, maxZ + ENVELOPE_PADDING,
                r, g, b, alpha);
    }
}
