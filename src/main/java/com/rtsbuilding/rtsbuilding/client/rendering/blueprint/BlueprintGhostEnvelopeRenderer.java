package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * 蓝图虚影包围盒渲染器。
 * <p>
 * 负责在蓝图预览的整个范围外侧渲染一层半透明包围盒框线，
 * 帮助玩家直观地了解蓝图占用的总空间。
 */
public final class BlueprintGhostEnvelopeRenderer {

    /** 包围盒框线与最外侧方块的间距 */
    private static final double ENVELOPE_PADDING = 0.02D;

    private BlueprintGhostEnvelopeRenderer() {
    }

    /**
     * 渲染蓝图虚影的整体包围盒框线。
     *
     * @param poseStack  姿势栈
     * @param lineBuffer 线条缓冲区
     * @param minX       包围盒最小 X
     * @param minY       包围盒最小 Y
     * @param minZ       包围盒最小 Z
     * @param maxX       包围盒最大 X
     * @param maxY       包围盒最大 Y
     * @param maxZ       包围盒最大 Z
     * @param r          红色分量
     * @param g          绿色分量
     * @param b          蓝色分量
     * @param alpha      透明度
     */
    public static void render(
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            int minX, int minY, int minZ,
            int maxX, int maxY, int maxZ,
            float r, float g, float b,
            float alpha) {

        if (minX == Integer.MAX_VALUE) {
            return; // 无有效方块时跳过
        }

        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX - ENVELOPE_PADDING, minY - ENVELOPE_PADDING, minZ - ENVELOPE_PADDING,
                maxX + ENVELOPE_PADDING, maxY + ENVELOPE_PADDING, maxZ + ENVELOPE_PADDING,
                r, g, b, alpha);
    }
}
