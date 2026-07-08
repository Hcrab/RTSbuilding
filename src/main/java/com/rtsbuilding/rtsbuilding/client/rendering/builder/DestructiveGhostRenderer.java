package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

/**
 * 范围破坏预览渲染入口。
 *
 * <p>该类先承接 ddf7251 的职责边界：普通 destructive ghost 由这里调度，
 * 已确认工作区的合并骨架交给 {@link MergedSkeletonRenderer}。</p>
 */
public final class DestructiveGhostRenderer {
    private DestructiveGhostRenderer() {
    }

    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        ShapeGhostRenderer.renderDestructiveGhost(
                preview, poseStack, lineBuffer, fillBuffer, progress, alphaMultiplier);
    }
}
