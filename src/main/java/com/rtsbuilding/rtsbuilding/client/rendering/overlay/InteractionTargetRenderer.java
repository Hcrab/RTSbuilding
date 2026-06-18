package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Renders 3D corner-bracket highlights around the block or entity the player is currently
 * hovering over. The highlight uses thickened quad-based brackets (rather than thin GL_LINES)
 * so it remains visible at a distance and avoids z-fighting with world geometry.
 *
 * <p><b>Rendering pipeline</b>
 * <ol>
 *   <li>Early-exit checks: rotation-capture, null world/camera, UI occlusion</li>
 *   <li>Ray-cast both blocks and entities, pick the nearest hit</li>
 *   <li>Compute the world-space bounding box (respecting multi-block structures)</li>
 *   <li>Draw thickened corner brackets with a breathing colour animation</li>
 *   <li>Render a translucent coloured fog layer on the selected block face</li>
 * </ol>
 *
 * <p>All methods are static; this class is never instantiated.
 */
public final class InteractionTargetRenderer {

    // ──────────────────────────────────────────────
    //  Constants – Geometry
    // ──────────────────────────────────────────────

    /** Extra inflation applied to entity bounding boxes so the brackets sit outside the model. */
    private static final double INFLATE = 0.03D;

    /** Small outward offset applied to block brackets to prevent z-fighting. */
    private static final double LINE_OFFSET = 0.01D;

    /** Distance at which a targeted block should read fully as the old bright skeleton. */
    private static final double NEAR_SKELETON_DISTANCE = 10.0D;

    /** Distance at which a targeted block should keep the current face-cover clarity. */
    private static final double FAR_COVER_DISTANCE = 20.0D;

    /** Alpha (opacity) of the hit-face fog layer when the camera is close to the target. */
    private static final float FACE_FOG_ALPHA_NEAR = 0.045F;

    /** Alpha (opacity) of the hit-face fog layer when the camera is far from the target. */
    private static final float FACE_FOG_ALPHA_FAR = 0.5F;

    /** Small outward offset applied to the hit-face quad to prevent z-fighting. */
    private static final double FACE_FOG_OFFSET = 0.005D;

    private static final float NO_DEPTH_BRACKET_ALPHA = 0.32F;
    private static final float NO_DEPTH_FACE_FOG_ALPHA_NEAR = 0.025F;
    private static final float NO_DEPTH_FACE_FOG_ALPHA_FAR = 0.18F;

    // ──────────────────────────────────────────────
    //  Constants – Animation
    // ──────────────────────────────────────────────

    /** Frequency of the breathing colour oscillation, in cycles per second. */
    private static final float BREATH_SPEED = 0.2F;

    /** Minimum multiplier applied to each colour channel during the breathing cycle (0…1). */
    private static final float BREATH_MIN_FACTOR = 0.7F;

    // ──────────────────────────────────────────────
    //  Constants – Colours (RGB, no alpha)
    // ──────────────────────────────────────────────

    /** Colour used for entity corner brackets, modulated by the breath factor. */
    private static final float ENTITY_COLOR_R = 0.50F;
    private static final float ENTITY_COLOR_G = 0.80F;
    private static final float ENTITY_COLOR_B = 1.00F;

    /** Colour used for block corner brackets (orange-gold), modulated by the breath factor. */
    private static final float BLOCK_COLOR_R = 0.965F;
    private static final float BLOCK_COLOR_G = 0.608F;
    private static final float BLOCK_COLOR_B = 0.192F;

    /** Close-range block highlight colour: brighter yellow, matching the older skeleton read. */
    private static final float NEAR_BLOCK_COLOR_R = 1.000F;
    private static final float NEAR_BLOCK_COLOR_G = 0.900F;
    private static final float NEAR_BLOCK_COLOR_B = 0.130F;

    /** Maximum ray-cast range for cursor-based hit-testing. */
    private static final double MAX_REACH = 128.0D;

    // ── Custom no-depth bracket render type ──

