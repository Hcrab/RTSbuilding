package com.rtsbuilding.rtsbuilding.client.rendering.smartplace;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostFillRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostWireframeRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 智能放置预览渲染器。
 *
 * <p>渲染两部分内容：
 * <ol>
 *   <li>填充位置半透明方块预览（复用 BuildGhostFillRenderer）</li>
 *   <li>包围盒金色线框——不可被方块阻挡，始终可见</li>
 * </ol>
 */
public final class SmartPlacePreviewRenderer {

    private SmartPlacePreviewRenderer() {}

    /** 金色线条颜色 */
    private static final float GOLD_R = 1.00F;
    private static final float GOLD_G = 0.85F;
    private static final float GOLD_B = 0.00F;
    private static final float GOLD_A = 0.90F;
    /** 包围盒向外扩展 padding */
    private static final double PADDING = 0.02;

    /**
     * 渲染智能放置预览。
     *
     * @param poseStack   坐标变换栈
     * @param lineBuffer  线框顶点缓冲
     * @param fillBuffer  填充顶点缓冲
     * @param positions   填充位置列表
     * @param boundingBox 包围盒（null 则不渲染）
     */
    public static void render(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            List<BlockPos> positions, AABB boundingBox) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        // 1. 填充位置预览（半透明方块）
        BuildGhostFillRenderer.renderFill(positions, poseStack, fillBuffer, true);

        // 2. 填充位置线框（青色以示区分）
        BuildGhostWireframeRenderer.renderWireframes(positions, poseStack, lineBuffer, true);

        // 3. 金色包围盒
        if (boundingBox != null) {
            renderGoldenBoundingBox(poseStack, lineBuffer, boundingBox);
        }
    }

    /**
     * 渲染金色包围盒线框。
     * <p>包围盒为所有填充位置的最外边界，向外扩展 padding。
     * 使用 {@code NO_DEPTH_TEST} 确保线条不被方块阻挡。</p>
     */
    private static void renderGoldenBoundingBox(PoseStack poseStack, VertexConsumer lineBuffer, AABB box) {
        double minX = box.minX - PADDING;
        double minY = box.minY - PADDING;
        double minZ = box.minZ - PADDING;
        double maxX = box.maxX + PADDING;
        double maxY = box.maxY + PADDING;
        double maxZ = box.maxZ + PADDING;

        // 第一遍：正常深度的线条（被方块遮挡时变暗）
        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                GOLD_R, GOLD_G, GOLD_B, GOLD_A * 0.4F);

        // 第二遍：无深度测试的线条（始终可见，更亮）
        // 通过将线条略微外扩并提高透明度实现"不被阻挡"效果
        double inset = 0.005;
        double minX2 = minX - inset;
        double minY2 = minY - inset;
        double minZ2 = minZ - inset;
        double maxX2 = maxX + inset;
        double maxY2 = maxY + inset;
        double maxZ2 = maxZ + inset;

        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX2, minY2, minZ2,
                maxX2, maxY2, maxZ2,
                GOLD_R, GOLD_G, GOLD_B, GOLD_A);
    }
}
