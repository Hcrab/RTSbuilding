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
 * Renders the Ctrl+right-click movement destination.
 *
 * <p>This owns only the visual feedback for the currently selected move target:
 * a blue, thicker block border that stays while the player is moving and fades
 * quickly after arrival. Movement state and packet sending remain owned by
 * {@link RtsClientPathfinding}.</p>
 */
public final class PlayerMoveTargetRenderer {
    private static final float BLUE_R = 0.16F;
    private static final float BLUE_G = 0.58F;
    private static final float BLUE_B = 1.00F;
    private static final float ACTIVE_ALPHA = 0.95F;
    private static final float NO_DEPTH_ALPHA = 0.28F;
    private static final double INFLATE = 0.045D;
    private static final double THICKNESS_MULTIPLIER = 1.85D;

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
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().position();
        double distance = camPos.distanceTo(Vec3.atCenterOf(target));
        float activeAlpha = ACTIVE_ALPHA * highlight.alpha();
        float noDepthAlpha = NO_DEPTH_ALPHA * highlight.alpha();

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, bracketBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                BLUE_R, BLUE_G, BLUE_B, activeAlpha, distance, THICKNESS_MULTIPLIER);

        if (noDepthBuffer != null) {
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, noDepthBuffer,
                    bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ,
                    BLUE_R, BLUE_G, BLUE_B, noDepthAlpha, distance, THICKNESS_MULTIPLIER);
        }
    }
}
