package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 建造 preview 线框渲染器。
 *
 * <p>线框是范围选择阶段的基础反馈，不受“方块虚影预览”设置影响。
 * 为了和玩家预期保持一致，这里始终使用蓝色线框。</p>
 */
public final class BuildGhostWireframeRenderer {
    private BuildGhostWireframeRenderer() {
    }

    public static void renderWireframes(List<BlockPos> blocks, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (BlockPos pos : blocks) {
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    pos.getX() + 0.03D, pos.getY() + 0.03D, pos.getZ() + 0.03D,
                    pos.getX() + 0.97D, pos.getY() + 0.97D, pos.getZ() + 0.97D,
                    0.30F, 0.75F, 1.00F, 0.95F);
        }
    }
}
