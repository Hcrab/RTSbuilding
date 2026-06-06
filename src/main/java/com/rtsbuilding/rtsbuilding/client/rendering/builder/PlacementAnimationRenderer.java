package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Short client-only visual confirmation for server-approved RTS block changes.
 *
 * <p>Also manages pending placement ghosts — translucent block models shown at
 * positions the client has submitted for placement but has not yet received
 * server confirmation for. The ghost is removed when the server sends the
 * placement-animation confirmation via {@link #add(BlockPos)}.
 */
public final class PlacementAnimationRenderer {
    /** Alpha for pending ghost block models */
    private static final float PENDING_GHOST_ALPHA = 0.60F;

    // ---- Pending ghost animation parameters ----

    /** Duration of the grow-in animation (ms) */
    private static final long GHOST_GROW_DURATION_MS = 220L;

    /** Final scale after grow-in completes */
    private static final float GHOST_BASE_SCALE = 0.8F;

    /** Amplitude of the subtle breathing pulse after full growth */
    private static final float GHOST_PULSE_AMPLITUDE = 0.025F;

    /** Angular frequency of the breathing oscillation (rad/ms) */
    private static final float GHOST_PULSE_FREQUENCY = 0.008F;

    // ---- Destroy ghost animation parameters ----

    /** Duration of the shrink-out animation (ms) — reverse of ghost grow-in */
    private static final long DESTROY_DURATION_MS = 220L;

    private PlacementAnimationRenderer() {
    }

    /** Pending ghosts: pos.asLong() -> entry */
    private static final Map<Long, PendingGhostEntry> PENDING_GHOSTS = new LinkedHashMap<>();

    /** Destroy ghosts: pos.asLong() -> entry */
    private static final Map<Long, DestroyGhostEntry> DESTROY_GHOSTS = new LinkedHashMap<>();

    // ===== Pending ghost management =====

    /**
     * Registers a batch of positions as pending placement ghosts.
     * These positions will render as translucent block models until the server
     * confirms placement via {@link #add(BlockPos)}.
     *
     * @param positions  the block positions that have been submitted for placement
     * @param blockState the block state to render at those positions (may be null,
     *                   in which case a coloured-box fallback is used)
     */
    public static void addPendingBatch(List<BlockPos> positions, BlockState blockState) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            PENDING_GHOSTS.put(pos.asLong(), new PendingGhostEntry(pos.immutable(), blockState, now));
        }
    }

    /** Clears all pending ghosts (e.g. when closing the screen). */
    public static void clearAllPending() {
        PENDING_GHOSTS.clear();
    }

    // ===== Server confirmation handlers =====

    public static void add(BlockPos pos) {
        // When server confirms a block was placed, remove the pending ghost
        if (pos != null) {
            PENDING_GHOSTS.remove(pos.asLong());
        }
    }

    /** Called when server confirms a block was destroyed — shows shrink-out animation. */
    public static void addDestroy(BlockPos pos) {
        if (pos == null) return;
        BlockState state = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            state = mc.level.getBlockState(pos);
            if (state.isAir()) {
                state = null; // block already gone, use fallback
            }
        }
        DESTROY_GHOSTS.put(pos.asLong(), new DestroyGhostEntry(pos.immutable(), state, System.currentTimeMillis()));
    }

    // ===== Render =====

    public static void render(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        if (com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled()) {
            renderPendingWireframes(poseStack, lineBuffer);
            renderDestroyWireframes(poseStack, lineBuffer);
        } else {
            if (!PENDING_GHOSTS.isEmpty()) {
                renderPendingGhosts(minecraft, poseStack, fillBuffer);
            }
            if (!DESTROY_GHOSTS.isEmpty()) {
                renderDestroyGhosts(minecraft, poseStack, fillBuffer);
            }
        }
    }

    // ===== Pending ghost rendering =====

    private static void renderPendingGhosts(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        Iterator<Map.Entry<Long, PendingGhostEntry>> iterator = PENDING_GHOSTS.entrySet().iterator();

        // Separate model-renderable entries from fallback entries
        Map<BlockState, java.util.ArrayList<BlockPos>> modelGroups = new HashMap<>();
        java.util.ArrayList<BlockPos> fallbackPositions = new java.util.ArrayList<>();

        while (iterator.hasNext()) {
            Map.Entry<Long, PendingGhostEntry> entry = iterator.next();
            PendingGhostEntry ghost = entry.getValue();
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
            MultiBufferSource translucentBuffer = new ShapeGhostRenderer.GhostAlphaBufferSource(blockBuffer, PENDING_GHOST_ALPHA);

            for (Map.Entry<BlockState, java.util.ArrayList<BlockPos>> group : modelGroups.entrySet()) {
                BlockState state = group.getKey();
                for (BlockPos pos : group.getValue()) {
                    int light = 0xF000F0; // fullbright, no lighting
                    PendingGhostEntry ghost = PENDING_GHOSTS.get(pos.asLong());
                    float scale = GHOST_BASE_SCALE;
                    if (ghost != null) {
                        long elapsed = now - ghost.addedAtMs;
                        scale = computeGhostGrowScale(elapsed);
                    }
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    // Animated scale around block centre (grow in + breathing)
                    poseStack.translate(0.5, 0.5, 0.5);
                    poseStack.scale(scale, scale, scale);
                    poseStack.translate(-0.5, -0.5, -0.5);
                    minecraft.getBlockRenderer().renderSingleBlock(
                            state,
                            poseStack,
                            translucentBuffer,
                            light,
                            OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
            blockBuffer.endBatch();
        }

        // Render fallback (coloured boxes for unresolvable states)
        if (!fallbackPositions.isEmpty()) {
            renderPendingGhostFallback(poseStack, fillBuffer, fallbackPositions);
        }
    }

    private static void renderPendingGhostFallback(PoseStack poseStack, VertexConsumer fillBuffer,
            java.util.List<BlockPos> positions) {
        long now = System.currentTimeMillis();
        float fillR = 0.40F;
        float fillG = 0.85F;
        float fillB = 0.90F;
        float fillA = 0.12F;

        for (BlockPos pos : positions) {
            PendingGhostEntry ghost = PENDING_GHOSTS.get(pos.asLong());
            float scale = GHOST_BASE_SCALE;
            if (ghost != null) {
                long elapsed = now - ghost.addedAtMs;
                scale = computeGhostGrowScale(elapsed);
            }
            // Inset interpolates from 0.5 (center point) to 0.06 (full size)
            double inset = 0.5D - scale * 0.44D;
            double minX = pos.getX() + inset;
            double minY = pos.getY() + inset;
            double minZ = pos.getZ() + inset;
            double maxX = pos.getX() + 1.0D - inset;
            double maxY = pos.getY() + 1.0D - inset;
            double maxZ = pos.getZ() + 1.0D - inset;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }
    }

    // ===== Destroy ghost rendering =====

    private static void renderDestroyGhosts(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = DESTROY_GHOSTS.entrySet().iterator();

        // Separate model-renderable entries from fallback entries
        Map<BlockState, java.util.ArrayList<BlockPos>> modelGroups = new HashMap<>();
        java.util.ArrayList<BlockPos> fallbackPositions = new java.util.ArrayList<>();

        while (iterator.hasNext()) {
            Map.Entry<Long, DestroyGhostEntry> entry = iterator.next();
            DestroyGhostEntry ghost = entry.getValue();
            long elapsed = now - ghost.addedAtMs;

            // Remove if animation is complete
            if (elapsed > DESTROY_DURATION_MS) {
                iterator.remove();
                continue;
            }

            BlockState state = ghost.blockState;
            if (state != null && !state.isAir() && state.getRenderShape() == RenderShape.MODEL) {
                modelGroups.computeIfAbsent(state, k -> new java.util.ArrayList<>()).add(ghost.pos);
            } else {
                fallbackPositions.add(ghost.pos);
            }
        }

        // Render model groups
        if (!modelGroups.isEmpty()) {
            MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
            MultiBufferSource translucentBuffer = new ShapeGhostRenderer.GhostAlphaBufferSource(blockBuffer, PENDING_GHOST_ALPHA);

            for (Map.Entry<BlockState, java.util.ArrayList<BlockPos>> group : modelGroups.entrySet()) {
                BlockState state = group.getKey();
                for (BlockPos pos : group.getValue()) {
                    int light = 0xF000F0; // fullbright, no lighting
                    DestroyGhostEntry ghost = DESTROY_GHOSTS.get(pos.asLong());
                    float scale = GHOST_BASE_SCALE;
                    if (ghost != null) {
                        long elapsed = now - ghost.addedAtMs;
                        scale = computeGhostShrinkScale(elapsed);
                    }
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    // Animated scale around block centre (shrink out)
                    poseStack.translate(0.5, 0.5, 0.5);
                    poseStack.scale(scale, scale, scale);
                    poseStack.translate(-0.5, -0.5, -0.5);
                    minecraft.getBlockRenderer().renderSingleBlock(
                            state,
                            poseStack,
                            translucentBuffer,
                            light,
                            OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
            blockBuffer.endBatch();
        }

        // Render fallback (coloured boxes for unresolvable states)
        if (!fallbackPositions.isEmpty()) {
            renderDestroyGhostFallback(poseStack, fillBuffer, fallbackPositions);
        }
    }

    private static void renderDestroyGhostFallback(PoseStack poseStack, VertexConsumer fillBuffer,
            java.util.List<BlockPos> positions) {
        long now = System.currentTimeMillis();
        float fillR = 1.00F;
        float fillG = 0.40F;
        float fillB = 0.35F;
        float fillA = 0.15F;

        for (BlockPos pos : positions) {
            DestroyGhostEntry ghost = DESTROY_GHOSTS.get(pos.asLong());
            float scale = GHOST_BASE_SCALE;
            if (ghost != null) {
                long elapsed = now - ghost.addedAtMs;
                scale = computeGhostShrinkScale(elapsed);
            }
            // Inset interpolates from 0.06 (full size) to 0.5 (center point)
            double inset = 0.5D - scale * 0.44D;
            double minX = pos.getX() + inset;
            double minY = pos.getY() + inset;
            double minZ = pos.getZ() + inset;
            double maxX = pos.getX() + 1.0D - inset;
            double maxY = pos.getY() + 1.0D - inset;
            double maxZ = pos.getZ() + 1.0D - inset;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }
    }

    // ===== Wireframe rendering =====

    private static void renderPendingWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        long now = System.currentTimeMillis();
        float lineR = 0.30F, lineG = 0.75F, lineB = 1.00F, lineA = 0.75F;
        for (PendingGhostEntry ghost : PENDING_GHOSTS.values()) {
            BlockPos pos = ghost.pos;
            float scale = computeGhostGrowScale(now - ghost.addedAtMs);
            double inset = 0.5D - scale * 0.44D;
            double minX = pos.getX() + inset;
            double minY = pos.getY() + inset;
            double minZ = pos.getZ() + inset;
            double maxX = pos.getX() + 1.0D - inset;
            double maxY = pos.getY() + 1.0D - inset;
            double maxZ = pos.getZ() + 1.0D - inset;
            LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, lineR, lineG, lineB, lineA);
        }
    }

    private static void renderDestroyWireframes(PoseStack poseStack, VertexConsumer lineBuffer) {
        long now = System.currentTimeMillis();
        float lineR = 1.00F, lineG = 0.46F, lineB = 0.46F, lineA = 0.75F;
        Iterator<Map.Entry<Long, DestroyGhostEntry>> iterator = DESTROY_GHOSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            DestroyGhostEntry ghost = iterator.next().getValue();
            long elapsed = now - ghost.addedAtMs;
            if (elapsed > DESTROY_DURATION_MS) {
                iterator.remove();
                continue;
            }
            float scale = computeGhostShrinkScale(elapsed);
            double inset = 0.5D - scale * 0.44D;
            double minX = ghost.pos.getX() + inset;
            double minY = ghost.pos.getY() + inset;
            double minZ = ghost.pos.getZ() + inset;
            double maxX = ghost.pos.getX() + 1.0D - inset;
            double maxY = ghost.pos.getY() + 1.0D - inset;
            double maxZ = ghost.pos.getZ() + 1.0D - inset;
            LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, lineR, lineG, lineB, lineA);
        }
    }

    // ===== Ghost animation helpers =====

    /**
     * Computes the animated ghost scale for a given elapsed time since creation.
     * <p>
     * Phase 1 (grow in): ease-out scale from nearly zero to GHOST_BASE_SCALE over
     *   GHOST_GROW_DURATION_MS, matching BuildingGadgets2's {@code renderGrow}.
     * Phase 2 (breathing): subtle sinusoidal oscillation around GHOST_BASE_SCALE
     *   to give a faint "alive" feel while awaiting server confirmation.
     *
     * @param elapsedMs elapsed time since the ghost entry was added (ms)
     * @return the animated scale to apply (centred on the block position)
     */
    private static float computeGhostGrowScale(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        float progress = Math.min(1.0F, elapsedMs / (float) GHOST_GROW_DURATION_MS);
        // Quadratic ease-out: starts fast, slows near the end
        progress = 1.0F - (1.0F - progress) * (1.0F - progress);
        float scale = progress * GHOST_BASE_SCALE;
        if (progress >= 1.0F) {
            // Subtle breathing pulse while waiting for server confirmation
            scale += GHOST_PULSE_AMPLITUDE * (float) Math.sin(elapsedMs * GHOST_PULSE_FREQUENCY);
        }
        return scale;
    }

    /**
     * Computes the animated shrink scale for destroy ghosts (reverse of grow-in).
     * <p>
     * Starts at GHOST_BASE_SCALE and eases out to 0 over DESTROY_DURATION_MS,
     * producing a satisfying "shrink away" effect that mirrors the placement grow-in.
     *
     * @param elapsedMs elapsed time since the destroy ghost entry was added (ms)
     * @return the animated scale to apply (centred on the block position)
     */
    private static float computeGhostShrinkScale(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        float progress = Math.min(1.0F, elapsedMs / (float) DESTROY_DURATION_MS);
        // Quadratic ease-out on the reverse: starts shrinking fast, slows near the end
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return Math.max(0.0F, GHOST_BASE_SCALE * (1.0F - eased));
    }

    // ===== Internal records =====

    /** Tracks a pending ghost position with its block state and addition time. */
    private record PendingGhostEntry(BlockPos pos, BlockState blockState, long addedAtMs) {
    }

    /** Tracks a destroy ghost position with its captured block state and addition time. */
    private record DestroyGhostEntry(BlockPos pos, BlockState blockState, long addedAtMs) {
    }
}
