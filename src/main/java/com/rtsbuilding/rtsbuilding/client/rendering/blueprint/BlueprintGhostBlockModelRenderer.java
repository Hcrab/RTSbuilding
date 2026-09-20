package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;

/**
 * 将一个蓝图方块模型写入调用方拥有的私有网格。
 * 不取得或结束 Minecraft 共享 buffer；真实世界坐标仍传给模型回调以保留模组取色语义。
 */
public final class BlueprintGhostBlockModelRenderer {
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    static boolean hasModel(BlueprintGhostBlock block) {
        return block != null && !block.missing() && block.state() != null
                && !block.state().isAir() && block.state().getRenderShape() == RenderShape.MODEL;
    }

    static void bake(Minecraft minecraft, BlueprintGhostBlock block, BlockPos anchor,
            PoseStack pose, VertexConsumer vertices) {
        BlockPos local = block.pos();
        pose.pushPose();
        try {
            pose.translate(local.getX(), local.getY(), local.getZ());
            GhostBlockModelRenderer.renderAt(minecraft, pose, ignored -> vertices,
                    block.state(), anchor.offset(local), GHOST_ALPHA, 1.0F, true);
        } finally {
            pose.popPose();
        }
    }
}
