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

/** Brief grow-in overlay after the server confirms remote placement. */
final class ConfirmedPlacementRenderer {
    private static final long PLACE_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.58F;
    private static final Map<Long, PlacementEntry> PLACEMENTS = new LinkedHashMap<>();

    private ConfirmedPlacementRenderer() {
    }

    static void add(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir()) {
            return;
        }
        PLACEMENTS.put(pos.asLong(), new PlacementEntry(pos.immutable(), state, System.currentTimeMillis()));
    }

    static void renderModels(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null || PLACEMENTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, MODEL_ALPHA);
        Iterator<Map.Entry<Long, PlacementEntry>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PlacementEntry entry = iterator.next().getValue();
            long elapsed = now - entry.addedAtMs;
            if (elapsed > PLACE_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (!isWithinBounds(entry.pos)) {
                continue;
            }
            float scale = computeGrowScale(elapsed);
            if (entry.state.getRenderShape() == RenderShape.MODEL) {
                poseStack.pushPose();
                poseStack.translate(entry.pos.getX(), entry.pos.getY(), entry.pos.getZ());
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.scale(scale, scale, scale);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                int light = LevelRenderer.getLightColor(minecraft.level, entry.pos);
                minecraft.getBlockRenderer().renderSingleBlock(entry.state, poseStack, translucentBuffer,
                        light, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            } else {
                renderFilledBox(poseStack, fillBuffer, entry.pos, scale);
            }
        }
        blockBuffer.endBatch();
    }

    static void renderWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        if (PLACEMENTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, PlacementEntry>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PlacementEntry entry = iterator.next().getValue();
            long elapsed = now - entry.addedAtMs;
            if (elapsed > PLACE_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (isWithinBounds(entry.pos)) {
                renderLineBox(poseStack, lineBuffer, entry.pos, computeGrowScale(elapsed),
                        0.30F, 0.85F, 1.00F, 0.82F);
            }
        }
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer fillBuffer, BlockPos pos, float scale) {
        double inset = 0.5D - scale * 0.46D;
        LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                0.40F, 0.85F, 0.90F, 0.16F);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos, float scale,
            float r, float g, float b, float alpha) {
        double inset = 0.5D - scale * 0.46D;
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                r, g, b, alpha);
    }

    private static float computeGrowScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) PLACE_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return 0.12F + eased * 0.86F;
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        return !controller.hasBounds()
                || RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private record PlacementEntry(BlockPos pos, BlockState state, long addedAtMs) {
    }
}
