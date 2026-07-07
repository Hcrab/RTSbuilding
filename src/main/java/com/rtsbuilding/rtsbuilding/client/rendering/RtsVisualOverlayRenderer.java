package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.culling.RtsCullingRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Central dispatch point for all RTS visual overlay effects.
 * Renders during the AFTER_TRANSLUCENT_BLOCKS stage, delegating to
 * sub-renderers in a fixed order.
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer {
    private static final int GL_LEQUAL = 515;

    // ===== Custom RenderTypes =====

    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CHUNK_XRAY_LINES = RenderType.create(
            "rtsbuilding_chunk_xray_lines",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.LINES, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** Bounding box bracket quads — QUADS mode ensures visibility from any angle */
    private static final RenderType BRACKET_QUADS = RenderType.create(
            "rtsbuilding_bracket_quads",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final RenderType TARGET_NO_DEPTH_QUADS = RenderType.create(
            "rtsbuilding_target_no_depth_quads",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CULLING_HANDLE_NO_DEPTH_FILL = RenderType.create(
            "rtsbuilding_culling_handle_no_depth_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CULLING_HANDLE_NO_DEPTH_LINES = RenderType.create(
            "rtsbuilding_culling_handle_no_depth_lines",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.LINES, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** World border style barrier for the RTS build boundary, using a custom striped texture */
    private static final RenderType BOUNDARY_BARRIER = RenderType.entityTranslucent(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textures/misc/barrier.png"));

    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.debugFilledBox();

    // ===== Backing buffers =====

    private static final ByteBufferBuilder CHUNK_FILL_BACKING = new ByteBufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final ByteBufferBuilder CHUNK_LINE_BACKING = new ByteBufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final ByteBufferBuilder LINE_BACKING = new ByteBufferBuilder(LINES.bufferSize());
    private static final ByteBufferBuilder FILL_BACKING = new ByteBufferBuilder(FILLED_BOX.bufferSize());
    private static final ByteBufferBuilder BRACKET_BACKING = new ByteBufferBuilder(BRACKET_QUADS.bufferSize());
    private static final ByteBufferBuilder TARGET_NO_DEPTH_BACKING = new ByteBufferBuilder(TARGET_NO_DEPTH_QUADS.bufferSize());
    private static final ByteBufferBuilder BOUNDARY_BARRIER_BACKING = new ByteBufferBuilder(BOUNDARY_BARRIER.bufferSize());
    private static final ByteBufferBuilder CULLING_HANDLE_FILL_BACKING = new ByteBufferBuilder(CULLING_HANDLE_NO_DEPTH_FILL.bufferSize());
    private static final ByteBufferBuilder CULLING_HANDLE_LINE_BACKING = new ByteBufferBuilder(CULLING_HANDLE_NO_DEPTH_LINES.bufferSize());

    private RtsVisualOverlayRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            // 1. Chunk guide grid (X-ray)
            if (controller.isChunkCurtainVisible()) {
                renderChunkGuides(minecraft, camPos, poseStack);
            }

            // 2. General render pipeline (lines + filledBox + brackets)
            double ax = controller.getAnchorX(), ay = controller.getAnchorY(), az = controller.getAnchorZ();
            double r = controller.getMaxRadius();
            double minX = ax - r, maxX = ax + r, minZ = az - r, maxZ = az + r;

            BufferBuilder lineBuffer = bufferFor(LINES, LINE_BACKING);
            BufferBuilder fillBuffer = bufferFor(FILLED_BOX, FILL_BACKING);
            BufferBuilder bracketBuffer = bufferFor(BRACKET_QUADS, BRACKET_BACKING);
            BufferBuilder targetNoDepthBuffer = bufferFor(TARGET_NO_DEPTH_QUADS, TARGET_NO_DEPTH_BACKING);
            BufferBuilder cullingHandleFillBuffer = bufferFor(CULLING_HANDLE_NO_DEPTH_FILL, CULLING_HANDLE_FILL_BACKING);
            BufferBuilder cullingHandleLineBuffer = bufferFor(CULLING_HANDLE_NO_DEPTH_LINES, CULLING_HANDLE_LINE_BACKING);

            BufferBuilder barrierBuffer = bufferFor(BOUNDARY_BARRIER, BOUNDARY_BARRIER_BACKING);

            BoundaryLineRenderer.renderBarrierBoundary(poseStack, barrierBuffer, minX, minZ, maxX, maxZ, ay, minecraft.level);
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, bracketBuffer);
            InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller, poseStack, bracketBuffer, targetNoDepthBuffer);
            PlayerMoveTargetRenderer.render(minecraft, poseStack, bracketBuffer, targetNoDepthBuffer);
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            RtsCullingRenderer.render(poseStack, lineBuffer, fillBuffer, cullingHandleLineBuffer, cullingHandleFillBuffer);
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(poseStack, lineBuffer, fillBuffer);
            BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            PlacementAnimationRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);

            drawIfNotEmpty(BOUNDARY_BARRIER, barrierBuffer);
            drawIfNotEmpty(LINES, lineBuffer);
            drawIfNotEmpty(FILLED_BOX, fillBuffer);
            drawBrackets(bracketBuffer);
            drawNoDepth(TARGET_NO_DEPTH_QUADS, targetNoDepthBuffer);
            drawNoDepth(CULLING_HANDLE_NO_DEPTH_FILL, cullingHandleFillBuffer);
            drawNoDepth(CULLING_HANDLE_NO_DEPTH_LINES, cullingHandleLineBuffer);
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderChunkGuides(Minecraft minecraft, Vec3 camPos, PoseStack poseStack) {
        BufferBuilder fillBuffer = bufferFor(CHUNK_XRAY_FILL, CHUNK_FILL_BACKING);
        BufferBuilder lineBuffer = bufferFor(CHUNK_XRAY_LINES, CHUNK_LINE_BACKING);
        ChunkGuideRenderer.renderChunkGuides(minecraft, camPos, poseStack, fillBuffer, lineBuffer);
        drawNoDepth(CHUNK_XRAY_FILL, fillBuffer);
        drawNoDepth(CHUNK_XRAY_LINES, lineBuffer);
    }

    // ===== Utility methods =====

    private static BufferBuilder bufferFor(RenderType type, ByteBufferBuilder backing) {
        return new BufferBuilder(backing, type.mode, type.format);
    }

    private static void drawIfNotEmpty(RenderType type, BufferBuilder buffer) {
        MeshData data = buffer.build();
        if (data != null) type.draw(data);
    }

    /** Draws interaction target bounding boxes (uses polygon offset to prevent Z-fighting) */
    private static void drawBrackets(BufferBuilder buffer) {
        MeshData data = buffer.build();
        if (data != null) {
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(-1.0F, -1.0F);
            BRACKET_QUADS.draw(data);
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
        }
    }

    /** Draws with depth test disabled (X-ray see-through effect) */
    private static void drawNoDepth(RenderType type, BufferBuilder buffer) {
        MeshData data = buffer.build();
        if (data != null) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            type.draw(data);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL_LEQUAL);
        }
    }
}
