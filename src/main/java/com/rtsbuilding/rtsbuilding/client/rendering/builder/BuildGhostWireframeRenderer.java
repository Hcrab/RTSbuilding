package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

import java.util.List;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;

/**
 * Wireframe renderer for single-block ghost previews.
 * <p>
 * Renders block outline wireframes. Build previews deliberately stay blue even
 * at the final confirmation step, because this layer is only a preview of
 * future positions, not a server-confirmed placement animation.
 */
public final class BuildGhostWireframeRenderer {
    private BuildGhostWireframeRenderer() {
    }

    /**
     * Renders wireframes at all target positions.
     *
     * @param blocks       Target block positions
     * @param poseStack    Pose stack for coordinate transforms
     * @param lineBuffer   Line vertex buffer
     * @param readyConfirm Kept for call-site compatibility; preview colour is constant.
     */
    public static void renderWireframes(List<BlockPos> blocks, PoseStack poseStack,
            VertexConsumer lineBuffer, boolean readyConfirm) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        float lineR = UiThemeWorldColors.red(UiThemeWorldColors.BUILD_PREVIEW);
        float lineG = UiThemeWorldColors.green(UiThemeWorldColors.BUILD_PREVIEW);
        float lineB = UiThemeWorldColors.blue(UiThemeWorldColors.BUILD_PREVIEW);

        for (BlockPos pos : blocks) {
            poseStack.pushPose();
            try {
                Minecraft minecraft = Minecraft.getInstance();
                boolean localFrame = minecraft.level != null
                        && RtsSableClientSpatialCompat.applyBlockRenderFrame(minecraft.level, pos, poseStack);
                double baseX = localFrame ? 0.0D : pos.getX();
                double baseY = localFrame ? 0.0D : pos.getY();
                double baseZ = localFrame ? 0.0D : pos.getZ();
                LevelRenderer.renderLineBox(
                        poseStack, lineBuffer,
                        baseX + 0.03D, baseY + 0.03D, baseZ + 0.03D,
                        baseX + 0.97D, baseY + 0.97D, baseZ + 0.97D,
                        lineR, lineG, lineB, 0.70F);
            } finally {
                poseStack.popPose();
            }
        }
    }
}
