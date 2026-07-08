package com.rtsbuilding.rtsbuilding.client.rendering.animation;


import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows translucent block ghosts for client-submitted placements while waiting
 * for server confirmation.
 */
final class PendingGhostRenderer {
    private static final float GHOST_ALPHA = 0.60F;
    private static final long GROW_DURATION_MS = 220L;
    private static final long MAX_PENDING_MS = 5000L;
    private static final float BASE_SCALE = 0.8F;
    private static final float PULSE_AMPLITUDE = 0.025F;
    private static final float PULSE_FREQUENCY = 0.008F;
    private static final Map<Long, PendingGhostEntry> GHOSTS = new LinkedHashMap<>();

    private PendingGhostRenderer() {
    }

    static void addPendingBatch(List<BlockPos> positions, BlockState blockState) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            if (pos != null) {
                GHOSTS.put(pos.asLong(), new PendingGhostEntry(pos.immutable(), blockState, now));
            }
        }
    }

    static void remove(BlockPos pos) {
        if (pos != null) {
            GHOSTS.remove(pos.asLong());
        }
    }

    static void render(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderPendingGhosts(minecraft, poseStack, fillBuffer);
    }

    static void renderWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) {
                continue;
            }
            float scale = computeGrowScale(now - ghost.addedAtMs);
            double inset = 0.5D - scale * 0.44D;
            LevelRenderer.renderLineBox(poseStack, lineBuffer,
                    ghost.pos.getX() + inset, ghost.pos.getY() + inset, ghost.pos.getZ() + inset,
                    ghost.pos.getX() + 1.0D - inset, ghost.pos.getY() + 1.0D - inset, ghost.pos.getZ() + 1.0D - inset,
                    0.30F, 0.75F, 1.00F, 0.75F);
        }
    }

    private static void renderPendingGhosts(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        if (GHOSTS.isEmpty()) {
            return;
        }
        pruneExpired(System.currentTimeMillis());
        if (GHOSTS.isEmpty()) {
            return;
        }
        Map<BlockState, java.util.ArrayList<BlockPos>> modelGroups = new HashMap<>();
        java.util.ArrayList<BlockPos> fallbackPositions = new java.util.ArrayList<>();
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) {
                continue;
            }
            BlockState state = ghost.blockState;
            if (state != null && !state.isAir() && state.getRenderShape() == RenderShape.MODEL) {
                modelGroups.computeIfAbsent(state, k -> new java.util.ArrayList<>()).add(ghost.pos);
            } else {
                fallbackPositions.add(ghost.pos);
            }
        }
        if (!modelGroups.isEmpty()) {
            long now = System.currentTimeMillis();
            MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
            MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, GHOST_ALPHA);
            for (Map.Entry<BlockState, java.util.ArrayList<BlockPos>> group : modelGroups.entrySet()) {
                BlockState state = group.getKey();
                for (BlockPos pos : group.getValue()) {
                    PendingGhostEntry ghost = GHOSTS.get(pos.asLong());
                    float scale = ghost == null ? BASE_SCALE : computeGrowScale(now - ghost.addedAtMs);
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    poseStack.translate(0.5D, 0.5D, 0.5D);
                    poseStack.scale(scale, scale, scale);
                    poseStack.translate(-0.5D, -0.5D, -0.5D);
                    minecraft.getBlockRenderer().renderSingleBlock(
                            state, poseStack, translucentBuffer,
                            0xF000F0, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
            blockBuffer.endBatch();
        }
        if (!fallbackPositions.isEmpty()) {
            renderFallback(poseStack, fillBuffer, fallbackPositions);
        }
    }

    private static void renderFallback(PoseStack poseStack, VertexConsumer fillBuffer, List<BlockPos> positions) {
        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            PendingGhostEntry ghost = GHOSTS.get(pos.asLong());
            float scale = ghost != null ? computeGrowScale(now - ghost.addedAtMs) : BASE_SCALE;
            double inset = 0.5D - scale * 0.44D;
            LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                    pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                    pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                    0.40F, 0.85F, 0.90F, 0.12F);
        }
    }

    private static void pruneExpired(long now) {
        Iterator<Map.Entry<Long, PendingGhostEntry>> iterator = GHOSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingGhostEntry ghost = iterator.next().getValue();
            if (now - ghost.addedAtMs > MAX_PENDING_MS) {
                iterator.remove();
            }
        }
    }

    private static float computeGrowScale(long elapsedMs) {
        if (elapsedMs < 0) {
            elapsedMs = 0;
        }
        float progress = Math.min(1.0F, elapsedMs / (float) GROW_DURATION_MS);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        float scale = progress * BASE_SCALE;
        if (progress >= 1.0F) {
            scale += PULSE_AMPLITUDE * (float) Math.sin(elapsedMs * PULSE_FREQUENCY);
        }
        return scale;
    }

    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return true;
        }
        return RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private record PendingGhostEntry(BlockPos pos, BlockState blockState, long addedAtMs) {
    }
}
