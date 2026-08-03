package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.client.rendering.RtsPrivateBufferLifecycle;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders the chain-mining (ultimine) ghost preview in FTB Ultimine style,
 * and provides shared two-pass edge rendering primitives used by
 * {@link MergedSkeletonRenderer}.
 */
public final class UltimineGhostRenderer extends RenderStateShard {

    // ── Custom no-depth translucent line render type ──

    private static final RenderType LINES_NO_DEPTH = RenderType.create(
            "rtsbuilding_ultimine_lines_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            512,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final BufferBuilder LINES_NO_DEPTH_BUFFER = new BufferBuilder(LINES_NO_DEPTH.bufferSize());

    // ── Custom no-depth translucent quad render type (for entity brackets) ──

    private static final RenderType BRACKET_NO_DEPTH = RenderType.create(
            "rtsbuilding_ultimine_bracket_no_depth",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final BufferBuilder BRACKET_NO_DEPTH_BUFFER = new BufferBuilder(BRACKET_NO_DEPTH.bufferSize());

    // ── Custom opaque depth-tested quad render type (for entity brackets) ──

    private static final RenderType BRACKET_OPAQUE = RenderType.create(
            "rtsbuilding_ultimine_bracket_opaque",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final BufferBuilder BRACKET_OPAQUE_BUFFER = new BufferBuilder(BRACKET_OPAQUE.bufferSize());

    // ── Breathing colour parameters ──

    private static final float BREATH_SPEED = 0.2F;
    private static final float BREATH_MIN_FACTOR = 0.7F;

    private UltimineGhostRenderer() {
        super("rtsbuilding_ultimine_ghost_renderer", () -> {}, () -> {});
    }

    // ===== Public API (called from ShapeGhostRenderer) =====

    static void render(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, float progress) {
        // If the player's mouse is hovering over an entity, render entity selection
        // brackets instead of the chain-destroy (ultimine) ghost preview.
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null && mc.getCameraEntity() != null
                && mc.screen instanceof com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen) {
            Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 viewDir = RaycastHelper.computeCursorRayDirection(mc);
            Vec3 rayEnd = camPos.add(viewDir.scale(128.0D));
            EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(mc, camPos, rayEnd, viewDir, 128.0D);
            if (entityHit != null) {
                renderEntityBrackets(poseStack, entityHit.getEntity(), camPos);
                return;
            }
        }

        List<BlockPos> blocks = preview.blocks();
        if (blocks == null || blocks.isEmpty()) return;

        // Step 1 — outer perimeter filtering
        List<BlockPos> outerBlocks = filterOuterBlocks(blocks);
        if (outerBlocks.isEmpty()) return;

        // Step 2 — merge adjacent outer blocks and extract edge segments
        List<UltimineBlockMerger.EdgeLine> edges = UltimineBlockMerger.getEdgeLines(outerBlocks);
        if (edges.isEmpty()) return;

        Matrix4f matrix = poseStack.last().pose();

        // Step 3 — breathing gold colour
        float breathFactor = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);
        float r = 1.00F * breathFactor;
        float g = 0.72F * breathFactor;
        float b = 0.24F * breathFactor;
        float edgeR = RenderingUtil.lerp(r, 0.38F, progress);
        float edgeG = RenderingUtil.lerp(g, 1.00F, progress);
        float edgeB = RenderingUtil.lerp(b, 0.42F, progress);

        // Step 4 — opaque depth-tested edges
        renderPass1(edges, matrix, lineBuffer, edgeR, edgeG, edgeB);

        // Step 5 — translucent no-depth edges
        renderPass2(edges, matrix, edgeR, edgeG, edgeB, 0.34F);

        // Step 6 — faint per-block fill
        renderFill(outerBlocks, poseStack, fillBuffer, edgeR, edgeG, edgeB, 0.08F);
    }

    // ===== Shared edge rendering primitives (used by MergedSkeletonRenderer) =====

    static void renderPass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b) {
        renderPass1(edges, matrix, lineBuffer, r, g, b, 0.95F);
    }

