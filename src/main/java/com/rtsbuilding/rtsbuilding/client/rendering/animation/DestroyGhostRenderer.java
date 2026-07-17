package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brief shrink-out overlay shown after the server confirms a remote break.
 * Its model and wireframe layers are controlled independently from placement
 * preview layers so breaking feedback can stay visible without forcing preview
 * noise, or vice versa.
 */
public final class DestroyGhostRenderer {
    private static final long DESTROY_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.56F;

    private static final Map<Long, DestroyGhostEntry> GHOSTS = new LinkedHashMap<>();

    private DestroyGhostRenderer() {
    }

    public static void add(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir()) {
            return;
        }
        GHOSTS.put(pos.asLong(), new DestroyGhostEntry(pos.immutable(), state, System.currentTimeMillis()));
    }

    static void renderModels(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null || GHOSTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = GHOSTS.entrySet().iterator();

        while (iterator.hasNext()) {
            DestroyGhostEntry ghost = iterator.next().getValue();
            long elapsed = now - ghost.addedAtMs;
            if (elapsed > DESTROY_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (!isWithinBounds(ghost.pos)) {
                continue;
            }
            float scale = computeShrinkScale(elapsed);
            if (ghost.state.getRenderShape() == RenderShape.MODEL) {
                GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer,
                        ghost.state, ghost.pos, MODEL_ALPHA, scale);
            } else {
                renderFilledBox(poseStack, fillBuffer, ghost.pos, scale);
            }
        }
        blockBuffer.endBatch();
    }

    static void renderWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        if (GHOSTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = GHOSTS.entrySet().iterator();

        while (iterator.hasNext()) {
            DestroyGhostEntry ghost = iterator.next().getValue();
            long elapsed = now - ghost.addedAtMs;
            if (elapsed > DESTROY_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (!isWithinBounds(ghost.pos)) {
                continue;
            }
            float scale = computeShrinkScale(elapsed);
            renderLineBox(poseStack, lineBuffer, ghost.pos, scale,
                    0.38F, 1.00F, 0.42F, Math.max(0.0F, scale * 0.95F));
        }
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer fillBuffer, BlockPos pos, float scale) {
        double inset = 0.5D - scale * 0.46D;
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                poseStack, fillBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                0.30F, 0.95F, 0.36F, Math.max(0.0F, scale * 0.14F));
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos, float scale,
            float r, float g, float b, float alpha) {
        double inset = 0.5D - scale * 0.46D;
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                poseStack, lineBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                r, g, b, alpha);
    }

    private static float computeShrinkScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) DESTROY_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return Math.max(0.0F, 1.0F - eased);
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return true;
        return RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private record DestroyGhostEntry(BlockPos pos, BlockState state, long addedAtMs) {
    }
}
