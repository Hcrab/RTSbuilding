package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 单方块放置虚影的线框渲染器。
 * <p>
 * 负责渲染方块轮廓线框，颜色根据确认状态变化：
 * <ul>
 *   <li>未确认（预览）：青色线框</li>
 *   <li>已就绪（确认）：绿色线框</li>
 * </ul>
 */
public final class BuildGhostWireframeRenderer {

    private BuildGhostWireframeRenderer() {
    }

    /**
     * 渲染所有目标位置的方块线框。
     *
     * @param blocks       目标方块位置列表
     * @param poseStack    姿势栈
     * @param lineBuffer   线条缓冲区
     * @param readyConfirm 是否已就绪等待确认
     */
    public static void renderWireframes(List<BlockPos> blocks, PoseStack poseStack,
            VertexConsumer lineBuffer, boolean readyConfirm) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        float lineR = readyConfirm ? 0.45F : 0.30F;
        float lineG = readyConfirm ? 0.95F : 0.75F;
        float lineB = readyConfirm ? 0.45F : 1.00F;

        for (BlockPos pos : blocks) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    lineR, lineG, lineB, 0.95F);
        }
    }
}