    private static final RenderType BRACKET_NO_DEPTH = RenderType.create(
            "rtsbuilding_target_brackets_no_depth",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final ByteBufferBuilder BRACKET_NO_DEPTH_BACKING = new ByteBufferBuilder(BRACKET_NO_DEPTH.bufferSize());

    // ── Custom no-depth face-fog render type ──

    private static final RenderType FACE_FOG_NO_DEPTH = RenderType.create(
            "rtsbuilding_face_fog_no_depth",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final ByteBufferBuilder FACE_FOG_NO_DEPTH_BACKING = new ByteBufferBuilder(FACE_FOG_NO_DEPTH.bufferSize());

    // ──────────────────────────────────────────────
    //  Internal helpers
    // ──────────────────────────────────────────────

    /** Utility constructor; class is never instantiated. */
    private InteractionTargetRenderer() {
    }

    // ══════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════

    /**
     * Entry-point called every frame by {@code RtsVisualOverlayRenderer}.
     * Performs all early-exit checks, ray-casts against blocks and entities, and
     * draws corner-bracket highlights for the nearest target.
     *
     * @param minecraft   the Minecraft client instance
     * @param controller  the client-side RTS controller (used for rotation-capture check)
     * @param poseStack   current transformation stack (already translated to camera space)
     * @param lineBuffer  {@link VertexConsumer} for normal depth-tested target geometry
     * @param noDepthBuffer {@link VertexConsumer} for the final no-depth visibility backstop
     */
    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer noDepthBuffer) {
        // ── Fast early-exit checks ──
        if (controller.isRotateCaptured() || minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        // ── Skip rendering when the cursor is over BuilderScreen UI elements ──
        if (isInteractionBlockedByUI(minecraft)) {
            return;
        }

        // ── Compute breathing colour factor ──
        float breathFactor = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);

        // ── Build ray-cast vectors ──
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = RaycastHelper.computeCursorRayDirection(minecraft);
        Vec3 rayEnd = camPos.add(viewDir.scale(MAX_REACH));

        // ── Ray-cast blocks and entities ──
        BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursor(minecraft, camPos, rayEnd, false);
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(minecraft, camPos, rayEnd, viewDir, MAX_REACH);

        // ── Pick the nearest hit ──
        double blockDistSq = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDistSq = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (entityHit != null && entityDistSq <= blockDistSq) {
            // Entity is closer (or equal) – render entity highlight
            Entity entity = entityHit.getEntity();
            // Skip entity outside RTS build boundary
            if (isWithinBounds(controller, entity.blockPosition())) {
                double distance = camPos.distanceTo(entity.getBoundingBox().getCenter());
                renderEntityCornerHighlight(poseStack, lineBuffer, noDepthBuffer, entity, distance, breathFactor);
            }
            return;
        }

        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // Block is the nearest target – render block highlight
        BlockPos pos = blockHit.getBlockPos();
        // Skip block outside RTS build boundary
        if (!isWithinBounds(controller, pos)) {
            return;
        }
        double distance = camPos.distanceTo(Vec3.atCenterOf(pos));
        renderBlockCornerHighlight(minecraft, poseStack, lineBuffer, noDepthBuffer, pos, blockHit.getDirection(), distance, breathFactor);
    }

    // ══════════════════════════════════════════════
    //  GUI Interaction Check
    // ══════════════════════════════════════════════

    /**
     * Returns {@code true} when the cursor is over a BuilderScreen UI element that should
     * prevent world-target highlighting (e.g. floating windows, shape-build confirmation,
     * chain-ultimine mode).
     *
     * @param minecraft  the Minecraft client instance
     * @return {@code true} if world highlighting should be suppressed
     */
    private static boolean isInteractionBlockedByUI(Minecraft minecraft) {
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return false;
        }

        // Compute GUI-space cursor coordinates
        var mcWindow = minecraft.getWindow();
        double mouseX = minecraft.mouseHandler.xpos() * mcWindow.getGuiScaledWidth() / (double) mcWindow.getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * mcWindow.getGuiScaledHeight() / (double) mcWindow.getScreenHeight();

        // Blocked when cursor is outside the world-view area
        if (!builderScreen.isWorldArea(mouseX, mouseY)) {
            return true;
        }

        // Blocked when cursor is over an open floating window
        for (var window : builderScreen.getFloatingWindowLayer().frontToBackWindows()) {
            if (window.isOpen() && window.isInsideWindow(mouseX, mouseY)) {
                return true;
            }
        }

        // Blocked during shape-build confirmation phase
        var shapeSession = builderScreen.getShapeController().getShapeBuildSession();
        if (shapeSession != null && shapeSession.phase() == ShapeBuildTypes.Phase.READY_CONFIRM) {
            return true;
        }

        // Blocked when Quick Build panel has an active box selection in progress
        var qbSession = builderScreen.getShapeController().getShapeBuildSession();
        if (builderScreen.isQuickBuildOpen() && qbSession != null
                && (qbSession.phase() == ShapeBuildTypes.Phase.NEED_SECOND_POINT
                || qbSession.phase() == ShapeBuildTypes.Phase.NEED_THIRD_POINT)) {
            return true;
        }

        return false;
    }

