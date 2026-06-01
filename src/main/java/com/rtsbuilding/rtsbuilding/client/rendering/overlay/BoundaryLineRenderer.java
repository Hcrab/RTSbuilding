package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;

/**
 * RTS建造范围边界线渲染器
 * 负责绘制红色的正方形边界框，标识玩家可操作的最大范围
 */
public final class BoundaryLineRenderer {

    /**
     * 私有构造函数，防止实例化
     */
    private BoundaryLineRenderer() {
    }

    /**
     * 绘制红色边界线（在锚点Y高度的正方形边框）
     * 
     * @param minecraft Minecraft客户端实例
     * @param poseStack 姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     * @param minX X轴最小值（锚点X - 半径）
     * @param minZ Z轴最小值（锚点Z - 半径）
     * @param maxX X轴最大值（锚点X + 半径）
     * @param maxZ Z轴最大值（锚点Z + 半径）
     * @param defaultY Y轴高度（使用锚点Y坐标）
     */
    public static void renderRedBoundary(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, double minX, double minZ, double maxX, double maxZ, double defaultY) {
        if (minecraft.level == null) {
            return;
        }

        float y = (float) defaultY;

        // 绘制正方形的四条边
        // 边1: 前边 (minX, minZ) -> (maxX, minZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, y, minZ, maxX, y, minZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 边2: 右边 (maxX, minZ) -> (maxX, maxZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, maxX, y, minZ, maxX, y, maxZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 边3: 后边 (maxX, maxZ) -> (minX, maxZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, maxX, y, maxZ, minX, y, maxZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        // 边4: 左边 (minX, maxZ) -> (minX, minZ)
        LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, y, maxZ, minX, y, minZ,
                1.0F, 0.25F, 0.25F, 1.0F);
    }
}
