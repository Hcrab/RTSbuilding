package com.rtsbuilding.rtsbuilding.client.rendering.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.StorageBatchSelectionSession;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 批量储存框选使用与现有世界选框一致的平滑 AABB、角括号和半透明蓝色体积。 */
public final class StorageBatchSelectionRenderer {
    private static final RtsSelectionBoxAnimator BOX_ANIMATOR = new RtsSelectionBoxAnimator();

    private StorageBatchSelectionRenderer() {
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer brackets, VertexConsumer noDepth, VertexConsumer fill) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            BOX_ANIMATOR.clear();
            return;
        }
        StorageBatchSelectionSession.SelectionBox box =
                screen.getStorageBatchSelection().selectionBox();
        if (box == null) {
            BOX_ANIMATOR.clear();
            return;
        }

        AABB animated = BOX_ANIMATOR.renderAabb(new RtsCullingBox(
                box.visualRevision(), box.min(), box.max()));
        if (animated == null) {
            return;
        }
        AABB visual = animated.inflate(0.012D);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
        double distance = camera.distanceTo(visual.getCenter());
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, brackets,
                visual.minX, visual.minY, visual.minZ,
                visual.maxX, visual.maxY, visual.maxZ,
                0.30F, 0.62F, 1.0F, box.complete() ? 0.95F : 0.72F, distance);
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, noDepth,
                visual.minX, visual.minY, visual.minZ,
                visual.maxX, visual.maxY, visual.maxZ,
                0.30F, 0.62F, 1.0F, box.complete() ? 0.16F : 0.10F, distance);
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                poseStack, fill,
                visual.minX, visual.minY, visual.minZ,
                visual.maxX, visual.maxY, visual.maxZ,
                0.30F, 0.62F, 1.0F, box.complete() ? 0.14F : 0.08F);
    }
}
