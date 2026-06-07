package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders in-world ghost previews for shape building and destruction
 * modes within the {@link BuilderScreen}.
 *
 * <p><b>Rendering modes:</b>
 * <ol>
 *   <li><b>Build models</b> — transparent block-model overlay for shape
 *       placement (e.g. walls, floors).</li>
 *   <li><b>Build fallback</b> — coloured cell outlines when the block
 *       model cannot be resolved.</li>
 *   <li><b>Destructive (range destroy)</b> — per-block coloured outlines
 *       with an envelope around non-breakable blocks.</li>
 *   <li><b>Ultimine (chain mining)</b> — merged-outer-perimeter edges
 *       with two-pass rendering (FTB Ultimine style).</li>
 *   <li><b>Wireframe</b> — simplified per-block wireframes (debug/config
 *       toggle).</li>
 * </ol>
 *
 * <p>This class is purely static; it is never instantiated.
 */
public final class ShapeGhostRenderer {

    /** Alpha value applied to build-mode ghost block models. */
    static final float BUILD_GHOST_ALPHA = 0.8F;

    /** Boundary padding applied around shape bounding boxes. */
    private static final double BOUNDARY_PADDING = 0.02D;

    // ──────────────────────────────────────────────
    //  Ultimine (chain-mining) rendering resources
    // ──────────────────────────────────────────────

    /**
     * Custom {@link RenderType} for translucent lines drawn without depth
     * testing, so they remain visible through world geometry.
     *
     * <p>Inspired by FTB Ultimine's
     * {@code UltimineRenderTypes.LINES_NO_DEPTH_TRANSLUCENT}. Used as the
     * translucent pass of the two-pass ultimine edge rendering:
     * <ul>
     *   <li><b>Pass 1</b> — opaque lines with depth test (standard
     *       {@code RenderType.lines()})</li>
     *   <li><b>Pass 2</b> — translucent lines without depth test
     *       (this {@link RenderType})</li>
     * </ul>
     */
    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "rtsbuilding_ultimine_lines_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            512,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Backing {@link ByteBufferBuilder} for the no-depth line render type. */
    private static final ByteBufferBuilder LINES_NO_DEPTH_BACKING = new ByteBufferBuilder(LINES_NO_DEPTH.bufferSize());

    private static float smoothedDestroyProgress;
    private static long smoothedDestroyProgressMs;
    private static int smoothedDestroyProgressKey;
    private static CachedMergedSkeleton cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
    private static final Set<Long> PENDING_DESTROYED_BLOCK_KEYS = new HashSet<>();
    private static final int MAX_MERGED_NO_DEPTH_EDGES = 4096;
    private static final int MAX_MERGED_FILL_BLOCKS = 768;

    /** Private constructor to prevent instantiation. */
    private ShapeGhostRenderer() {
    }

    /**
     * Entry-point that delegates to the appropriate rendering method based on
     * the current {@link ShapeDataRecords.GhostPreview} state.
     *
     * <p>Dispatch order:
     * <ol>
     *   <li><b>Wireframe</b> — config-controlled debug mode, takes priority.</li>
     *   <li><b>Ultimine</b> — chain-mining ghost (FTB Ultimine style).</li>
     *   <li><b>Destructive</b> — range-destroy ghost with per-block outlines.</li>
     *   <li><b>Build models / fallback</b> — shape-placement ghost preview.</li>
     * </ol>
     *
     * @param minecraft  Minecraft client instance
     * @param poseStack  transformation stack (already translated to camera space)
     * @param lineBuffer vertex consumer for line geometry (depth-tested)
     * @param fillBuffer vertex consumer for translucent fill geometry
     */
    public static void renderShapeGhostPreview(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        // Only render when BuilderScreen is active
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            return;
        }

