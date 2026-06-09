package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 蓝图虚影预览渲染器（门面类）。
 * <p>
 * 负责编排蓝图虚影预览的完整渲染管道，将各子任务委托给专用的子渲染器：
 * <ul>
 *   <li>{@link BlueprintGhostBoundsFilter} — 边界裁剪</li>
 *   <li>{@link BlueprintGhostBlockModelRenderer} — 半透明方块模型渲染</li>
 *   <li>{@link BlueprintGhostFallbackRenderer} — 缺失/无模型方块的回退框线</li>
 *   <li>{@link BlueprintGhostEnvelopeRenderer} — 整体包围盒框线</li>
 * </ul>
 * <p>
 * 公开 API 保持向后兼容。
 */
public final class BlueprintGhostRenderer {

    private static final float TRUNCATED_BOX_ALPHA = 0.22F;

    private BlueprintGhostRenderer() {
    }

    /**
     * 渲染蓝图的虚影预览。
     *
     * @param minecraft  Minecraft 客户端实例
     * @param poseStack  姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     * @param fillBuffer 填充缓冲区（预留，当前未使用）
     */
    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // 仅在 BuilderScreen 中渲染
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }

        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        // 1. 过滤超出 RTS 边界的蓝图方块
        List<BlueprintPanel.BlueprintGhostBlock> filteredBlocks = BlueprintGhostBoundsFilter.filter(preview.blocks());
        if (filteredBlocks.isEmpty()) {
            return;
        }

        // 2. 根据材料是否齐备选择颜色（材料齐备：绿色系；材料缺失：红色系）
        float lineR = preview.materialsReady() ? 0.35F : 1.00F;
        float lineG = preview.materialsReady() ? 0.95F : 0.72F;
        float lineB = preview.materialsReady() ? 0.72F : 0.22F;

        // 3. 初始化包围盒边界
        int[] minX = {Integer.MAX_VALUE};
        int[] minY = {Integer.MAX_VALUE};
        int[] minZ = {Integer.MAX_VALUE};
        int[] maxX = {Integer.MIN_VALUE};
        int[] maxY = {Integer.MIN_VALUE};
        int[] maxZ = {Integer.MIN_VALUE};

        // 4. 渲染半透明方块模型（同时收集包围盒边界）
        BlueprintGhostBlockModelRenderer.renderModels(
                minecraft, filteredBlocks, poseStack,
                minX, minY, minZ,
                maxX, maxY, maxZ);

        // 5. 渲染缺失/无模型方块的回退框线
        BlueprintGhostFallbackRenderer.renderFallbacks(filteredBlocks, poseStack, lineBuffer, lineR, lineG, lineB);

        // 6. 渲染整体包围盒框线
        float envelopeAlpha = preview.truncated() ? TRUNCATED_BOX_ALPHA : BlueprintGhostBlockModelRenderer.GHOST_ALPHA;
        BlueprintGhostEnvelopeRenderer.render(
                poseStack, lineBuffer,
                minX[0], minY[0], minZ[0],
                maxX[0], maxY[0], maxZ[0],
                lineR, lineG, lineB,
                envelopeAlpha);
    }
}
