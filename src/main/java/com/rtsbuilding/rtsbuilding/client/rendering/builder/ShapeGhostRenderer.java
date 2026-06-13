package com.rtsbuilding.rtsbuilding.client.rendering.builder;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders quick-build and range-destroy shape ghosts for Forge 1.20.1.
 *
 * <p>The Forge branch keeps the older render buffers, so this class ports the
 * player-facing mainline behaviour without copying the 1.21-only buffer API:
 * selection previews use per-cell helpers, confirmed destroy work areas use a
 * cached merged outer skeleton, and completed block positions are removed
 * incrementally through {@link #markDestroyed(BlockPos)}.
 */
public final class ShapeGhostRenderer {
    private static final double BOUNDARY_PADDING = 0.02D;
    private static final int MAX_MERGED_FILL_BLOCKS = 768;

    private static float smoothedDestroyProgress;
    private static long smoothedDestroyProgressMs;
    private static int smoothedDestroyProgressKey;
    private static CachedMergedSkeleton cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
    private static final Set<Long> PENDING_DESTROYED_BLOCK_KEYS = new HashSet<>();

    private ShapeGhostRenderer() {
    }

    public static void renderShapeGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            return;
        }

        boolean sawConfirmedWorkArea = false;
        for (ShapeDataRecords.GhostPreview preview : screen.getConfirmedRangeDestroyPreviews()) {
            sawConfirmedWorkArea |= isConfirmedDestructiveWorkArea(preview);
            renderGhostPreview(minecraft, preview, poseStack, lineBuffer, fillBuffer);
        }
        ShapeDataRecords.GhostPreview currentPreview = screen.getShapeGhostPreview();
        sawConfirmedWorkArea |= isConfirmedDestructiveWorkArea(currentPreview);
        renderGhostPreview(minecraft, currentPreview, poseStack, lineBuffer, fillBuffer);
        if (!sawConfirmedWorkArea) {
            cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
            PENDING_DESTROYED_BLOCK_KEYS.clear();
        }
    }

    public static void markDestroyed(BlockPos pos) {
        if (pos != null) {
            PENDING_DESTROYED_BLOCK_KEYS.add(pos.asLong());
        }
    }

    private static void renderGhostPreview(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (preview == null) {
            return;
        }
        if (!preview.chainDestroyPreview() && isEmpty(preview.blocks()) && isEmpty(preview.emptyBlocks())) {
            return;
        }

        if (preview.destructive() && preview.confirmedWorkArea()) {
            if (preview.chainDestroyPreview()) {
                renderConfirmedDestroyWorkArea(preview, poseStack, lineBuffer, fillBuffer);
            } else {
                renderConfirmedRangeDestroyWorkArea(preview, poseStack, lineBuffer, fillBuffer);
            }
            return;
        }

        if (preview.chainDestroyPreview()) {
            renderUltimineGhost(preview, poseStack, lineBuffer, fillBuffer);
            return;
        }

        if (preview.destructive()) {
            renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer, 0.0F, 1.0F);
            return;
        }

        renderBuildGhostFallback(minecraft, preview, poseStack, lineBuffer, fillBuffer);
    }

    private static void renderBuildGhostFallback(Minecraft minecraft, ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (isEmpty(blocks)) {
            return;
        }

        BlockState blockState = BuildGhostBlockStateResolver.resolve(minecraft, blocks.get(0));
        if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
            renderBlockModelGhosts(minecraft, blocks, poseStack, blockState);
            renderBuildGhostWireframes(preview, poseStack, lineBuffer);
            return;
        }
        ItemStack spawnEggStack = BuildGhostBlockStateResolver.resolveSpawnEggStack(minecraft);
        if (!spawnEggStack.isEmpty()) {
            EntityGhostRenderer.renderEntities(minecraft, blocks, poseStack, spawnEggStack);
            renderBuildGhostWireframes(preview, poseStack, lineBuffer);
            return;
        }
        if (!BuildGhostBlockStateResolver.resolveEndCrystalStack(minecraft).isEmpty()) {
            EntityGhostRenderer.renderEndCrystals(minecraft, blocks, poseStack);
            renderBuildGhostWireframes(preview, poseStack, lineBuffer);
            return;
        }

        float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
        float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
        float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
        float fillA = preview.readyConfirm() ? 0.22F : 0.16F;
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;

        for (BlockPos pos : blocks) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    lineR, lineG, lineB, 0.95F);
        }
    }

    private static void renderBlockModelGhosts(Minecraft minecraft, List<BlockPos> blocks, PoseStack poseStack,
            BlockState blockState) {
        if (minecraft == null || minecraft.level == null || isEmpty(blocks)) {
            return;
        }
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, 0.70F);
        for (BlockPos pos : blocks) {
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            int light = LevelRenderer.getLightColor(minecraft.level, pos);
            minecraft.getBlockRenderer().renderSingleBlock(
                    blockState, poseStack, translucentBuffer, light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        blockBuffer.endBatch();
    }

    private static void renderBuildGhostWireframes(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
        for (BlockPos pos : preview.blocks()) {
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    pos.getX() + 0.03D, pos.getY() + 0.03D, pos.getZ() + 0.03D,
                    pos.getX() + 0.97D, pos.getY() + 0.97D, pos.getZ() + 0.97D,
                    lineR, lineG, lineB, 0.95F);
        }
    }

    private static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        float alpha = clamp01(alphaMultiplier);
        if (alpha <= 0.0F) {
            return;
        }

        if (!isEmpty(preview.blocks()) || !isEmpty(preview.emptyBlocks())) {
            float envLineR = lerp(1.00F, 0.38F, progress);
            float envLineG = lerp(0.86F, 1.00F, progress);
            float envLineB = lerp(0.22F, 0.42F, progress);
            float envFillR = lerp(1.00F, 0.30F, progress);
            float envFillG = lerp(0.86F, 0.95F, progress);
            float envFillB = lerp(0.18F, 0.36F, progress);
            renderGhostEnvelope(
                    poseStack, lineBuffer, fillBuffer,
                    preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, 0.78F * alpha,
                    envFillR, envFillG, envFillB, 0.10F * alpha);
        }

        DestructiveCellColors colors = DestructiveCellColors.forConfirmState(preview.readyConfirm());
        float lineR = lerp(colors.lineR(), 0.38F, progress);
        float lineG = lerp(colors.lineG(), 1.00F, progress);
        float lineB = lerp(colors.lineB(), 0.42F, progress);
        float fillR = lerp(colors.fillR(), 0.30F, progress);
        float fillG = lerp(colors.fillG(), 0.95F, progress);
        float fillB = lerp(colors.fillB(), 0.36F, progress);

        for (BlockPos pos : preview.blocks()) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, colors.fillA() * alpha);
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    lineR, lineG, lineB, colors.lineA() * alpha);
        }
    }

    private static boolean isConfirmedDestructiveWorkArea(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.destructive() && preview.confirmedWorkArea();
    }

    private static void renderConfirmedRangeDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        ClientRtsController controller = ClientRtsController.get();
        if (!Config.isRangeDestroySkeletonEnabled()) {
            cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
            renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer,
                    smoothedDestroyProgress(controller, preview), 1.0F);
            return;
        }
        if (hasStartedDestroyBatch(controller, preview)) {
            renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer, 1.0F, 0.030F);
            return;
        }
        if (cachedMergedSkeleton.matchesPreview(preview)) {
            CachedMergedSkeleton skeleton = getCachedMergedSkeleton(preview);
            if (!skeleton.isEmpty()) {
                renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer, 1.0F, 0.030F);
            }
            return;
        }
        renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer,
                smoothedDestroyProgress(controller, preview), 1.0F);
    }

    private static void renderConfirmedDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer,
                smoothedDestroyProgress(ClientRtsController.get(), preview), 0.035F);
    }

    private static void renderMergedDestroySkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float fillAlpha) {
        CachedMergedSkeleton skeleton = getCachedMergedSkeleton(preview);
        if (!skeleton.isEmpty()) {
            renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer, progress, fillAlpha);
        }
    }

    private static void renderMergedDestroySkeleton(CachedMergedSkeleton skeleton, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float fillAlpha) {
        if (skeleton.edges().isEmpty()) {
            return;
        }
        float edgeR = lerp(1.00F, 0.38F, progress);
        float edgeG = lerp(0.86F, 1.00F, progress);
        float edgeB = lerp(0.22F, 0.42F, progress);
        renderEdgeLines(skeleton.edges(), poseStack.last().pose(), lineBuffer, edgeR, edgeG, edgeB, 0.95F);
        if (skeleton.fillBlocks().size() <= MAX_MERGED_FILL_BLOCKS) {
            renderFaintFill(skeleton.fillBlocks(), poseStack, fillBuffer, edgeR, edgeG, edgeB, fillAlpha);
        }
    }

    private static CachedMergedSkeleton getCachedMergedSkeleton(ShapeDataRecords.GhostPreview preview) {
        if (preview == null || isEmpty(preview.blocks())) {
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
        if (isEmpty(blocks)) {
            return CachedMergedSkeleton.EMPTY;
        }
        List<BlockPos> remainingBlocks = List.copyOf(blocks);
        Set<Long> remainingKeys = buildBlockKeySet(remainingBlocks);
        EdgeBuild edgeBuild = buildFastSurfaceEdgeBuild(remainingBlocks, remainingKeys);
        if (edgeBuild.visibleEdges().isEmpty()) {
            return CachedMergedSkeleton.EMPTY;
        }
        List<BlockPos> fillBlocks = buildFillBlocks(remainingBlocks, remainingKeys);
        return new CachedMergedSkeleton(
                preview,
                key,
                blocks.size(),
                preview.chainDestroyPreview(),
                preview.confirmedWorkArea(),
                remainingKeys,
                edgeBuild.edgeMap(),
                List.copyOf(edgeBuild.visibleEdges()),
                List.copyOf(fillBlocks));
    }

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
        if (!changed) {
            return skeleton;
        }
        for (Long removedKey : removedKeys) {
            remainingKeys.remove(removedKey);
        }
        collectNoLongerLiveNeighbourTargets(edgeMap, removedKeys, remainingKeys);
        List<BlockPos> fillBlocks = remainingKeys.size() <= MAX_MERGED_FILL_BLOCKS
                ? buildFillBlocks(remainingKeys)
                : List.of();
        return skeleton.withRemaining(
                Set.copyOf(remainingKeys),
                edgeMap,
                List.copyOf(visibleEdgeLines(edgeMap)),
                List.copyOf(fillBlocks));
    }

    private static void collectNoLongerLiveNeighbourTargets(Map<EdgeKey, EdgeAccumulator> edges,
            List<Long> removedKeys, Set<Long> remainingKeys) {
        for (int i = 0; i < removedKeys.size(); i++) {
            Long removedKey = removedKeys.get(i);
            if (removedKey == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(removedKey);
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX() + 1, pos.getY(), pos.getZ());
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX() - 1, pos.getY(), pos.getZ());
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX(), pos.getY() + 1, pos.getZ());
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX(), pos.getY() - 1, pos.getZ());
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX(), pos.getY(), pos.getZ() + 1);
            removeNoLongerLiveTargetIfPresent(edges, removedKeys, remainingKeys, pos.getX(), pos.getY(), pos.getZ() - 1);
        }
    }

    private static void removeNoLongerLiveTargetIfPresent(Map<EdgeKey, EdgeAccumulator> edges,
            List<Long> removedKeys, Set<Long> remainingKeys, int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        if (!remainingKeys.contains(key)) {
            return;
        }
        BlockPos pos = BlockPos.of(key);
        if (isLiveDestroyTarget(pos)) {
            return;
        }
        removeBlockSurfaceContributions(edges, pos, remainingKeys);
        remainingKeys.remove(key);
        removedKeys.add(key);
    }

    private static boolean isLiveDestroyTarget(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || pos == null) {
            return true;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && state.getDestroySpeed(minecraft.level, pos) >= 0.0F;
    }

    private static void renderUltimineGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (isEmpty(blocks)) {
            return;
        }
        Set<Long> blockKeys = buildBlockKeySet(blocks);
        List<BlockPos> outerBlocks = filterOuterBlocksFast(blocks, blockKeys);
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) {
            return;
        }

        float breath = getBreathFactor();
        float r = 1.00F * breath;
        float g = 0.72F * breath;
        float b = 0.24F * breath;
        float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
        float edgeR = lerp(r, 0.38F, progress);
        float edgeG = lerp(g, 1.00F, progress);
        float edgeB = lerp(b, 0.42F, progress);
        renderEdgeLines(edges, poseStack.last().pose(), lineBuffer, edgeR, edgeG, edgeB, 0.95F);
        renderFaintFill(outerBlocks, poseStack, fillBuffer, edgeR, edgeG, edgeB, 0.08F);
    }

    private static void renderEdgeLines(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b, float alpha) {
        int red = channel(r);
        int green = channel(g);
        int blue = channel(b);
        int a = channel(alpha);
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            lineBuffer.vertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .color(red, green, blue, a)
                    .normal(edge.xn(), edge.yn(), edge.zn())
                    .endVertex();
            lineBuffer.vertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .color(red, green, blue, a)
                    .normal(edge.xn(), edge.yn(), edge.zn())
                    .endVertex();
        }
    }

    private static void renderFaintFill(List<BlockPos> blocks, PoseStack poseStack, VertexConsumer fillBuffer,
            float r, float g, float b, float alpha) {
        for (BlockPos pos : blocks) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, b, alpha);
        }
    }

    private static void renderGhostEnvelope(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        Bounds bounds = Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) {
            return;
        }
        double minX = bounds.minX() - BOUNDARY_PADDING;
        double minY = bounds.minY() - BOUNDARY_PADDING;
        double minZ = bounds.minZ() - BOUNDARY_PADDING;
        double maxX = bounds.maxX() + 1.0D + BOUNDARY_PADDING;
        double maxY = bounds.maxY() + 1.0D + BOUNDARY_PADDING;
        double maxZ = bounds.maxZ() + 1.0D + BOUNDARY_PADDING;

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                fillR, fillG, fillB, fillA);
        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                lineR, lineG, lineB, lineA);
    }

    private static float smoothedDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        int key = previewKey(preview);
        float target = rawDestroyProgress(controller, preview);
        long now = System.currentTimeMillis();
        if (key != smoothedDestroyProgressKey || smoothedDestroyProgressMs <= 0L) {
            smoothedDestroyProgressKey = key;
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return clamp01(smoothedDestroyProgress);
        }
        float deltaSeconds = Math.min(0.10F, Math.max(0.0F, (now - smoothedDestroyProgressMs) / 1000.0F));
        smoothedDestroyProgressMs = now;
        float speed = target > smoothedDestroyProgress ? 4.5F : 1.8F;
        float maxDelta = speed * deltaSeconds;
        if (Math.abs(target - smoothedDestroyProgress) <= maxDelta) {
            smoothedDestroyProgress = target;
        } else {
            smoothedDestroyProgress += Math.signum(target - smoothedDestroyProgress) * maxDelta;
        }
        if (target <= 0.0F && smoothedDestroyProgress < 0.01F) {
            smoothedDestroyProgress = 0.0F;
        }
        return clamp01(smoothedDestroyProgress);
    }

    private static float rawDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null) {
            return 0.0F;
        }
        BlockPos progressPos = controller.getMineProgressPos();
        if (progressPos == null || !previewContains(preview, progressPos)) {
            return 0.0F;
        }
        int processed = controller.getUltimineProgressProcessed();
        int total = controller.getUltimineProgressTotal();
        if (processed > 0 && total > 0) {
            return 1.0F;
        }
        int stage = controller.getMineProgressStage();
        if (stage < 0) {
            return 0.0F;
        }
        return clamp01((Math.min(9, stage) + 1) / 10.0F);
    }

    private static boolean hasStartedDestroyBatch(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        if (controller == null || preview == null) {
            return false;
        }
        BlockPos progressPos = controller.getMineProgressPos();
        return progressPos != null
                && previewContains(preview, progressPos)
                && controller.getUltimineProgressProcessed() > 0
                && controller.getUltimineProgressTotal() > 0;
    }

    private static boolean previewContains(ShapeDataRecords.GhostPreview preview, BlockPos pos) {
        return preview != null && pos != null && (contains(preview.blocks(), pos) || contains(preview.emptyBlocks(), pos));
    }

    private static boolean contains(List<BlockPos> blocks, BlockPos pos) {
        if (blocks == null || pos == null) {
            return false;
        }
        for (BlockPos block : blocks) {
            if (pos.equals(block)) {
                return true;
            }
        }
        return false;
    }

    private static int previewKey(ShapeDataRecords.GhostPreview preview) {
        Bounds bounds = preview == null ? null : Bounds.from(preview.blocks(), preview.emptyBlocks());
        if (bounds == null) {
            return 0;
        }
        int result = 17;
        result = 31 * result + bounds.minX();
        result = 31 * result + bounds.minY();
        result = 31 * result + bounds.minZ();
        result = 31 * result + bounds.maxX();
        result = 31 * result + bounds.maxY();
        result = 31 * result + bounds.maxZ();
        result = 31 * result + (preview.chainDestroyPreview() ? 1 : 0);
        result = 31 * result + (preview.confirmedWorkArea() ? 1 : 0);
        return result;
    }

    private static int blockCollectionKey(List<BlockPos> blocks) {
        long hash = 0xCBF29CE484222325L;
        for (BlockPos pos : blocks) {
            hash ^= pos == null ? 0L : pos.asLong();
            hash *= 0x100000001B3L;
        }
        hash ^= blocks.size();
        return (int) (hash ^ (hash >>> 32));
    }

    private static Set<Long> buildBlockKeySet(List<BlockPos> blocks) {
        Set<Long> keys = new HashSet<>();
        if (blocks == null) {
            return keys;
        }
        for (BlockPos pos : blocks) {
            if (pos != null) {
                keys.add(pos.asLong());
            }
        }
        return keys;
    }

    private static EdgeBuild buildFastSurfaceEdgeBuild(List<BlockPos> blocks, Set<Long> blockKeys) {
        if (isEmpty(blocks) || blockKeys == null || blockKeys.isEmpty()) {
            return EdgeBuild.EMPTY;
        }
        Map<EdgeKey, EdgeAccumulator> edges = new HashMap<>(Math.max(64, blocks.size() * 8));
        for (BlockPos pos : blocks) {
            if (pos != null) {
                addBlockSurfaceContributions(edges, pos.getX(), pos.getY(), pos.getZ(), blockKeys);
            }
        }
        return new EdgeBuild(edges, visibleEdgeLines(edges));
    }

    private static List<UltimineBlockMerger.EdgeLine> visibleEdgeLines(Map<EdgeKey, EdgeAccumulator> edgeMap) {
        if (edgeMap == null || edgeMap.isEmpty()) {
            return List.of();
        }
        List<UltimineBlockMerger.EdgeLine> result = new ArrayList<>(edgeMap.size());
        for (Map.Entry<EdgeKey, EdgeAccumulator> entry : edgeMap.entrySet()) {
            if (entry.getValue().isVisible()) {
                result.add(entry.getKey().toLine());
            }
        }
        return result;
    }

    private static void addBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z,
            Set<Long> blockKeys) {
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) {
            addFaceEdges(edges, x, y, z, FaceSide.EAST);
        }
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) {
            addFaceEdges(edges, x, y, z, FaceSide.WEST);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) {
            addFaceEdges(edges, x, y, z, FaceSide.UP);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) {
            addFaceEdges(edges, x, y, z, FaceSide.DOWN);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) {
            addFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) {
            addFaceEdges(edges, x, y, z, FaceSide.NORTH);
        }
    }

    private static void removeBlockSurfaceContributions(Map<EdgeKey, EdgeAccumulator> edges, BlockPos pos,
            Set<Long> blockKeys) {
        if (pos == null || !blockKeys.contains(pos.asLong())) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))) {
            removeFaceEdges(edges, x, y, z, FaceSide.EAST);
        }
        if (!blockKeys.contains(BlockPos.asLong(x - 1, y, z))) {
            removeFaceEdges(edges, x, y, z, FaceSide.WEST);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y + 1, z))) {
            removeFaceEdges(edges, x, y, z, FaceSide.UP);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y - 1, z))) {
            removeFaceEdges(edges, x, y, z, FaceSide.DOWN);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y, z + 1))) {
            removeFaceEdges(edges, x, y, z, FaceSide.SOUTH);
        }
        if (!blockKeys.contains(BlockPos.asLong(x, y, z - 1))) {
            removeFaceEdges(edges, x, y, z, FaceSide.NORTH);
        }
    }

    private static void addFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x;
        int x1 = x + 1;
        int y0 = y;
        int y1 = y + 1;
        int z0 = z;
        int z1 = z + 1;
        switch (side) {
            case EAST -> {
                addEdge(edges, x1, y0, z0, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x1, y0, z0, side);
            }
            case WEST -> {
                addEdge(edges, x0, y0, z0, x0, y0, z1, side);
                addEdge(edges, x0, y0, z1, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x0, y1, z0, side);
                addEdge(edges, x0, y1, z0, x0, y0, z0, side);
            }
            case UP -> {
                addEdge(edges, x0, y1, z0, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x0, y1, z0, side);
            }
            case DOWN -> {
                addEdge(edges, x0, y0, z0, x1, y0, z0, side);
                addEdge(edges, x1, y0, z0, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x0, y0, z1, side);
                addEdge(edges, x0, y0, z1, x0, y0, z0, side);
            }
            case SOUTH -> {
                addEdge(edges, x0, y0, z1, x1, y0, z1, side);
                addEdge(edges, x1, y0, z1, x1, y1, z1, side);
                addEdge(edges, x1, y1, z1, x0, y1, z1, side);
                addEdge(edges, x0, y1, z1, x0, y0, z1, side);
            }
            case NORTH -> {
                addEdge(edges, x0, y0, z0, x0, y1, z0, side);
                addEdge(edges, x0, y1, z0, x1, y1, z0, side);
                addEdge(edges, x1, y1, z0, x1, y0, z0, side);
                addEdge(edges, x1, y0, z0, x0, y0, z0, side);
            }
        }
    }

    private static void removeFaceEdges(Map<EdgeKey, EdgeAccumulator> edges, int x, int y, int z, FaceSide side) {
        int x0 = x;
        int x1 = x + 1;
        int y0 = y;
        int y1 = y + 1;
        int z0 = z;
        int z1 = z + 1;
        switch (side) {
            case EAST -> {
                removeEdge(edges, x1, y0, z0, x1, y1, z0, side);
                removeEdge(edges, x1, y1, z0, x1, y1, z1, side);
                removeEdge(edges, x1, y1, z1, x1, y0, z1, side);
                removeEdge(edges, x1, y0, z1, x1, y0, z0, side);
            }
            case WEST -> {
                removeEdge(edges, x0, y0, z0, x0, y0, z1, side);
                removeEdge(edges, x0, y0, z1, x0, y1, z1, side);
                removeEdge(edges, x0, y1, z1, x0, y1, z0, side);
                removeEdge(edges, x0, y1, z0, x0, y0, z0, side);
            }
            case UP -> {
                removeEdge(edges, x0, y1, z0, x0, y1, z1, side);
                removeEdge(edges, x0, y1, z1, x1, y1, z1, side);
                removeEdge(edges, x1, y1, z1, x1, y1, z0, side);
                removeEdge(edges, x1, y1, z0, x0, y1, z0, side);
            }
            case DOWN -> {
                removeEdge(edges, x0, y0, z0, x1, y0, z0, side);
                removeEdge(edges, x1, y0, z0, x1, y0, z1, side);
                removeEdge(edges, x1, y0, z1, x0, y0, z1, side);
                removeEdge(edges, x0, y0, z1, x0, y0, z0, side);
            }
            case SOUTH -> {
                removeEdge(edges, x0, y0, z1, x1, y0, z1, side);
                removeEdge(edges, x1, y0, z1, x1, y1, z1, side);
                removeEdge(edges, x1, y1, z1, x0, y1, z1, side);
                removeEdge(edges, x0, y1, z1, x0, y0, z1, side);
            }
            case NORTH -> {
                removeEdge(edges, x0, y0, z0, x0, y1, z0, side);
                removeEdge(edges, x0, y1, z0, x1, y1, z0, side);
                removeEdge(edges, x1, y1, z0, x1, y0, z0, side);
                removeEdge(edges, x1, y0, z0, x0, y0, z0, side);
            }
        }
    }

    private static void addEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2,
            FaceSide side) {
        edges.computeIfAbsent(EdgeKey.of(x1, y1, z1, x2, y2, z2), ignored -> new EdgeAccumulator()).add(side);
    }

    private static void removeEdge(Map<EdgeKey, EdgeAccumulator> edges, int x1, int y1, int z1, int x2, int y2, int z2,
            FaceSide side) {
        EdgeKey key = EdgeKey.of(x1, y1, z1, x2, y2, z2);
        EdgeAccumulator accumulator = edges.get(key);
        if (accumulator == null) {
            return;
        }
        accumulator.remove(side);
        if (accumulator.isEmpty()) {
            edges.remove(key);
        }
    }

    private static List<BlockPos> filterOuterBlocksFast(List<BlockPos> blocks, Set<Long> blockKeys) {
        if (isEmpty(blocks) || blockKeys == null || blockKeys.isEmpty()) {
            return List.of();
        }
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (!blockKeys.contains(BlockPos.asLong(x + 1, y, z))
                    || !blockKeys.contains(BlockPos.asLong(x - 1, y, z))
                    || !blockKeys.contains(BlockPos.asLong(x, y + 1, z))
                    || !blockKeys.contains(BlockPos.asLong(x, y - 1, z))
                    || !blockKeys.contains(BlockPos.asLong(x, y, z + 1))
                    || !blockKeys.contains(BlockPos.asLong(x, y, z - 1))) {
                outerBlocks.add(pos);
            }
        }
        return outerBlocks;
    }

    private static List<BlockPos> buildFillBlocks(List<BlockPos> blocks, Set<Long> remainingKeys) {
        if (isEmpty(blocks) || remainingKeys == null || remainingKeys.isEmpty()
                || remainingKeys.size() > MAX_MERGED_FILL_BLOCKS) {
            return List.of();
        }
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (pos != null && remainingKeys.contains(pos.asLong()) && hasMissingFaceNeighbour(pos, remainingKeys)) {
                outerBlocks.add(pos);
            }
        }
        return outerBlocks;
    }

    private static List<BlockPos> buildFillBlocks(Set<Long> remainingKeys) {
        if (remainingKeys == null || remainingKeys.isEmpty() || remainingKeys.size() > MAX_MERGED_FILL_BLOCKS) {
            return List.of();
        }
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (Long key : remainingKeys) {
            if (key == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(key);
            if (hasMissingFaceNeighbour(pos, remainingKeys)) {
                outerBlocks.add(pos);
            }
        }
        return outerBlocks;
    }

    private static boolean hasMissingFaceNeighbour(BlockPos pos, Set<Long> blockKeys) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return !blockKeys.contains(BlockPos.asLong(x + 1, y, z))
                || !blockKeys.contains(BlockPos.asLong(x - 1, y, z))
                || !blockKeys.contains(BlockPos.asLong(x, y + 1, z))
                || !blockKeys.contains(BlockPos.asLong(x, y - 1, z))
                || !blockKeys.contains(BlockPos.asLong(x, y, z + 1))
                || !blockKeys.contains(BlockPos.asLong(x, y, z - 1));
    }

    private static float getBreathFactor() {
        double timeSeconds = System.currentTimeMillis() / 1000.0D;
        double phase = timeSeconds * 0.2D * 2.0D * Math.PI;
        double sin = Math.sin(phase);
        return (float) ((sin + 1.0D) * 0.15D + 0.70D);
    }

    private static boolean isEmpty(List<BlockPos> blocks) {
        return blocks == null || blocks.isEmpty();
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static int channel(float value) {
        return Math.max(0, Math.min(255, Math.round(clamp01(value) * 255.0F)));
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
        }
    }

    private static final class MutableBounds {
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;
        private boolean hasAny;

        private void include(List<BlockPos> blocks) {
            if (blocks == null) {
                return;
            }
            for (BlockPos pos : blocks) {
                if (pos == null) {
                    continue;
                }
                this.minX = Math.min(this.minX, pos.getX());
                this.minY = Math.min(this.minY, pos.getY());
                this.minZ = Math.min(this.minZ, pos.getZ());
                this.maxX = Math.max(this.maxX, pos.getX());
                this.maxY = Math.max(this.maxY, pos.getY());
                this.maxZ = Math.max(this.maxZ, pos.getZ());
                this.hasAny = true;
            }
        }

        private Bounds toBounds() {
            return this.hasAny ? new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ) : null;
        }
    }

    private record EdgeBuild(Map<EdgeKey, EdgeAccumulator> edgeMap, List<UltimineBlockMerger.EdgeLine> visibleEdges) {
        private static final EdgeBuild EMPTY = new EdgeBuild(Map.of(), List.of());
    }

    private enum FaceSide {
        EAST,
        WEST,
        UP,
        DOWN,
        SOUTH,
        NORTH;

        private static final int COUNT = values().length;
    }

    private record EdgeKey(int x1, int y1, int z1, int x2, int y2, int z2) {
        private static EdgeKey of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (compareVertex(x1, y1, z1, x2, y2, z2) <= 0) {
                return new EdgeKey(x1, y1, z1, x2, y2, z2);
            }
            return new EdgeKey(x2, y2, z2, x1, y1, z1);
        }

        private static int compareVertex(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (x1 != x2) {
                return Integer.compare(x1, x2);
            }
            if (y1 != y2) {
                return Integer.compare(y1, y2);
            }
            return Integer.compare(z1, z2);
        }

        private UltimineBlockMerger.EdgeLine toLine() {
            return new UltimineBlockMerger.EdgeLine(this.x1, this.y1, this.z1, this.x2, this.y2, this.z2);
        }
    }

    private static final class EdgeAccumulator {
        private final int[] sideCounts = new int[FaceSide.COUNT];
        private int total;

        private void add(FaceSide side) {
            this.sideCounts[side.ordinal()]++;
            this.total++;
        }

        private void remove(FaceSide side) {
            int index = side.ordinal();
            if (this.sideCounts[index] <= 0) {
                return;
            }
            this.sideCounts[index]--;
            this.total--;
        }

        private boolean isEmpty() {
            return this.total <= 0;
        }

        private boolean isVisible() {
            return this.total == 1 || sideTypeCount() > 1;
        }

        private int sideTypeCount() {
            int count = 0;
            for (int sideCount : this.sideCounts) {
                if (sideCount > 0) {
                    count++;
                }
            }
            return count;
        }
    }

    private record CachedMergedSkeleton(
            ShapeDataRecords.GhostPreview preview,
            int key,
            int blockCount,
            boolean chainDestroyPreview,
            boolean confirmedWorkArea,
            Set<Long> remainingBlockKeys,
            Map<EdgeKey, EdgeAccumulator> edgeMap,
            List<UltimineBlockMerger.EdgeLine> edges,
            List<BlockPos> fillBlocks) {
        private static final CachedMergedSkeleton EMPTY = new CachedMergedSkeleton(
                null, 0, 0, false, false, Set.of(), Map.of(), List.of(), List.of());

        private boolean isEmpty() {
            return this.edges.isEmpty();
        }

        private boolean isSourceEmpty() {
            return this.preview == null || this.remainingBlockKeys.isEmpty();
        }

        private boolean matchesPreview(ShapeDataRecords.GhostPreview candidate) {
            return candidate != null && candidate == this.preview;
        }

        private boolean matchesKey(int candidateKey, int candidateBlockCount, boolean candidateChainDestroyPreview,
                boolean candidateConfirmedWorkArea) {
            return this.preview != null
                    && !isSourceEmpty()
                    && this.key == candidateKey
                    && this.blockCount == candidateBlockCount
                    && this.chainDestroyPreview == candidateChainDestroyPreview
                    && this.confirmedWorkArea == candidateConfirmedWorkArea;
        }

        private CachedMergedSkeleton withPreview(ShapeDataRecords.GhostPreview candidate) {
            return new CachedMergedSkeleton(candidate, this.key, this.blockCount, this.chainDestroyPreview,
                    this.confirmedWorkArea, this.remainingBlockKeys, this.edgeMap, this.edges, this.fillBlocks);
        }

        private CachedMergedSkeleton withRemaining(Set<Long> nextKeys, Map<EdgeKey, EdgeAccumulator> nextEdgeMap,
                List<UltimineBlockMerger.EdgeLine> nextEdges, List<BlockPos> nextFillBlocks) {
            return new CachedMergedSkeleton(this.preview, this.key, this.blockCount, this.chainDestroyPreview,
                    this.confirmedWorkArea, nextKeys, nextEdgeMap, nextEdges, nextFillBlocks);
        }
    }

    private record DestructiveCellColors(
            float lineR, float lineG, float lineB, float lineA,
            float fillR, float fillG, float fillB, float fillA) {
        private static DestructiveCellColors forConfirmState(boolean readyConfirm) {
            return new DestructiveCellColors(
                    1.00F,
                    readyConfirm ? 0.95F : 0.46F,
                    readyConfirm ? 0.45F : 0.64F,
                    readyConfirm ? 0.95F : 0.62F,
                    1.00F,
                    readyConfirm ? 0.72F : 0.25F,
                    readyConfirm ? 0.24F : 0.44F,
                    readyConfirm ? 0.22F : 0.07F);
        }
    }
}
