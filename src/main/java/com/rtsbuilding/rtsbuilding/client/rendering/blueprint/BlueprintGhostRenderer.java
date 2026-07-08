package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 蓝图虚影渲染入口。
 *
 * <p>这个类只负责串联渲染步骤，实际的边界裁剪、方块模型、兜底线框和整体包围盒
 * 交给独立子类，避免后续修蓝图预览时继续堆大类。</p>
 */
public final class BlueprintGhostRenderer {
    private static final float TRUNCATED_BOX_ALPHA = 0.22F;

    private BlueprintGhostRenderer() {
    }

    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }

        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        List<BlueprintPanel.BlueprintGhostBlock> filteredBlocks = BlueprintGhostBoundsFilter.filter(preview.blocks());
        if (filteredBlocks.isEmpty()) {
            return;
        }

        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        int[] minX = {Integer.MAX_VALUE};
        int[] minY = {Integer.MAX_VALUE};
        int[] minZ = {Integer.MAX_VALUE};
        int[] maxX = {Integer.MIN_VALUE};
        int[] maxY = {Integer.MIN_VALUE};
        int[] maxZ = {Integer.MIN_VALUE};

        BlueprintGhostBlockModelRenderer.renderModels(
                minecraft, filteredBlocks, poseStack,
                minX, minY, minZ,
                maxX, maxY, maxZ);

        BlueprintGhostFallbackRenderer.renderFallbacks(filteredBlocks, poseStack, lineBuffer, lineR, lineG, lineB);

        float envelopeAlpha = preview.truncated() ? TRUNCATED_BOX_ALPHA : BlueprintGhostBlockModelRenderer.GHOST_ALPHA;
        BlueprintGhostEnvelopeRenderer.render(
                poseStack, lineBuffer,
                minX[0], minY[0], minZ[0],
                maxX[0], maxY[0], maxZ[0],
                lineR, lineG, lineB,
                envelopeAlpha);
    }
}