        boolean sawConfirmedDestructiveWorkArea = false;
        for (ShapeDataRecords.GhostPreview preview : screen.getConfirmedRangeDestroyPreviews()) {
            sawConfirmedDestructiveWorkArea |= isConfirmedDestructiveWorkArea(preview);
            renderGhostPreview(minecraft, preview, poseStack, lineBuffer, fillBuffer);
        }
        ShapeDataRecords.GhostPreview currentPreview = screen.getShapeGhostPreview();
        sawConfirmedDestructiveWorkArea |= isConfirmedDestructiveWorkArea(currentPreview);
        renderGhostPreview(minecraft, currentPreview, poseStack, lineBuffer, fillBuffer);
        if (!sawConfirmedDestructiveWorkArea) {
            cachedMergedSkeleton = CachedMergedSkeleton.EMPTY;
            PENDING_DESTROYED_BLOCK_KEYS.clear();
        }
    }

    public static void markDestroyed(BlockPos pos) {
        if (pos != null) {
            PENDING_DESTROYED_BLOCK_KEYS.add(pos.asLong());
        }
    }

    private static void renderGhostPreview(Minecraft minecraft, ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        if (preview == null) {
            return;
        }
        // Ultimine ghost always tries to render (it handles empty blocks internally);
        // skip early-exit for ultimine to avoid blocking its fallthrough logic.
        if (!preview.chainDestroyPreview() && preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty()) {
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

        if (com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled()) {
            renderWireframePreview(preview, poseStack, lineBuffer);
            return;
        }

        if (preview.chainDestroyPreview()) {
            renderUltimineGhost(preview, poseStack, lineBuffer, fillBuffer);
            return;
        }

        if (preview.destructive()) {
            renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer);
            return;
        }

        // Build mode — try transparent block models, fall back to cell outlines
        BlockState blockState = resolveBuildBlockState(minecraft);
        if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
            renderBuildGhostModels(minecraft, preview, poseStack, blockState, lineBuffer);
        } else {
            renderBuildGhostFallback(preview, poseStack, lineBuffer, fillBuffer);
        }
    }

    /**
     * Resolves the {@link BlockState} used for rendering build-mode ghost block models.
     *
     * <p>Priority order:
     * <ol>
     *   <li>Item selected in the RTS storage panel</li>
     *   <li>Player's main hand item</li>
     * </ol>
     *
     * @return the block state, or {@code null} if neither source yields a {@link BlockItem}
     */
    private static BlockState resolveBuildBlockState(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    /**
     * Renders build-mode ghost previews using transparent block models.
     *
     * <p>Each block position is rendered with the actual block model at a fixed alpha
     * ({@value #BUILD_GHOST_ALPHA}), then an overall bounding-box outline is drawn.
     */
    private static void renderBuildGhostModels(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, BlockState blockState, VertexConsumer lineBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }

        // Compute the overall bounding box extent
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : blocks) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }

        // Render translucent block model for each position (with position-specific lighting)
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, BUILD_GHOST_ALPHA);

        for (BlockPos pos : blocks) {
            int light = LevelRenderer.getLightColor(minecraft.level, pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(
                    blockState,
                    poseStack,
                    translucentBuffer,
                    light,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        blockBuffer.endBatch();

        // Render overall bounding-box outline (green tones when readyConfirm, cyan otherwise)
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX - BOUNDARY_PADDING, minY - BOUNDARY_PADDING, minZ - BOUNDARY_PADDING,
                maxX + BOUNDARY_PADDING, maxY + BOUNDARY_PADDING, maxZ + BOUNDARY_PADDING,
                lineR, lineG, lineB,
                0.95F);
    }

    /**
     * Fallback rendering for build-mode ghosts: coloured cell outlines used when the
     * block state cannot be resolved (e.g. non-block item or air).
     */
    private static void renderBuildGhostFallback(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
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
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fillR, fillG, fillB, fillA);

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    lineR, lineG, lineB,
                    0.95F);
        }
    }

    /**
     * Renders the destructive (range-destroy) ghost preview: per-block coloured
     * outlines with an envelope around non-breakable blocks (emptyBlocks).
     */
    private static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer,
                preview.confirmedWorkArea() ? smoothedDestroyProgress(ClientRtsController.get(), preview) : 0.0F,
                1.0F);
    }

    private static void renderDestructiveGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float alphaMultiplier) {
        boolean readyConfirm = preview.readyConfirm();
        float alpha = clamp01(alphaMultiplier);
        if (alpha <= 0.0F) {
            return;
        }

        // Keep a total yellow envelope visible even when the selected shape contains no air cells.
        if (!isEmpty(preview.blocks()) || !isEmpty(preview.emptyBlocks())) {
            float envLineR = lerp(1.00F, 0.38F, progress);
            float envLineG = lerp(0.86F, 1.00F, progress);
            float envLineB = lerp(0.22F, 0.42F, progress);
            float envLineA = 0.78F * alpha;
            float envFillR = lerp(1.00F, 0.30F, progress);
            float envFillG = lerp(0.86F, 0.95F, progress);
            float envFillB = lerp(0.18F, 0.36F, progress);
            float envFillA = 0.10F * alpha;
            renderGhostEnvelope(poseStack, lineBuffer, fillBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, envLineA,
                    envFillR, envFillG, envFillB, envFillA);
        }

        // Per-block cell highlight
        DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
        float lineR = lerp(dcc.lineR, 0.38F, progress);
        float lineG = lerp(dcc.lineG, 1.00F, progress);
        float lineB = lerp(dcc.lineB, 0.42F, progress);
        float lineA = dcc.lineA * alpha;
        float fillR = lerp(dcc.fillR, 0.30F, progress);
        float fillG = lerp(dcc.fillG, 0.95F, progress);
        float fillB = lerp(dcc.fillB, 0.36F, progress);
        float fillA = dcc.fillA * alpha;

        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }
        for (BlockPos pos : blocks) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;

            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    fillR, fillG, fillB, fillA);

            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    cellMinX, cellMinY, cellMinZ,
                    cellMaxX, cellMaxY, cellMaxZ,
                    lineR, lineG, lineB,
                    lineA);
        }
    }

    private static boolean isConfirmedDestructiveWorkArea(ShapeDataRecords.GhostPreview preview) {
        return preview != null && preview.destructive() && preview.confirmedWorkArea();
    }

    private static void renderConfirmedRangeDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        ClientRtsController controller = ClientRtsController.get();
        if (hasStartedDestroyBatch(controller, preview)) {
            renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer, 1.0F, 0.30F, 0.030F);
            return;
        }
        if (cachedMergedSkeleton.matchesPreview(preview)) {
            CachedMergedSkeleton skeleton = getCachedMergedSkeleton(preview);
            if (!skeleton.isEmpty()) {
                renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer, 1.0F, 0.30F, 0.030F);
            }
            return;
        }
        renderDestructiveGhost(preview, poseStack, lineBuffer, fillBuffer,
                smoothedDestroyProgress(controller, preview), 1.0F);
    }

    /**
     * Renders the confirmed connected-destroy work area after the player has
     * started mining. Range Destroy intentionally does not use this path; its
     * per-cell helper grid remains visible and fades with the outer envelope.
     */
    private static void renderConfirmedDestroyWorkArea(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
        renderMergedDestroySkeleton(preview, poseStack, lineBuffer, fillBuffer, progress, 0.30F, 0.035F);
    }

    private static void renderMergedDestroySkeleton(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        CachedMergedSkeleton skeleton = getCachedMergedSkeleton(preview);
        if (skeleton.isEmpty()) {
            return;
        }
        renderMergedDestroySkeleton(skeleton, poseStack, lineBuffer, fillBuffer, progress, noDepthAlpha, fillAlpha);
    }

    private static void renderMergedDestroySkeleton(CachedMergedSkeleton skeleton, PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer fillBuffer, float progress, float noDepthAlpha, float fillAlpha) {
        List<UltimineBlockMerger.EdgeLine> edges = skeleton.edges();
        if (edges.isEmpty()) {
            return;
        }

        Matrix4f matrix = poseStack.last().pose();
        float edgeR = lerp(1.00F, 0.38F, progress);
        float edgeG = lerp(0.86F, 1.00F, progress);
        float edgeB = lerp(0.22F, 0.42F, progress);
        renderUltiminePass1(edges, matrix, lineBuffer, edgeR, edgeG, edgeB);
        if (edges.size() <= MAX_MERGED_NO_DEPTH_EDGES) {
            renderUltiminePass2(edges, matrix, edgeR, edgeG, edgeB, noDepthAlpha);
        }

        // A tiny fill keeps the work area readable from high RTS camera angles without recreating the old yellow clutter.
        if (skeleton.fillBlocks().size() <= MAX_MERGED_FILL_BLOCKS) {
            renderUltimineFill(skeleton.fillBlocks(), poseStack, fillBuffer, edgeR, edgeG, edgeB, fillAlpha);
        }
    }

    private static CachedMergedSkeleton getCachedMergedSkeleton(ShapeDataRecords.GhostPreview preview) {
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
        if (blocks == null || blocks.isEmpty()) {
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
                remainingBlocks,
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

    private static void addNewlyExposedNeighbourContributions(Map<EdgeKey, EdgeAccumulator> edges, BlockPos removedPos,
            Set<Long> remainingKeys) {
        int x = removedPos.getX();
        int y = removedPos.getY();
        int z = removedPos.getZ();
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

    private static List<BlockPos> buildFillBlocks(List<BlockPos> blocks, Set<Long> remainingKeys) {
        if (blocks == null || blocks.isEmpty() || remainingKeys == null || remainingKeys.isEmpty()
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

    private static CachedMergedSkeleton rebuildMergedSkeleton(CachedMergedSkeleton source, List<BlockPos> remainingBlocks,
            Set<Long> remainingKeys) {
        if (remainingBlocks == null || remainingBlocks.isEmpty()) {
            return source.withRemaining(List.of(), Set.of(), Map.of(), List.of(), List.of());
        }
        EdgeBuild edgeBuild = buildFastSurfaceEdgeBuild(remainingBlocks, remainingKeys);
        List<BlockPos> fillBlocks = buildFillBlocks(remainingBlocks, remainingKeys);
        return source.withRemaining(
                List.copyOf(remainingBlocks),
                Set.copyOf(remainingKeys),
                edgeBuild.edgeMap(),
                List.copyOf(edgeBuild.visibleEdges()),
                List.copyOf(fillBlocks));
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

    /**
     * Fast dynamic edge builder for confirmed destroy batches.
     *
     * <p>This intentionally avoids {@link VoxelShape} boolean merging. The
     * confirmed batch changes repeatedly as the server confirms broken blocks,
     * so the hot path needs a cheap voxel-surface update: for each remaining
     * block, emit the four edges of faces whose neighbour is no longer in the
     * remaining target set. Duplicate face edges are removed by an integer
     * endpoint key.
     */
    private static EdgeBuild buildFastSurfaceEdgeBuild(List<BlockPos> blocks, Set<Long> blockKeys) {
        if (blocks == null || blocks.isEmpty() || blockKeys == null || blockKeys.isEmpty()) {
            return EdgeBuild.EMPTY;
        }
        Map<EdgeKey, EdgeAccumulator> edges = new HashMap<>(Math.max(64, blocks.size() * 8));
        for (BlockPos pos : blocks) {
            if (pos == null) {
                continue;
            }
            addBlockSurfaceContributions(edges, pos.getX(), pos.getY(), pos.getZ(), blockKeys);
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
        if (blocks == null || blocks.isEmpty() || blockKeys == null || blockKeys.isEmpty()) {
            return List.of();
        }
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (pos == null) {
                continue;
            }
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

    // ──────────────────────────────────────────────
    //  Ultimine ghost — FTB Ultimine style
    // ──────────────────────────────────────────────

    /**
     * Renders the chain-mining (ultimine) ghost preview in FTB Ultimine style.
     *
     * <p><b>Pipeline:</b>
     * <ol>
     *   <li><b>Outer-perimeter filtering</b> — keeps only blocks that have at
     *       least one face-adjacent neighbour outside the selection.</li>
     *   <li><b>AABB merge + edge extraction</b> — adjacent outer blocks are
     *       merged into larger AABBs; then every outer edge is extracted
     *       via {@link UltimineBlockMerger#getEdgeLines}.</li>
     *   <li><b>Breathing colour</b> — gold (1.0, 0.72, 0.24) oscillates
     *       between full brightness and 70 % via a sine wave.</li>
     *   <li><b>Pass 1 (depth-tested)</b> — opaque edges via
     *       {@code RenderType.lines()}.</li>
     *   <li><b>Pass 2 (no-depth translucent)</b> — same edges without depth
     *       test, visible through occluding geometry.</li>
     *   <li><b>Faint per-block fill</b> — alpha-6 % box for each outer block.</li>
     * </ol>
     */
    private static void renderUltimineGhost(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        // Step 1 — keep only blocks on the outer perimeter of the selection
        List<BlockPos> outerBlocks = filterOuterBlocks(blocks);
        if (outerBlocks.isEmpty()) {
            return;
        }

        // Step 2 — merge adjacent outer blocks and extract edge segments
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) {
            return;
        }

        Matrix4f matrix = poseStack.last().pose();

        // Step 3 — sinusoidal breathing animation on the gold colour
        float breathFactor = getBreathFactor();
        float baseR = 1.00F, baseG = 0.72F, baseB = 0.24F;
        float r = baseR * breathFactor;
        float g = baseG * breathFactor;
        float b = baseB * breathFactor;
        float progress = smoothedDestroyProgress(ClientRtsController.get(), preview);
        float edgeR = lerp(r, 0.38F, progress);
        float edgeG = lerp(g, 1.00F, progress);
        float edgeB = lerp(b, 0.42F, progress);

        // Step 4 — opaque depth-tested edges (visible only on front faces)
        renderUltiminePass1(edges, matrix, lineBuffer, edgeR, edgeG, edgeB);

        // Step 5 — translucent no-depth edges (visible through geometry)
        renderUltiminePass2(edges, matrix, edgeR, edgeG, edgeB, 0.34F);

        // Step 6 — very faint per-block fill
        renderUltimineFill(outerBlocks, poseStack, fillBuffer, edgeR, edgeG, edgeB, 0.08F);
    }

    /**
     * Oscillation frequency of the breathing colour pulse (Hz).
     * A complete bright → dim → bright cycle takes 1 / {@value #BREATH_SPEED} s.
     */
    private static final float BREATH_SPEED = 0.2F;

    /**
     * Minimum brightness multiplier applied during the breathing cycle.
     * The factor oscillates in [ {@value #BREATH_MIN_FACTOR}, 1.0 ].
     */
    private static final float BREATH_MIN_FACTOR = 0.7F;

    /**
     * Computes a time-varying scalar in [{@link #BREATH_MIN_FACTOR}, 1.0] that
     * oscillates sinusoidally at {@link #BREATH_SPEED} Hz.
     *
     * <p>Multiplying colour channels by this factor produces a gentle pulsing
     * effect (identical to the breathing animation in
     * {@code InteractionTargetRenderer}).
     *
     * @return the current breath factor, always 0.7 … 1.0
     */
    private static float getBreathFactor() {
        double timeSeconds = System.currentTimeMillis() / 1000.0D;
        double phase = timeSeconds * BREATH_SPEED * 2.0D * Math.PI;
        double sin = Math.sin(phase);
        // Map sin ∈ [-1, 1] → factor ∈ [BREATH_MIN_FACTOR, 1.0]
        return (float) ((sin + 1.0D) * 0.5D * (1.0F - BREATH_MIN_FACTOR) + BREATH_MIN_FACTOR);
    }

    // ──────────────────────────────────────────────
    //  Outer-perimeter block filtering
    // ──────────────────────────────────────────────

    /**
     * Filters the block list to retain only those on the outer perimeter of
     * the selection — blocks that have at least one face-adjacent neighbour
     * <em>outside</em> the selection set.
     *
     * <p>Internal blocks (all six face neighbours present in the selection)
     * are excluded, as their edges would be hidden inside the merged volume.
     *
     * @param blocks all block positions in the ultimine selection
     * @return the subset of blocks on the outer perimeter
     */
    private static List<BlockPos> filterOuterBlocks(List<BlockPos> blocks) {
        Set<BlockPos> allBlocks = new HashSet<>(blocks);
        BlockPos[] faceOffsets = {
                new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0),
                new BlockPos(0, 1, 0), new BlockPos(0, -1, 0),
                new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        };
        List<BlockPos> outerBlocks = new ArrayList<>();
        for (BlockPos pos : blocks) {
            boolean isOuter = false;
            for (BlockPos offset : faceOffsets) {
                if (!allBlocks.contains(pos.offset(offset))) {
                    isOuter = true;
                    break;
                }
            }
            if (isOuter) {
                outerBlocks.add(pos);
            }
        }
        return outerBlocks;
    }

    // ──────────────────────────────────────────────
    //  Edge line rendering — two-pass
    // ──────────────────────────────────────────────

    /**
     * <b>Pass 1:</b> renders solid, depth-tested edge lines using the standard
     * {@code RenderType.lines()} vertex consumer. Edges are visible only on
     * front faces of the merged geometry.
     *
     * @param edges      edge segments to render
     * @param matrix     current model-view-projection matrix
     * @param lineBuffer vertex consumer for depth-tested lines
     * @param r          red   channel (0-1), modulated by breath factor
     * @param g          green channel (0-1)
     * @param b          blue  channel (0-1)
     */
    private static void renderUltiminePass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b) {
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            lineBuffer.addVertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .setColor(r, g, b, 0.95F)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
            lineBuffer.addVertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .setColor(r, g, b, 0.95F)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
        }
    }

    /**
     * <b>Pass 2:</b> renders translucent edge lines with depth testing
     * disabled, so they remain visible even when occluded by world geometry.
     *
     * <p>Uses a manual {@link BufferBuilder} backed by
     * {@link #LINES_NO_DEPTH_BACKING} and submits the mesh directly via
     * {@link RenderType#draw}, bypassing the buffer-source pipeline which
     * does not flush reliably in the
     * {@code RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS} context.
     *
     * @param edges  edge segments to render
     * @param matrix current model-view-projection matrix
     * @param r      red   channel (0-1), modulated by breath factor
     * @param g      green channel (0-1)
     * @param b      blue  channel (0-1)
     */
    private static void renderUltiminePass2(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            float r, float g, float b, float alpha) {
        BufferBuilder ndBuffer = new BufferBuilder(LINES_NO_DEPTH_BACKING, VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            ndBuffer.addVertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .setColor(r, g, b, alpha)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
            ndBuffer.addVertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .setColor(r, g, b, alpha)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
        }
        MeshData meshData = ndBuffer.build();
        if (meshData != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            LINES_NO_DEPTH.draw(meshData);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    // ──────────────────────────────────────────────
    //  Per-block faint fill
    // ──────────────────────────────────────────────

    /**
     * Renders a very translucent filled box (alpha 6 %) for each outer
     * block, indicating the extent of each block that will be chain-mined.
     *
     * @param outerBlocks outer-perimeter block positions
     * @param poseStack   transformation stack
     * @param fillBuffer  vertex consumer for translucent fill quads
     * @param r           red   channel (0-1), modulated by breath factor
     * @param g           green channel (0-1)
     * @param b           blue  channel (0-1)
     */
    private static void renderUltimineFill(List<BlockPos> outerBlocks, PoseStack poseStack,
            VertexConsumer fillBuffer, float r, float g, float b, float fillA) {
        for (BlockPos pos : outerBlocks) {
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, b, fillA);
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

        double padding = BOUNDARY_PADDING;
        double minX = bounds.minX() - padding;
        double minY = bounds.minY() - padding;
        double minZ = bounds.minZ() - padding;
        double maxX = bounds.maxX() + 1.0D + padding;
        double maxY = bounds.maxY() + 1.0D + padding;
        double maxZ = bounds.maxZ() + 1.0D + padding;

        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                fillR, fillG, fillB, fillA);

        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                lineR, lineG, lineB,
                lineA);
    }

    private static void renderWireframePreview(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        boolean destructive = preview.destructive();
        boolean readyConfirm = preview.readyConfirm();
        float progress = preview.confirmedWorkArea() ? smoothedDestroyProgress(ClientRtsController.get(), preview) : 0.0F;

        // Envelope for the whole destruct region.
        if (destructive && (!isEmpty(preview.blocks()) || !isEmpty(preview.emptyBlocks()))) {
            float envLineR = lerp(1.00F, 0.38F, progress);
            float envLineG = lerp(0.86F, 1.00F, progress);
            float envLineB = lerp(0.22F, 0.42F, progress);
            float envLineA = 0.78F;
            renderWireframeEnvelope(poseStack, lineBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, envLineA);
        }

        if (preview.blocks() == null || preview.blocks().isEmpty()) {
            return;
        }

        // Cell line boxes
        for (BlockPos pos : preview.blocks()) {
            double cellMinX = pos.getX() + 0.03D;
            double cellMinY = pos.getY() + 0.03D;
            double cellMinZ = pos.getZ() + 0.03D;
            double cellMaxX = pos.getX() + 0.97D;
            double cellMaxY = pos.getY() + 0.97D;
            double cellMaxZ = pos.getZ() + 0.97D;
            if (destructive) {
                DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        dcc.lineR(), dcc.lineG(), dcc.lineB(), dcc.lineA());
            } else {
                float lineR = readyConfirm ? 0.45F : 0.30F;
                float lineG = readyConfirm ? 0.95F : 0.75F;
                float lineB = readyConfirm ? 0.45F : 1.00F;
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        cellMinX, cellMinY, cellMinZ,
                        cellMaxX, cellMaxY, cellMaxZ,
                        lineR, lineG, lineB, 0.95F);
            }
        }
    }

    private static void renderWireframeEnvelope(PoseStack poseStack, VertexConsumer lineBuffer,
            List<BlockPos> primaryBlocks, List<BlockPos> envelopeBlocks,
            float lineR, float lineG, float lineB, float lineA) {
        Bounds bounds = Bounds.from(primaryBlocks, envelopeBlocks);
        if (bounds == null) return;
        double padding = BOUNDARY_PADDING;
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                bounds.minX() - padding, bounds.minY() - padding, bounds.minZ() - padding,
                bounds.maxX() + 1.0D + padding, bounds.maxY() + 1.0D + padding, bounds.maxZ() + 1.0D + padding,
                lineR, lineG, lineB, lineA);
    }

    private static float smoothedDestroyProgress(ClientRtsController controller, ShapeDataRecords.GhostPreview preview) {
        int previewKey = previewKey(preview);
        float target = rawDestroyProgress(controller, preview);
        long now = System.currentTimeMillis();
        if (previewKey != smoothedDestroyProgressKey) {
            smoothedDestroyProgressKey = previewKey;
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return smoothedDestroyProgress;
        }
        if (smoothedDestroyProgressMs <= 0L) {
            smoothedDestroyProgressMs = now;
            smoothedDestroyProgress = target;
            return smoothedDestroyProgress;
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
        if (preview == null || pos == null) {
            return false;
        }
        return contains(preview.blocks(), pos) || contains(preview.emptyBlocks(), pos);
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

    private static boolean isEmpty(List<BlockPos> blocks) {
        return blocks == null || blocks.isEmpty();
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * clamp01(amount);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
        }
    }

    /** Cell rendering colors for destructive ghost preview — grouped by confirm state. */
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
            List<BlockPos> remainingBlocks,
            Set<Long> remainingBlockKeys,
            Map<EdgeKey, EdgeAccumulator> edgeMap,
            List<UltimineBlockMerger.EdgeLine> edges,
            List<BlockPos> fillBlocks) {
        private static final CachedMergedSkeleton EMPTY = new CachedMergedSkeleton(
                null, 0, 0, false, false, List.of(), Set.of(), Map.of(), List.of(), List.of());

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
                    readyConfirm ? 0.22F : 0.07F
            );
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
            if (blocks == null || blocks.isEmpty()) {
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
            if (!this.hasAny) {
                return null;
            }
            return new Bounds(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
        }
    }

    /**
     * Applies a fixed alpha multiplier to block model rendering.
     * Routes all render types through the translucent layer with an alpha override.
     */
    record GhostAlphaBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType renderType) {
            return new GhostAlphaVertexConsumer(delegate.getBuffer(RenderType.translucent()), alpha);
        }
    }

    record GhostAlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(red, green, blue, Math.round(alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
