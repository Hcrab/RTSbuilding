package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.core.BlockPos;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

/**
 * Manages merged skeleton caching, dynamic edge computation, and rendering
 * of confirmed destructive work areas (both chain-destroy and range-destroy).
 *
 * <p>When the player confirms a destructive work area, individual per-block
 * outlines are replaced by a merged-outer-perimeter skeleton for visual
 * clarity. As blocks are destroyed (via {@link #markDestroyed(BlockPos)}),
 * the skeleton incrementally updates to expose newly adjacent faces.
 */
public final class MergedSkeletonRenderer {

    private static CachedMergedSkeleton cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
    private static final Set<Long> PENDING_DESTROYED_BLOCK_KEYS = new HashSet<>();

    private static final int MAX_MERGED_NO_DEPTH_EDGES = 4096;
    private static final int MAX_MERGED_FILL_BLOCKS = 768;

    private MergedSkeletonRenderer() {
    }

    // ===== Public API (called externally and from ShapeGhostRenderer) =====

    /** Records a block as destroyed so it can be removed from the merged skeleton. */
    public static void markDestroyed(BlockPos pos) {
        if (pos != null) {
            PENDING_DESTROYED_BLOCK_KEYS.add(pos.asLong());
        }
    }

    /** Clears cached skeleton (e.g. when destructive session ends). */
    static void clearCache() {
        cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
        PENDING_DESTROYED_BLOCK_KEYS.clear();
    }

    /** Returns true if the cached skeleton matches the given preview and is non-empty. */
    static boolean hasCachedSkeleton(ShapeDataRecords.GhostPreview preview) {
        return cachedMergedSkeleton.matchesPreview(preview) && !cachedMergedSkeleton.isEmpty();
    }

    /** Returns true even when the matching cached skeleton has been fully eroded. */
    static boolean hasSkeletonCacheForPreview(ShapeDataRecords.GhostPreview preview) {
        return cachedMergedSkeleton.matchesPreview(preview);
    }

