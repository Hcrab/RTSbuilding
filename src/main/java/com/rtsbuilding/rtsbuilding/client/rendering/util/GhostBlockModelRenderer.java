package com.rtsbuilding.rtsbuilding.client.rendering.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 共享的 RTS 幽灵方块模型渲染入口。
 * <p>
 * 不使用 {@code renderSingleBlock}，因为它按“独立方块/物品预览”渲染，
 * 第三方方块颜色回调可能拿不到真实世界坐标。这里直接调用模型烘焙器，
 * 明确传入当前客户端世界和目标 {@link BlockPos}，让 TFC 这类按位置计算
 * 树叶颜色的模组能拿到非空坐标。
 */
public final class GhostBlockModelRenderer {
    private GhostBlockModelRenderer() {
    }

    public static boolean renderAt(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource,
            BlockState state, BlockPos pos, float alpha) {
        return renderAt(minecraft, poseStack, bufferSource, state, pos, alpha, 1.0F);
    }

    public static boolean renderAt(Minecraft minecraft, PoseStack poseStack, MultiBufferSource bufferSource,
            BlockState state, BlockPos pos, float alpha, float scale) {
        // 26.1 禁止在旧阶段直接向共享 buffer 写方块模型。保留这个窄边界，
        // 让业务侧虚影状态继续编译；真正几何由后续 SubmitCustomGeometry 桥接入。
        return false;
    }
}