    static void renderPass1(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            VertexConsumer lineBuffer, float r, float g, float b, float alpha) {
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            lineBuffer.vertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .color(r, g, b, alpha)
                    .normal(edge.xn(), edge.yn(), edge.zn()).endVertex();
            lineBuffer.vertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .color(r, g, b, alpha)
                    .normal(edge.xn(), edge.yn(), edge.zn()).endVertex();
        }
    }

    static void renderPass2(List<UltimineBlockMerger.EdgeLine> edges, Matrix4f matrix,
            float r, float g, float b, float alpha) {
        begin(LINES_NO_DEPTH, LINES_NO_DEPTH_BUFFER);
        for (UltimineBlockMerger.EdgeLine edge : edges) {
            LINES_NO_DEPTH_BUFFER.vertex(matrix, (float) edge.x1(), (float) edge.y1(), (float) edge.z1())
                    .color(r, g, b, alpha)
                    .normal(edge.xn(), edge.yn(), edge.zn()).endVertex();
            LINES_NO_DEPTH_BUFFER.vertex(matrix, (float) edge.x2(), (float) edge.y2(), (float) edge.z2())
                    .color(r, g, b, alpha)
                    .normal(edge.xn(), edge.yn(), edge.zn()).endVertex();
        }
        if (!LINES_NO_DEPTH_BUFFER.isCurrentBatchEmpty()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            LINES_NO_DEPTH.end(LINES_NO_DEPTH_BUFFER, 0, 0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        } else {
            LINES_NO_DEPTH_BUFFER.endOrDiscardIfEmpty();
        }
    }

    static void renderFill(List<BlockPos> outerBlocks, PoseStack poseStack,
            VertexConsumer fillBuffer, float r, float g, float b, float fillA) {
        for (BlockPos pos : outerBlocks) {
            RenderingUtil.filledBox(
                    poseStack, fillBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1,
                    r, g, b, fillA);
        }
    }

    // ===== Entity bracket rendering (ultimine override when hovering entity) =====

    /**
     * Renders entity selection corner brackets when the player's mouse is over an
     * entity during ultimine (chain-destroy) mode. This overrides the block-level
     * chain-mining ghost preview so the player can clearly see that the cursor
     * is targeting an entity rather than blocks.
     */
    private static void renderEntityBrackets(PoseStack poseStack,
            Entity entity, Vec3 camPos) {
        AABB bounds = entity.getBoundingBox().inflate(0.03D);
        float breathFactor = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);
        float r = 0.50F * breathFactor;
        float g = 0.80F * breathFactor;
        float b = 1.00F * breathFactor;
        double distance = camPos.distanceTo(bounds.getCenter());

        // Opaque depth-tested brackets (drawn via dedicated RenderType + BufferBuilder)
        begin(BRACKET_OPAQUE, BRACKET_OPAQUE_BUFFER);
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, BRACKET_OPAQUE_BUFFER,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, 0.95F, distance);
        if (!BRACKET_OPAQUE_BUFFER.isCurrentBatchEmpty()) {
            BRACKET_OPAQUE.end(BRACKET_OPAQUE_BUFFER, 0, 0, 0);
        } else {
            BRACKET_OPAQUE_BUFFER.endOrDiscardIfEmpty();
        }

        // Transparent no-depth brackets (visible through world geometry)
        begin(BRACKET_NO_DEPTH, BRACKET_NO_DEPTH_BUFFER);
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, BRACKET_NO_DEPTH_BUFFER,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, 0.20F, distance);
        if (!BRACKET_NO_DEPTH_BUFFER.isCurrentBatchEmpty()) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            BRACKET_NO_DEPTH.end(BRACKET_NO_DEPTH_BUFFER, 0, 0, 0);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        } else {
            BRACKET_NO_DEPTH_BUFFER.endOrDiscardIfEmpty();
        }
    }

    /** 复用 Forge 1.20.1 的旧 BufferBuilder 生命周期。 */
    private static void begin(RenderType type, BufferBuilder buffer) {
        RtsPrivateBufferLifecycle.begin(buffer, type.mode(), type.format());
    }

    // ===== Private helpers =====

    /**
     * Filters to outer-perimeter blocks (at least one face neighbour outside
     * the selection set).
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
            if (isOuter) outerBlocks.add(pos);
        }
        return outerBlocks;
    }
}
