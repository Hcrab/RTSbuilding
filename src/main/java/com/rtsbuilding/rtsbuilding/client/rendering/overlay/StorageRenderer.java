package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 储存方块高亮渲染器
 * 负责渲染已链接的储存容器方块的蓝色边框，帮助玩家识别RTS系统的储存网络
 */
public final class StorageRenderer {

    /**
     * 私有构造函数，防止实例化
     */
    private StorageRenderer() {
    }

    /**
     * 渲染所有已链接的储存方块高亮
     *
     * @param minecraft Minecraft客户端实例
     * @param controller RTS控制器，提供储存位置列表
     * @param poseStack 姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     */
    public static void renderLinkedStorages(Minecraft minecraft, ClientRtsController controller, PoseStack poseStack,
                                            VertexConsumer lineBuffer) {
        if (minecraft.level == null || controller.getLinkedStoragePositions().isEmpty()) {
            return;
        }

        // 遍历所有已链接的储存位置
        for (BlockPos pos : controller.getLinkedStoragePositions()) {
            // 检查区块是否已加载
            if (!minecraft.level.hasChunkAt(pos)) {
                continue;
            }

            // 检查方块是否存在（非空气）
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            // 绘制蓝色边框，向外扩展0.002单位以避免Z-fighting
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() - 0.002D,
                    pos.getY() - 0.002D,
                    pos.getZ() - 0.002D,
                    pos.getX() + 1.002D,
                    pos.getY() + 1.002D,
                    pos.getZ() + 1.002D,
                    0.24F, 0.55F, 1.00F, 1.0F);  // 天蓝色
        }
    }
}
