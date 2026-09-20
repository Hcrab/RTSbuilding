package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;

/**
 * 只将一个蓝图模型写入调用方拥有的局部网格，不持有或结束 Minecraft 的共享缓冲。
 * 逻辑坐标仍为真实世界位置，保留 TFC 等模组的世界取色回调；几何坐标为蓝图局部坐标。
 */
public final class BlueprintGhostBlockModelRenderer {
    public static final float GHOST_ALPHA = 0.30F;

    private BlueprintGhostBlockModelRenderer() {
    }

    static boolean hasModel(BlueprintGhostBlock block) {
        return !block.missing() && block.state() != null && !block.state().isAir()
                && block.state().getRenderShape() == RenderShape.MODEL;
    }

    static void bake(Minecraft minecraft, BlueprintGhostBlock block, BlockPos anchor,
            PoseStack pose, VertexConsumer vertices) {
        BlockPos local = block.pos();
        pose.pushPose();
        try {
            pose.translate(local.getX(), local.getY(), local.getZ());
            // 不对真实地形启用邻面剔除，否则地形会错误遮掉尚未放置的蓝图面。
            GhostBlockModelRenderer.renderAtLocal(minecraft, pose, ignored -> vertices,
                    block.state(), anchor.offset(local), GHOST_ALPHA, 1.0F);
        } finally {
            pose.popPose();
        }
    }
}
