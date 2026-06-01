package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.blueprint.client.BlueprintPanel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 蓝图捕获框渲染器
 * 负责渲染蓝图录制时的选择框、包含方块高亮和排除方块标记
 */
public final class BlueprintCaptureRenderer {
    // 包含方块高亮的最大数量限制，避免性能问题
    private static final int CAPTURE_BLOCK_HIGHLIGHT_LIMIT = 8192;
    // 排除方块高亮的最大数量限制
    private static final int CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT = 1024;
    
    // 优化：提取颜色常量，便于统一调整
    private static final float CAPTURE_FILL_R = 0.12F;
    private static final float CAPTURE_FILL_G = 0.46F;
    private static final float CAPTURE_FILL_B = 0.95F;
    private static final float CAPTURE_FILL_A = 0.06F;
    
    private static final float INCLUDED_BLOCK_R = 0.12F;
    private static final float INCLUDED_BLOCK_G = 0.56F;
    private static final float INCLUDED_BLOCK_B = 1.0F;
    private static final float INCLUDED_BLOCK_A = 0.11F;
    
    private static final float EXCLUDED_BLOCK_R = 1.0F;
    private static final float EXCLUDED_BLOCK_G = 0.36F;
    private static final float EXCLUDED_BLOCK_B = 0.12F;
    private static final float EXCLUDED_BLOCK_A = 0.95F;
    
    private static final float BOUNDARY_BOX_R = 0.35F;
    private static final float BOUNDARY_BOX_G = 0.78F;
    private static final float BOUNDARY_BOX_B = 1.0F;
    private static final float BOUNDARY_BOX_A = 0.95F;

    /**
     * 私有构造函数，防止实例化
     */
    private BlueprintCaptureRenderer() {
    }

    /**
     * 渲染蓝图捕获选择框和高亮
     * 
     * @param poseStack 姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     * @param fillBuffer 填充缓冲区
     */
    public static void renderBlueprintCaptureBox(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        // 获取第一个角点（起始点）
        BlockPos first = BlueprintPanel.getCapturePointA();
        if (first == null) {
            return;
        }

        // 获取第二个角点（预览点），如果未设置则使用第一个点
        BlockPos second = BlueprintPanel.getCapturePreviewPointB();
        if (second == null) {
            second = first;
        }

        // 计算包围盒边界（向外扩展0.01单位以避免Z-fighting）
        double minX = Math.min(first.getX(), second.getX()) - 0.01D;
        double minY = Math.min(first.getY(), second.getY()) + 0.99D;
        double minZ = Math.min(first.getZ(), second.getZ()) - 0.01D;
        double maxX = Math.max(first.getX(), second.getX()) + 1.01D;
        double maxY = Math.max(first.getY(), second.getY()) + 1.01D;
        double maxZ = Math.max(first.getZ(), second.getZ()) + 1.01D;

        // 确保Y轴范围有效
        if (minY > maxY) {
            minY = maxY - 0.02D;
        }

        // 获取包含的方块列表（受数量限制）
        List<BlockPos> includedBlocks = BlueprintPanel.getCaptureIncludedBlocksForRender(CAPTURE_BLOCK_HIGHLIGHT_LIMIT);

        // 如果需要渲染整体填充且不渲染单个方块高亮，则绘制半透明蓝色填充
        if (BlueprintPanel.shouldRenderCapturePreviewFill()
                && !BlueprintPanel.shouldRenderCaptureBlockHighlights(CAPTURE_BLOCK_HIGHLIGHT_LIMIT)) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    CAPTURE_FILL_R, CAPTURE_FILL_G, CAPTURE_FILL_B, CAPTURE_FILL_A);
        }

        // 渲染每个包含方块的蓝色高亮
        for (BlockPos pos : includedBlocks) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    pos.getX() + 0.04D, pos.getY() + 0.04D, pos.getZ() + 0.04D,
                    pos.getX() + 0.96D, pos.getY() + 0.96D, pos.getZ() + 0.96D,
                    INCLUDED_BLOCK_R, INCLUDED_BLOCK_G, INCLUDED_BLOCK_B, INCLUDED_BLOCK_A);
        }

        // 渲染每个排除方块的红色边框
        for (BlockPos pos : BlueprintPanel.getCaptureExcludedBlocksForRender(CAPTURE_EXCLUDED_HIGHLIGHT_LIMIT)) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() + 0.06D, pos.getY() + 0.06D, pos.getZ() + 0.06D,
                    pos.getX() + 0.94D, pos.getY() + 0.94D, pos.getZ() + 0.94D,
                    EXCLUDED_BLOCK_R, EXCLUDED_BLOCK_G, EXCLUDED_BLOCK_B, EXCLUDED_BLOCK_A);
        }

        // 渲染整个选择框的蓝色边框
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                BOUNDARY_BOX_R, BOUNDARY_BOX_G, BOUNDARY_BOX_B, BOUNDARY_BOX_A);
    }
}