    /**
     * Renders the merged skeleton from cache if available.
     * @return true if rendering was performed
     */
    static boolean renderCachedSkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        return renderCachedSkeleton(preview, poseStack, lineBuffer, fillBuffer, progress, noDepthAlpha, fillAlpha, 1.0F);
    }

    static boolean renderCachedSkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha,
            float alphaMultiplier) {
        if (!cachedMergedSkeleton.matchesPreview(preview)) return false;
        CachedMergedSkeleton skeleton = getCachedSkeleton(preview);
        if (skeleton.isEmpty()) return false;
        renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, alphaMultiplier);
        return true;
    }

    /**
     * Renders the merged skeleton without cache warm-up (for already-started batches).
     * Builds skeleton on the fly if needed.
     */
    static void renderMergedSkeletonFast(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        renderMergedSkeletonFast(preview, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, 1.0F);
    }

    static void renderMergedSkeletonFast(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha,
            float alphaMultiplier) {
        renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, alphaMultiplier);
    }

    static void renderMergedSkeletonSnapshot(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha,
            float alphaMultiplier) {
        if (preview == null || preview.blocks() == null || preview.blocks().isEmpty()) return;
        CachedMergedSkeleton skeleton = buildMergedSkeleton(preview, blockCollectionKey(preview.blocks()));
        if (skeleton.isEmpty()) return;
        renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, alphaMultiplier);
    }

    // ===== Confirmed destructive work area rendering =====

    /**
     * Renders the confirmed chain-destroy work area with merged skeleton.
     */
    static void renderConfirmedDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress) {
        renderConfirmedDestroyWorkArea(preview, poseStack, lineBuffer, fillBuffer, progress, 1.0F);
    }

    static void renderConfirmedDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer, progress, 0.30F, 0.035F,
                alphaMultiplier);
    }

    /**
     * Renders the confirmed range-destroy work area. Falls back to
     * per-cell rendering when the merged skeleton cache is cold.
     */
    static void renderConfirmedRangeDestroy(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress) {
        if (ShapeGhostRenderer.hasStartedDestroyBatch(ClientRtsController.get(), preview)) {
            renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer, 1.0F, 0.30F, 0.030F,
                    1.0F);
            return;
        }
        if (cachedMergedSkeleton.matchesPreview(preview)) {
            CachedMergedSkeleton skeleton = getCachedSkeleton(preview);
            if (!skeleton.isEmpty()) {
                renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer, 1.0F, 0.30F, 0.030F,
                        1.0F);
            }
            return;
        }
        // Fall back to per-cell rendering (handled by caller via DestructiveGhostRenderer)
    }

    // ===== Merged skeleton rendering =====

    private static void renderMergedDestroySkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, 1.0F);
    }

    private static void renderMergedDestroySkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha,
            float alphaMultiplier) {
        CachedMergedSkeleton skeleton = getCachedSkeleton(preview);
        if (skeleton.isEmpty()) return;
        renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, alphaMultiplier);
    }

    private static void renderMergedDestroySkeleton(CachedMergedSkeleton skeleton, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer,
                progress, noDepthAlpha, fillAlpha, 1.0F);
    }

    private static void renderMergedDestroySkeleton(CachedMergedSkeleton skeleton, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha,
            float alphaMultiplier) {
        List<UltimineBlockMerger.EdgeLine> edges = skeleton.edges();
        if (edges.isEmpty()) return;
        float alpha = RenderingUtil.clamp01(alphaMultiplier);
        if (alpha <= 0.0F) return;

        var matrix = poseStack.last().pose();
        float edgeR = RenderingUtil.lerp(1.00F, 0.38F, progress);
        float edgeG = RenderingUtil.lerp(0.86F, 1.00F, progress);
        float edgeB = RenderingUtil.lerp(0.22F, 0.42F, progress);

        UltimineGhostRenderer.renderPass1(edges, matrix, lineBuffer, edgeR, edgeG, edgeB, 0.95F * alpha);
        if (edges.size() <= MAX_MERGED_NO_DEPTH_EDGES) {
            UltimineGhostRenderer.renderPass2(edges, matrix, edgeR, edgeG, edgeB, noDepthAlpha * alpha);
        }
        if (skeleton.fillBlocks().size() <= MAX_MERGED_FILL_BLOCKS) {
            UltimineGhostRenderer.renderFill(skeleton.fillBlocks(), poseStack, fillBuffer, edgeR, edgeG, edgeB,
                    fillAlpha * alpha);
        }
    }

    // ===== Skeleton caching =====

    private static CachedMergedSkeleton getCachedSkeleton(ShapeDataRecords.GhostPreview preview) {
        if (preview == null || preview.blocks() == null || preview.blocks().isEmpty()) {
            return CachedMergedSkeleton.EMPTY;
        }
        if (cachedMergedSkeleton.matchesPreview(preview)) {
            cachedMergedSkeleton = applyPendingDestroyedBlocks(cachedMergedSkeleton);
            return cachedMergedSkeleton;
        }
        int key = blockCollectionKey(preview.blocks());
        if (cachedMergedSkeleton.matchesKey(key, preview.blocks().size(),
                preview.chainDestroyPreview(), preview.confirmedWorkArea())) {
            cachedMergedSkeleton = cachedMergedSkeleton.withPreview(preview);
            cachedMergedSkeleton = applyPendingDestroyedBlocks(cachedMergedSkeleton);
            return cachedMergedSkeleton;
        }
        cachedMergedSkeleton = buildMergedSkeleton(preview, key);
        cachedMergedSkeleton = applyPendingDestroyedBlocks(cachedMergedSkeleton);
        return cachedMergedSkeleton;
    }

    private static CachedMergedSkeleton buildMergedSkeleton(ShapeDataRecords.GhostPreview preview, int key) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) return CachedMergedSkeleton.EMPTY;

        List<BlockPos> remainingBlocks = List.copyOf(blocks);
        Set<Long> remainingKeys = buildBlockKeySet(remainingBlocks);
        EdgeBuild edgeBuild = buildFastSurfaceEdgeBuild(remainingBlocks, remainingKeys);
        if (edgeBuild.visibleEdges().isEmpty()) return CachedMergedSkeleton.EMPTY;

        List<BlockPos> fillBlocks = buildFillBlocks(remainingBlocks, remainingKeys);
        return new CachedMergedSkeleton(
                preview, key, blocks.size(),
                preview.chainDestroyPreview(), preview.confirmedWorkArea(),
                remainingBlocks, remainingKeys,
                edgeBuild.edgeMap(),
                List.copyOf(edgeBuild.visibleEdges()),
                List.copyOf(fillBlocks));
    }

    // ===== Incremental update for destroyed blocks =====

    private static CachedMergedSkeleton applyPendingDestroyedBlocks(CachedMergedSkeleton skeleton) {
        if (skeleton.isSourceEmpty() || PENDING_DESTROYED_BLOCK_KEYS.isEmpty()) {
            return skeleton;
        }
        Set<Long> remainingKeys = new HashSet<>(skeleton.remainingBlockKeys());
        Map<EdgeKey, EdgeAccumulator> edgeMap = skeleton.edgeMap();
        List<Long> removedKeys = new ArrayList<>();
        boolean changed = false;

        for (Long destroyedKey : PENDING_DESTROYED_BLOCK_KEYS) {
            if (destroyedKey != null && remainingKeys.contains(destroyedKey)) {
                removeBlockSurfaceContributions(edgeMap, BlockPos.of(destroyedKey), remainingKeys);
                removedKeys.add(destroyedKey);
                changed = true;
            }
        }
        PENDING_DESTROYED_BLOCK_KEYS.clear();
        if (!changed) return skeleton;

        for (Long removedKey : removedKeys) {
            remainingKeys.remove(removedKey);
        }
        for (Long removedKey : removedKeys) {
            BlockPos removedPos = BlockPos.of(removedKey);
            addNewlyExposedNeighbourContributions(edgeMap, removedPos, remainingKeys);
        }
        List<BlockPos> fillBlocks = remainingKeys.size() <= MAX_MERGED_FILL_BLOCKS
                ? buildFillBlocks(remainingKeys)
                : List.of();
        return skeleton.withRemaining(
                skeleton.remainingBlocks(),
                Set.copyOf(remainingKeys),
                edgeMap,
                List.copyOf(visibleEdgeLines(edgeMap)),
                List.copyOf(fillBlocks));
    }

    // ===== Edge contribution management =====

    private static void removeBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos,
            Set<Long> blockKeys) {
        if (pos == null || !blockKeys.contains(pos.asLong())) return;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) removeFaceEdges(edges, x, y, z, FaceSide.EAST);
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) removeFaceEdges(edges, x, y, z, FaceSide.WEST);
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) removeFaceEdges(edges, x, y, z, FaceSide.UP);
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) removeFaceEdges(edges, x, y, z, FaceSide.DOWN);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) removeFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) removeFaceEdges(edges, x, y, z, FaceSide.NORTH);
    }

    private static void addNewlyExposedNeighbourContributions(Map<EdgeKey, EdgeAccumulator> edges, BlockPos removedPos,
            Set<Long> remainingKeys) {
        int x = removedPos.getX(), y = removedPos.getY(), z = removedPos.getZ();
        addBlockFaceIfPresent(edges, x + 1, y, z, FaceSide.WEST, remainingKeys);
        addBlockFaceIfPresent(edges, x - 1, y, z, FaceSide.EAST, remainingKeys);
        addBlockFaceIfPresent(edges, x, y + 1, z, FaceSide.DOWN, remainingKeys);
        addBlockFaceIfPresent(edges, x, y - 1, z, FaceSide.UP, remainingKeys);
        addBlockFaceIfPresent(edges, x, y, z + 1, FaceSide.NORTH, remainingKeys);
        addBlockFaceIfPresent(edges, x, y, z - 1, FaceSide.SOUTH, remainingKeys);
    }

    private static void addBlockFaceIfPresent(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side,
            Set<Long> blockKeys) {
        if (blockKeys.contains(BlockPos.asLong(x, y, z))) {
            addFaceEdges(edges, x, y, z, side);
        }
    }

    private static void addBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z,
            Set<Long> blockKeys) {
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) addFaceEdges(edges, x, y, z, FaceSide.EAST);
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) addFaceEdges(edges, x, y, z, FaceSide.WEST);
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) addFaceEdges(edges, x, y, z, FaceSide.UP);
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) addFaceEdges(edges, x, y, z, FaceSide.DOWN);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) addFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) addFaceEdges(edges, x, y, z, FaceSide.NORTH);
    }

    // ===== Fill block computation =====

    private static List<BlockPos> buildFillBlocks(List<BlockPos> blocks, Set<Long> remainingKeys) {
        if (blocks == null || blocks.isEmpty() || remainingKeys == null || remainingKeys.isEmpty()
                || remainingKeys.size() > MAX_MERGED_FILL_BLOCKS) return List.of();
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (pos != null && remainingKeys.contains(pos.asLong()) && hasMissingFaceNeighbour(pos, remainingKeys)) {
                outerBlocks.add(pos);
            }
        }
        return outerBlocks;
    }

    private static List<BlockPos> buildFillBlocks(Set<Long> remainingKeys) {
        if (remainingKeys == null || remainingKeys.isEmpty() || remainingKeys.size() > MAX_MERGED_FILL_BLOCKS) return List.of();
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (Long key : remainingKeys) {
            if (key == null) continue;
            BlockPos pos = BlockPos.of(key);
            if (hasMissingFaceNeighbour(pos, remainingKeys)) outerBlocks.add(pos);
        }
        return outerBlocks;
    }

    private static boolean hasMissingFaceNeighbour(BlockPos pos, Set<Long> blockKeys) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return !blockKeys.contains(BlockPos.asLong(x + 1, y, z))
                || !blockKeys.contains(BlockPos.asLong(x - 1, y, z))
                || !blockKeys.contains(BlockPos.asLong(x, y + 1, z))
                || !blockKeys.contains(BlockPos.asLong(x, y - 1, z))
                || !blockKeys.contains(BlockPos.asLong(x, y, z + 1))
                || !blockKeys.contains(BlockPos.asLong(x, y, z - 1));
    }

    private static Set<Long> buildBlockKeySet(List<BlockPos> blocks) {
        Set<Long> keys = new HashSet<>();
        if (blocks != null) {
            for (BlockPos pos : blocks) {
                if (pos != null) keys.add(pos.asLong());
            }
        }
        return keys;
    }

    // ===== Edge build and fast building =====

    private static EdgeBuild buildFastSurfaceEdgeBuild(List<BlockPos> blocks, Set<Long> blockKeys) {
        if (blocks == null || blocks.isEmpty() || blockKeys == null || blockKeys.isEmpty()) return EdgeBuild.EMPTY;
        Map<EdgeKey, EdgeAccumulator> edges = new HashMap<>(Math.max(64, blocks.size() * 8));
        for (BlockPos pos : blocks) {
            if (pos != null) addBlockSurfaceContributions(edges, pos.getX(), pos.getY(), pos.getZ(), blockKeys);
        }
        return new EdgeBuild(edges, visibleEdgeLines(edges));
    }

    private static List<UltimineBlockMerger.EdgeLine> visibleEdgeLines(Map<EdgeKey, EdgeAccumulator> edgeMap) {
        if (edgeMap == null || edgeMap.isEmpty()) return List.of();
        List<UltimineBlockMerger.EdgeLine> result = new ArrayList<>(edgeMap.size());
        for (Map.Entry<EdgeKey, EdgeAccumulator> entry : edgeMap.entrySet()) {
            if (entry.getValue().isVisible()) result.add(entry.getKey().toLine());
        }
        return result;
    }

    // ===== Face edge operations =====

    private static void addFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        switch (side) {
            case EAST  -> { addEdge(edges, x1, y0, z0, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x1, y0, z0, side); }
            case WEST  -> { addEdge(edges, x0, y0, z0, x0, y0, z1, side); addEdge(edges, x0, y0, z1, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x0, y1, z0, side); addEdge(edges, x0, y1, z0, x0, y0, z0, side); }
            case UP    -> { addEdge(edges, x0, y1, z0, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x0, y1, z0, side); }
            case DOWN  -> { addEdge(edges, x0, y0, z0, x1, y0, z0, side); addEdge(edges, x1, y0, z0, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x0, y0, z1, side); addEdge(edges, x0, y0, z1, x0, y0, z0, side); }
            case SOUTH -> { addEdge(edges, x0, y0, z1, x1, y0, z1, side); addEdge(edges, x1, y0, z1, x1, y1, z1, side); addEdge(edges, x1, y1, z1, x0, y1, z1, side); addEdge(edges, x0, y1, z1, x0, y0, z1, side); }
            case NORTH -> { addEdge(edges, x0, y0, z0, x0, y1, z0, side); addEdge(edges, x0, y1, z0, x1, y1, z0, side); addEdge(edges, x1, y1, z0, x1, y0, z0, side); addEdge(edges, x1, y0, z0, x0, y0, z0, side); }
        }
    }

    private static void removeFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x, x1 = x + 1, y0 = y, y1 = y + 1, z0 = z, z1 = z + 1;
        switch (side) {
            case EAST  -> { removeEdge(edges, x1, y0, z0, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x1, y0, z0, side); }
            case WEST  -> { removeEdge(edges, x0, y0, z0, x0, y0, z1, side); removeEdge(edges, x0, y0, z1, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x0, y1, z0, side); removeEdge(edges, x0, y1, z0, x0, y0, z0, side); }
            case UP    -> { removeEdge(edges, x0, y1, z0, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x0, y1, z0, side); }
            case DOWN  -> { removeEdge(edges, x0, y0, z0, x1, y0, z0, side); removeEdge(edges, x1, y0, z0, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x0, y0, z1, side); removeEdge(edges, x0, y0, z1, x0, y0, z0, side); }
            case SOUTH -> { removeEdge(edges, x0, y0, z1, x1, y0, z1, side); removeEdge(edges, x1, y0, z1, x1, y1, z1, side); removeEdge(edges, x1, y1, z1, x0, y1, z1, side); removeEdge(edges, x0, y1, z1, x0, y0, z1, side); }
            case NORTH -> { removeEdge(edges, x0, y0, z0, x0, y1, z0, side); removeEdge(edges, x0, y1, z0, x1, y1, z0, side); removeEdge(edges, x1, y1, z0, x1, y0, z0, side); removeEdge(edges, x1, y0, z0, x0, y0, z0, side); }
        }
    }

    private static void addEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2, FaceSide side) {
        edges.computeIfAbsent(EdgeKey.of(x1, y1, z1, x2, y2, z2), ignored -> new EdgeAccumulator()).add(side);
    }

    private static void removeEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2, FaceSide side) {
        EdgeKey key = EdgeKey.of(x1, y1, z1, x2, y2, z2);
        EdgeAccumulator acc = edges.get(key);
        if (acc == null) return;
        acc.remove(side);
        if (acc.isEmpty()) edges.remove(key);
    }

    // ===== Hashing =====

    private static int blockCollectionKey(List<BlockPos> blocks) {
        long hash = 0xCBF29CE484222325L;
        for (BlockPos pos : blocks) {
            long value = pos == null ? 0L : pos.asLong();
            hash ^= value;
            hash *= 0x100000001B3L;
        }
        hash ^= blocks.size();
        return (int) (hash ^ (hash >>> 32));
    }

    // ===== Utility =====

    // ===== Internal records and types =====

    private record CachedMergedSkeleton(
            ShapeDataRecords.GhostPreview preview,
            int key,
            int blockCount,
            boolean chainDestroyPreview,
            boolean confirmedWorkArea,
            List<BlockPos> remainingBlocks,
            Set<Long> remainingBlockKeys,
            Map<EdgeKey, EdgeAccumulator> edgeMap,
            List<UltimineBlockMerger.EdgeLine> edges,
            List<BlockPos> fillBlocks) {

        private static final CachedMergedSkeleton EMPTY = new CachedMergedSkeleton(
                null, 0, 0, false, false, List.of(), Set.of(), Map.of(), List.of(), List.of());

        private boolean isEmpty() { return this.edges.isEmpty(); }
        private boolean isSourceEmpty() { return this.preview == null || this.remainingBlockKeys.isEmpty(); }
        private boolean matchesPreview(ShapeDataRecords.GhostPreview candidate) { return candidate != null && candidate == this.preview; }

        private boolean matchesKey(int candidateKey, int candidateBlockCount, boolean candidateChainDestroyPreview,
                boolean candidateConfirmedWorkArea) {
            return this.preview != null && !isSourceEmpty()
                    && this.key == candidateKey && this.blockCount == candidateBlockCount
                    && this.chainDestroyPreview == candidateChainDestroyPreview
                    && this.confirmedWorkArea == candidateConfirmedWorkArea;
        }

        private CachedMergedSkeleton withPreview(ShapeDataRecords.GhostPreview candidate) {
            return new CachedMergedSkeleton(candidate, this.key, this.blockCount, this.chainDestroyPreview,
                    this.confirmedWorkArea, this.remainingBlocks, this.remainingBlockKeys, this.edgeMap,
                    this.edges, this.fillBlocks);
        }

        private CachedMergedSkeleton withRemaining(List<BlockPos> nextBlocks, Set<Long> nextKeys,
                Map<EdgeKey, EdgeAccumulator> nextEdgeMap,
                List<UltimineBlockMerger.EdgeLine> nextEdges, List<BlockPos> nextFillBlocks) {
            return new CachedMergedSkeleton(this.preview, this.key, this.blockCount, this.chainDestroyPreview,
                    this.confirmedWorkArea, nextBlocks, nextKeys, nextEdgeMap, nextEdges, nextFillBlocks);
        }
    }

    private record EdgeBuild(Map<EdgeKey, EdgeAccumulator> edgeMap, List<UltimineBlockMerger.EdgeLine> visibleEdges) {
        private static final EdgeBuild EMPTY = new EdgeBuild(Map.of(), List.of());
    }

    private enum FaceSide { EAST, WEST, UP, DOWN, SOUTH, NORTH;
        private static final int COUNT = values().length;
    }

    private record EdgeKey(int x1, int y1, int z1, int x2, int y2, int z2) {
        private static EdgeKey of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (compareVertex(x1, y1, z1, x2, y2, z2) <= 0) return new EdgeKey(x1, y1, z1, x2, y2, z2);
            return new EdgeKey(x2, y2, z2, x1, y1, z1);
        }

        private static int compareVertex(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (x1 != x2) return Integer.compare(x1, x2);
            if (y1 != y2) return Integer.compare(y1, y2);
            return Integer.compare(z1, z2);
        }

        private UltimineBlockMerger.EdgeLine toLine() {
            return new UltimineBlockMerger.EdgeLine(this.x1, this.y1, this.z1, this.x2, this.y2, this.z2);
        }
    }

    private static final class EdgeAccumulator {
        private final int[] sideCounts = new int[FaceSide.COUNT];
        private int total;

        private void add(FaceSide side) { this.sideCounts[side.ordinal()]++; this.total++; }
        private void remove(FaceSide side) {
            int idx = side.ordinal();
            if (this.sideCounts[idx] <= 0) return;
            this.sideCounts[idx]--;
            this.total--;
        }
        private boolean isEmpty() { return this.total <= 0; }
        private boolean isVisible() { return this.total == 1 || sideTypeCount() > 1; }

        private int sideTypeCount() {
            int count = 0;
            for (int sc : this.sideCounts) { if (sc > 0) count++; }
            return count;
        }
    }
}
