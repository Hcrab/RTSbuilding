package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 渲染 Ctrl+右键移动玩家的目标提示。
 *
 * <p>这里只负责视觉反馈：蓝色目标框在移动中保持显示，抵达后短暂淡出。
 * 移动状态仍由 {@link RtsClientPathfinding} 维护，避免把输入、移动和渲染职责混在一起。</p>
 */
public final class PlayerMoveTargetRenderer {
    private static final float BLUE_R = 0.16F;
    private static final float BLUE_G = 0.58F;
    private static final float BLUE_B = 1.00F;
    private static final float ACTIVE_ALPHA = 0.95F;
    private static final float NO_DEPTH_ALPHA = 0.28F;
    private static final double INFLATE = 0.045D;

    private PlayerMoveTargetRenderer() {
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer bracketBuffer, VertexConsumer noDepthBuffer) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        RtsClientPathfinding.MoveTargetHighlight highlight = RtsClientPathfinding.getMoveTargetHighlight();
        if (highlight == null || highlight.alpha() <= 0.0F) {
            return;
        }

        BlockPos target = highlight.target();
        AABB bounds = new AABB(target).inflate(INFLATE);
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = camPos.distanceTo(Vec3.atCenterOf(target));
        float activeAlpha = ACTIVE_ALPHA * highlight.alpha();
        float noDepthAlpha = NO_DEPTH_ALPHA * highlight.alpha();

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, bracketBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                BLUE_R, BLUE_G, BLUE_B, activeAlpha, distance);

        if (noDepthBuffer != null) {
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, noDepthBuffer,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    BLUE_R, BLUE_G, BLUE_B, noDepthAlpha, distance);
        }
    }
}
