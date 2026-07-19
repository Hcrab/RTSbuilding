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

import java.util.*;

/**
 * Manages and renders pending placement ghosts — translucent block models
 * shown at positions the client has submitted for placement but has not yet
 * received server confirmation for.
 */
public final class PendingGhostRenderer {

    private static final float GHOST_ALPHA = 0.60F;

    // ---- Ghost animation parameters ----

    private static final long GROW_DURATION_MS = 220L;
    private static final long MAX_PENDING_MS = 5000L;
    private static final float BASE_SCALE = 0.8F;
    private static final float PULSE_AMPLITUDE = 0.025F;
    private static final float PULSE_FREQUENCY = 0.008F;

    private PendingGhostRenderer() {
    }

    /** pos.asLong() -> entry */
    private static final Map<Long, PendingGhostEntry> GHOSTS = new LinkedHashMap<>();

    // ===== Public API =====

    /**
     * Registers a batch of positions as pending placement ghosts.
     *
     * @param positions  the block positions submitted for placement
     * @param blockState the block state to render (may be null for fallback)
     */
    public static void addPendingBatch(List<BlockPos> positions, BlockState blockState) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            GHOSTS.put(pos.asLong(), new PendingGhostEntry(pos.immutable(), blockState, now));
        }
    }

    /** Clears all pending ghosts (e.g. when closing the screen). */
    public static void clearAll() {
        GHOSTS.clear();
    }

    /** Removes the ghost at the given position (server confirmed placement). */
    public static void remove(BlockPos pos) {
        if (pos != null) {
            GHOSTS.remove(pos.asLong());
        }
    }

    // ===== Rendering (called from facade) =====

    /** Renders pending ghosts as translucent block models (or fallback boxes). */
    static void render(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderPendingGhosts(minecraft, poseStack, fillBuffer);
    }

    /** Renders pending ghosts as wireframes (line-only mode). */
    static void renderWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        float lineR = 0.30F, lineG = 0.75F, lineB = 1.00F, lineA = 0.75F;
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            BlockPos pos = ghost.pos;
            float scale = computeGrowScale(now - ghost.addedAtMs);
            double inset = 0.5D - scale * 0.44D;
            double minX = pos.getX() + inset;
            double minY = pos.getY() + inset;
            double minZ = pos.getZ() + inset;
            double maxX = pos.getX() + 1.0D - inset;
            double maxY = pos.getY() + 1.0D - inset;
            double maxZ = pos.getZ() + 1.0D - inset;
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(poseStack, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, lineR, lineG, lineB, lineA);
        }
    }

    static void collectModelAnimations(List<PlacementAnimationRenderer.ModelAnimation> out) {
        long now = System.currentTimeMillis();
        pruneExpired(now);
        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)
                    || ghost.blockState == null
                    || ghost.blockState.isAir()
                    || ghost.blockState.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            out.add(new PlacementAnimationRenderer.ModelAnimation(
                    ghost.blockState,
                    ghost.pos,
                    GHOST_ALPHA,
                    computeGrowScale(now - ghost.addedAtMs)));
        }
    }

    // ===== Internal rendering =====

    private static void renderPendingGhosts(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        if (GHOSTS.isEmpty()) return;
        pruneExpired(System.currentTimeMillis());
        if (GHOSTS.isEmpty()) return;

        // Separate model-renderable entries from fallback entries
        Map<BlockState, java.util.ArrayList<BlockPos>> modelGroups = new HashMap<>();
        java.util.ArrayList<BlockPos> fallbackPositions = new java.util.ArrayList<>();

        for (PendingGhostEntry ghost : GHOSTS.values()) {
            if (!isWithinBounds(ghost.pos)) continue;
            BlockState state = ghost.blockState;
            if (state != null && !state.isAir() && state.getRenderShape() == RenderShape.MODEL) {
                modelGroups.computeIfAbsent(state, k -> new java.util.ArrayList<>()).add(ghost.pos);
            } else {
                fallbackPositions.add(ghost.pos);
            }
        }

        // Render model groups
        if (!modelGroups.isEmpty()) {
            long now = System.currentTimeMillis();
            MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();

            for (Map.Entry<BlockState, java.util.ArrayList<BlockPos>> group : modelGroups.entrySet()) {
                BlockState state = group.getKey();
                for (BlockPos pos : group.getValue()) {
                    float scale = computeGrowScale(now - GHOSTS.get(pos.asLong()).addedAtMs);
                    GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer,
                            state, pos, GHOST_ALPHA, scale);
                }
            }
            blockBuffer.endBatch();
        }

        // Render fallback (coloured boxes for unresolvable states)
        if (!fallbackPositions.isEmpty()) {
            renderFallback(poseStack, fillBuffer, fallbackPositions);
        }
    }

    private static void renderFallback(PoseStack poseStack, VertexConsumer fillBuffer,
            java.util.List<BlockPos> positions) {
        long now = System.currentTimeMillis();
        float fillR = 0.40F, fillG = 0.85F, fillB = 0.90F, fillA = 0.12F;

        for (BlockPos pos : positions) {
            PendingGhostEntry ghost = GHOSTS.get(pos.asLong());
            float scale = (ghost != null) ? computeGrowScale(now - ghost.addedAtMs) : BASE_SCALE;
            double inset = 0.5D - scale * 0.44D;
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                    pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                    fillR, fillG, fillB, fillA);
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

    // ===== Animation helpers =====

    /**
     * Computes the animated ghost scale: ease-out grow-in over GROW_DURATION_MS,
     * followed by a subtle sinusoidal breathing pulse.
     */
    private static float computeGrowScale(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        float progress = Math.min(1.0F, elapsedMs / (float) GROW_DURATION_MS);
        progress = 1.0F - (1.0F - progress) * (1.0F - progress); // quadratic ease-out
        float scale = progress * BASE_SCALE;
        if (progress >= 1.0F) {
            scale += PULSE_AMPLITUDE * (float) Math.sin(elapsedMs * PULSE_FREQUENCY);
        }
        return scale;
    }

    // ===== Internal record =====

    private record PendingGhostEntry(BlockPos pos, BlockState blockState, long addedAtMs) {
    }

    /**
     * Checks whether a block position is within RTS bounds.
     */
    private static boolean isWithinBounds(BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return true;
        return RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }
}
