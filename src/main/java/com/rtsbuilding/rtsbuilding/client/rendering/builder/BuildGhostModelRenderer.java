package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 旧世界渲染阶段保留的兼容入口。
 *
 * <p>26.1 的真实方块模型虚影由 RtsWorldPreviewExtractor 和平台
 * SubmitCustomGeometry bridge 提交。本入口刻意不访问共享 buffer，也不
 * endBatch；旧版本反哺时仍可在各自 adapter 中实现相同语义。</p>
 */
public final class BuildGhostModelRenderer {
    public static final float GHOST_ALPHA = 0.8F;

    private BuildGhostModelRenderer() {
    }

    public static void renderModels(
            Minecraft minecraft,
            List<BlockPos> blocks,
            PoseStack poseStack,
            BlockState blockState) {
        // 模型已经在 26.1 的提取/提交桥处理；线框仍由旧阶段按原行为绘制。
    }
}
