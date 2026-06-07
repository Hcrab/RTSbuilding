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
import java.util.HashSet;
import java.util.List;
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
        if (!(minecraft.screen instanceof BuilderScreen)) {
            return;
        }

        ShapeDataRecords.GhostPreview preview = ((BuilderScreen) minecraft.screen).getShapeGhostPreview();

        // Ultimine ghost always tries to render (it handles empty blocks internally);
        // skip early-exit for ultimine to avoid blocking its fallthrough logic.
        if (!preview.ultimineGhost() && preview.blocks().isEmpty() && preview.emptyBlocks().isEmpty()) {
            return;
        }

        if (com.rtsbuilding.rtsbuilding.Config.isWireframePreviewEnabled()) {
            renderWireframePreview(preview, poseStack, lineBuffer);
            return;
        }

        if (preview.ultimineGhost()) {
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
        boolean readyConfirm = preview.readyConfirm();

        // Envelope outline for non-breakable blocks (yellow markers in emptyBlocks region)
        if (!preview.emptyBlocks().isEmpty()) {
            float envLineR = 1.00F, envLineG = 0.86F, envLineB = 0.22F, envLineA = 0.72F;
            float envFillR = 1.00F, envFillG = 0.86F, envFillB = 0.18F, envFillA = 0.18F;
            renderGhostEnvelope(poseStack, lineBuffer, fillBuffer, preview.blocks(), preview.emptyBlocks(),
                    envLineR, envLineG, envLineB, envLineA,
                    envFillR, envFillG, envFillB, envFillA);
        }

        // Per-block cell highlight
        DestructiveCellColors dcc = DestructiveCellColors.forConfirmState(readyConfirm);
        float lineR = dcc.lineR, lineG = dcc.lineG, lineB = dcc.lineB, lineA = dcc.lineA;
        float fillR = dcc.fillR, fillG = dcc.fillG, fillB = dcc.fillB, fillA = dcc.fillA;

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

        // Step 4 — opaque depth-tested edges (visible only on front faces)
        renderUltiminePass1(edges, matrix, lineBuffer, r, g, b);

        // Step 5 — translucent no-depth edges (visible through geometry)
        renderUltiminePass2(edges, matrix, r, g, b);

        // Step 6 — very faint per-block fill
        renderUltimineFill(outerBlocks, poseStack, fillBuffer, r, g, b);
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
            float r, float g, float b) {
        BufferBuilder ndBuffer = new BufferBuilder(LINES_NO_DEPTH_BACKING, VertexFormat.Mode.LINES,
                DefaultVertexFormat.POSITION_COLOR_NORMAL);
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            ndBuffer.addVertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .setColor(r, g, b, 0.30F)
                    .setNormal(edge.xn(), edge.yn(), edge.zn());
            ndBuffer.addVertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .setColor(r, g, b, 0.30F)
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
            VertexConsumer fillBuffer, float r, float g, float b) {
        float fillA = 0.06F;
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

        // Envelope for non-breakable blocks in destruct mode
        if (destructive && !preview.emptyBlocks().isEmpty()) {
            float envLineR = 1.00F, envLineG = 0.86F, envLineB = 0.22F, envLineA = 0.72F;
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

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private static Bounds from(List<BlockPos> first, List<BlockPos> second) {
            MutableBounds bounds = new MutableBounds();
            bounds.include(first);
            bounds.include(second);
            return bounds.toBounds();
        }
    }

    /** Cell rendering colors for destructive ghost preview — grouped by confirm state. */
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
