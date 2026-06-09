package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Brief shrink-out overlay after the server confirms remote block break. */
final class DestroyGhostRenderer {
    private static final long DESTROY_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.56F;
    private static final Map<Long, DestroyGhostEntry> GHOSTS = new LinkedHashMap<>();

    private DestroyGhostRenderer() {
    }

    static void add(BlockPos pos, BlockState state) {
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
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, MODEL_ALPHA);
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
                poseStack.pushPose();
                poseStack.translate(ghost.pos.getX(), ghost.pos.getY(), ghost.pos.getZ());
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.scale(scale, scale, scale);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                int light = LevelRenderer.getLightColor(minecraft.level, ghost.pos);
                minecraft.getBlockRenderer().renderSingleBlock(ghost.state, poseStack, translucentBuffer,
                        light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
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
            if (isWithinBounds(ghost.pos)) {
                renderLineBox(poseStack, lineBuffer, ghost.pos, computeShrinkScale(elapsed),
                        0.38F, 1.00F, 0.42F, 0.95F);
            }
        }
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer fillBuffer, BlockPos pos, float scale) {
        double inset = 0.5D - scale * 0.46D;
        LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                0.30F, 0.95F, 0.36F, Math.max(0.0F, scale * 0.14F));
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos, float scale,
            float r, float g, float b, float alpha) {
        double inset = 0.5D - scale * 0.46D;
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                r, g, b, Math.max(0.0F, scale * alpha));
    }

    private static float computeShrinkScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) DESTROY_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return Math.max(0.0F, 1.0F - eased);
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        return !controller.hasBounds()
                || RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private record DestroyGhostEntry(BlockPos pos, BlockState state, long addedAtMs) {
    }
}
