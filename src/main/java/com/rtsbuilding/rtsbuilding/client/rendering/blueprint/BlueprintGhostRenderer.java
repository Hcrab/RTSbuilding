package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Blueprint ghost preview renderer (facade class).
 * <p>
 * Orchestrates the complete rendering pipeline for blueprint ghost previews,
 * delegating sub-tasks to dedicated sub-renderers:
 * <ul>
 *   <li>{@link BlueprintGhostBoundsFilter} — bounds clipping</li>
 *   <li>{@link BlueprintGhostBlockModelRenderer} — translucent block model rendering</li>
 *   <li>{@link BlueprintGhostFallbackRenderer} — fallback wireframes for missing/non-model blocks</li>
 *   <li>{@link BlueprintGhostEnvelopeRenderer} — overall bounding box outline</li>
 * </ul>
 * <p>
 * Public API is kept for backward compatibility.
 */
public final class BlueprintGhostRenderer {

    private static final float TRUNCATED_BOX_ALPHA = 0.22F;

    private BlueprintGhostRenderer() {
    }

    /**
     * Renders the blueprint ghost preview.
     *
     * @param minecraft  Minecraft client instance
     * @param poseStack  Pose stack for coordinate transforms
     * @param lineBuffer Line vertex buffer
     * @param fillBuffer Fill vertex buffer (reserved, currently unused)
     */
    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // Only render in BuilderScreen
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }

        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        // 1. Filter out blocks outside RTS bounds
        List<BlueprintPanel.BlueprintGhostBlock> filteredBlocks = BlueprintGhostBoundsFilter.filter(preview.blocks());
        if (filteredBlocks.isEmpty()) {
            return;
        }

        // 2. Choose colour based on material availability (ready: green, missing: red)
        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        // 3. Initialise bounding box bounds
        int[] minX = {Integer.MAX_VALUE};
        int[] minY = {Integer.MAX_VALUE};
        int[] minZ = {Integer.MAX_VALUE};
        int[] maxX = {Integer.MIN_VALUE};
        int[] maxY = {Integer.MIN_VALUE};
        int[] maxZ = {Integer.MIN_VALUE};

        // 4. Render translucent block models (collecting bounding box bounds simultaneously)
        BlueprintGhostBlockModelRenderer.renderModels(
                minecraft, filteredBlocks, poseStack,
                minX, minY, minZ,
                maxX, maxY, maxZ);

        // 5. Render fallback wireframes for missing/non-model blocks
        BlueprintGhostFallbackRenderer.renderFallbacks(filteredBlocks, poseStack, lineBuffer, lineR, lineG, lineB);

        // 6. Render overall bounding box outline
        float envelopeAlpha = preview.truncated() ? TRUNCATED_BOX_ALPHA : BlueprintGhostBlockModelRenderer.GHOST_ALPHA;
        BlueprintGhostEnvelopeRenderer.render(
                poseStack, lineBuffer,
                minX[0], minY[0], minZ[0],
                maxX[0], maxY[0], maxZ[0],
                lineR, lineG, lineB,
                envelopeAlpha);
    }
}