    // ══════════════════════════════════════════════
    //  Highlight Rendering – Entity & Block
    // ══════════════════════════════════════════════

    /**
     * Renders corner-bracket highlights around an entity's bounding box.
     *
     * @param poseStack    current transformation stack
     * @param lineBuffer   vertex consumer for bracket quads
     * @param entity       the target entity
     * @param distance     distance from the camera to the entity centre (used for thickness scaling)
     * @param breathFactor current breathing animation multiplier
     */
    private static void renderEntityCornerHighlight(PoseStack poseStack, VertexConsumer lineBuffer,
            VertexConsumer noDepthBuffer, Entity entity, double distance, float breathFactor) {
        AABB bounds = entity.getBoundingBox().inflate(INFLATE);
        float r = ENTITY_COLOR_R * breathFactor;
        float g = ENTITY_COLOR_G * breathFactor;
        float b = ENTITY_COLOR_B * breathFactor;

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, lineBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, distance);

        // ── Transparent no-depth brackets (visible through terrain) ──
        renderCornerBracketsNoDepth(poseStack, noDepthBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, distance);
    }

    /**
     * Renders corner-bracket highlights around a block's world-space bounding box.
     * Multi-block structures (double-plants, beds) are merged into a single bounding box.
     *
     * @param minecraft    the Minecraft client instance
     * @param poseStack    current transformation stack
     * @param lineBuffer   vertex consumer for bracket quads
     * @param pos          block position of the targeted block
     * @param distance     distance from the camera to the block centre
     * @param breathFactor current breathing animation multiplier
     */
    private static void renderBlockCornerHighlight(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer noDepthBuffer,
            BlockPos pos, Direction hitFace, double distance, float breathFactor) {
        if (minecraft.level == null) {
            return;
        }

        AABB bounds = computeWorldBounds(minecraft.level, pos);
        if (bounds == null) {
            return;
        }

        double off = LINE_OFFSET;
        BlockHighlightVisual visual = blockHighlightVisual(distance, breathFactor);

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, lineBuffer,
                bounds.minX - off, bounds.minY - off, bounds.minZ - off,
                bounds.maxX + off, bounds.maxY + off, bounds.maxZ + off,
                visual.r(), visual.g(), visual.b(), distance);

        // ── Transparent no-depth brackets (visible through terrain) ──
        renderCornerBracketsNoDepth(poseStack, noDepthBuffer,
                bounds.minX - off, bounds.minY - off, bounds.minZ - off,
                bounds.maxX + off, bounds.maxY + off, bounds.maxZ + off,
                visual.r(), visual.g(), visual.b(), distance);

        // Close targets already occupy a large portion of the screen, so the
        // face cover fades out and leaves the old bright skeleton as the read.
        renderHitFaceFog(lineBuffer, poseStack, bounds, hitFace, visual.r(), visual.g(), visual.b(), visual.faceAlpha());
        renderHitFaceFog(noDepthBuffer, poseStack, bounds, hitFace,
                visual.r(), visual.g(), visual.b(), visual.noDepthFaceAlpha());
    }

    /**
     * Blends block target feedback by camera distance: far targets keep the
     * current orange face cover, while close targets become bright yellow
     * skeletons so the selected face does not flood the player's view.
     */
    private static BlockHighlightVisual blockHighlightVisual(double distance, float breathFactor) {
        float nearWeight = 1.0F - smoothstep(NEAR_SKELETON_DISTANCE, FAR_COVER_DISTANCE, distance);
        float farWeight = 1.0F - nearWeight;
        float r = (NEAR_BLOCK_COLOR_R * nearWeight + BLOCK_COLOR_R * farWeight) * breathFactor;
        float g = (NEAR_BLOCK_COLOR_G * nearWeight + BLOCK_COLOR_G * farWeight) * breathFactor;
        float b = (NEAR_BLOCK_COLOR_B * nearWeight + BLOCK_COLOR_B * farWeight) * breathFactor;
        float faceAlpha = FACE_FOG_ALPHA_NEAR * nearWeight + FACE_FOG_ALPHA_FAR * farWeight;
        float noDepthFaceAlpha = NO_DEPTH_FACE_FOG_ALPHA_NEAR * nearWeight + NO_DEPTH_FACE_FOG_ALPHA_FAR * farWeight;
        return new BlockHighlightVisual(r, g, b, faceAlpha, noDepthFaceAlpha);
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0D - 2.0D * t));
    }

    private record BlockHighlightVisual(float r, float g, float b, float faceAlpha, float noDepthFaceAlpha) {
    }

    // ══════════════════════════════════════════════
    //  No-depth Bracket Rendering
    // ══════════════════════════════════════════════

    /** Adds transparent no-depth corner brackets to the renderer-level backstop buffer. */
    private static void renderCornerBracketsNoDepth(PoseStack poseStack, VertexConsumer noDepthBuffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, double distance) {
        if (noDepthBuffer == null) {
            return;
        }
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, noDepthBuffer,
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, NO_DEPTH_BRACKET_ALPHA, distance);
    }

    // ══════════════════════════════════════════════
    //  Hit-Face Fog Rendering
    // ══════════════════════════════════════════════

    /**
     * Renders a translucent coloured fog quad on the single face of the bounding box
     * that the player's crosshair is currently targeting. Rendered without depth test
     * so it remains visible even when occluded by terrain.
     *
     * @param consumer  vertex consumer
     * @param poseStack current transformation stack
     * @param bounds    the world-space bounding box of the target block/structure
     * @param face      the direction of the hit face
     * @param r         red   colour component [0, 1] (already modulated by breath factor)
     * @param g         green colour component [0, 1]
     * @param b         blue  colour component [0, 1]
     */
    private static void renderHitFaceFog(VertexConsumer consumer, PoseStack poseStack,
            AABB bounds, Direction face, float r, float g, float b, float alpha) {
        if (consumer == null) {
            return;
        }
        double off = FACE_FOG_OFFSET;

        double x1 = bounds.minX, x2 = bounds.maxX;
        double y1 = bounds.minY, y2 = bounds.maxY;
        double z1 = bounds.minZ, z2 = bounds.maxZ;

        BufferBuilder fogBuffer = new BufferBuilder(FACE_FOG_NO_DEPTH_BACKING, VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_COLOR);

        switch (face) {
            case DOWN -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1 - off, z1, x2, y1 - off, z1, x2, y1 - off, z2, x1, y1 - off, z2, r, g, b, alpha);
            case UP -> RenderingUtil.quad(consumer, poseStack,
                    x1, y2 + off, z1, x1, y2 + off, z2, x2, y2 + off, z2, x2, y2 + off, z1, r, g, b, alpha);
            case NORTH -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1, z1 - off, x2, y1, z1 - off, x2, y2, z1 - off, x1, y2, z1 - off, r, g, b, alpha);
            case SOUTH -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1, z2 + off, x1, y2, z2 + off, x2, y2, z2 + off, x2, y1, z2 + off, r, g, b, alpha);
            case WEST -> RenderingUtil.quad(consumer, poseStack,
                    x1 - off, y1, z1, x1 - off, y2, z1, x1 - off, y2, z2, x1 - off, y1, z2, r, g, b, alpha);
            case EAST -> RenderingUtil.quad(consumer, poseStack,
                    x2 + off, y1, z1, x2 + off, y1, z2, x2 + off, y2, z2, x2 + off, y2, z1, r, g, b, alpha);
        }

        var meshData = fogBuffer.build();
        if (meshData != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            FACE_FOG_NO_DEPTH.draw(meshData);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    // ══════════════════════════════════════════════
    //  Bounds Computation
    // ══════════════════════════════════════════════

    /**
     * Checks whether the given block position is within the RTS build boundary.
     * If no boundary is active ({@code controller.hasBounds() == false}), always returns {@code true}.
     *
     * @param controller the client RTS controller (provides anchor and radius)
     * @param pos        the block position to test
     * @return {@code true} if the position is within the build boundary, or if no boundary is set
     */
    private static boolean isWithinBounds(ClientRtsController controller, BlockPos pos) {
        if (!controller.hasBounds()) return true;
        return RenderingUtil.isWithinBounds(pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    /**
     * Computes the world-space axis-aligned bounding box for the block at {@code pos}.
     * If the block is part of a multi-block structure (double-plant, bed, etc.) the
     * bounding box encompasses all connected blocks.
     *
     * @param level the world
     * @param pos   the targeted block position
     * @return the merged world-space AABB, or {@code null} if the block has no shape
     */
    private static AABB computeWorldBounds(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        if (isMultiBlockStructure(state)) {
            return computeMultiBlockBoundsBfs(level, pos);
        }

        return computeSingleBlockAABB(level, pos);
    }

    /**
     * Returns {@code true} if the given block state is part of a multi-block structure
     * (e.g. double-tall plants or beds) that requires BFS-based bounds merging.
     *
     * @param state the block state to test
     * @return {@code true} if the state has a multi-block property
     */
    private static boolean isMultiBlockStructure(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            || state.hasProperty(BlockStateProperties.BED_PART);
    }

    /**
     * Performs a BFS from the starting {@code pos} to discover all connected blocks that
     * belong to the same multi-block structure, then returns the merged world-space AABB.
     * <p>
     * Connection directions are determined by {@link #getConnectionDirections} and depend
     * on the block type (vertical for double-plants, horizontal for beds).
     *
     * @param level the world
     * @param pos   the starting block position
     * @return the merged world-space AABB of the entire structure
     */
    private static AABB computeMultiBlockBoundsBfs(Level level, BlockPos pos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        visited.add(pos);

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);

            AABB aabb = computeSingleBlockAABB(level, current);
            if (aabb != null) {
                minX = Math.min(minX, aabb.minX);
                minY = Math.min(minY, aabb.minY);
                minZ = Math.min(minZ, aabb.minZ);
                maxX = Math.max(maxX, aabb.maxX);
                maxY = Math.max(maxY, aabb.maxY);
                maxZ = Math.max(maxZ, aabb.maxZ);
            }

            for (Direction dir : getConnectionDirections(currentState)) {
                BlockPos neighbor = current.relative(dir);
                if (!visited.add(neighbor)) {
                    continue;
                }

                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.is(currentState.getBlock()) && isMultiBlockStructure(neighborState)) {
                    queue.add(neighbor);
                }
            }
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Returns the connection direction(s) for a multi-block structure.
     * <ul>
     *   <li><b>Double-block-half</b> (double-plants): connects vertically (UP from LOWER, DOWN from UPPER)</li>
     *   <li><b>Bed</b>: connects horizontally based on the HEAD/FOOT orientation and facing</li>
     * </ul>
     *
     * @param state the block state
     * @return an array of neighbour directions to traverse during BFS
     */
    private static Direction[] getConnectionDirections(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            return new Direction[]{half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN};
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            // HEAD connects towards the foot (opposite of facing), FOOT connects towards the head
            return new Direction[]{part == BedPart.HEAD ? facing.getOpposite() : facing};
        }
        return new Direction[0];
    }

    /**
     * Computes the world-space AABB for a single block by merging all sub-boxes of its
     * {@link VoxelShape}. If the shape is empty (e.g. air or structure-void), returns {@code null}.
     *
     * @param level the world
     * @param pos   the block position
     * @return the merged world-space AABB, or {@code null} if the block has no shape
     */
    private static AABB computeSingleBlockAABB(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return null;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        for (AABB box : shape.toAabbs()) {
            minX = Math.min(minX, pos.getX() + box.minX);
            minY = Math.min(minY, pos.getY() + box.minY);
            minZ = Math.min(minZ, pos.getZ() + box.minZ);
            maxX = Math.max(maxX, pos.getX() + box.maxX);
            maxY = Math.max(maxY, pos.getY() + box.maxY);
            maxZ = Math.max(maxZ, pos.getZ() + box.maxZ);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
