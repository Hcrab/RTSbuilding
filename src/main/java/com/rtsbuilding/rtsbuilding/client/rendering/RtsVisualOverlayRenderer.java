package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.AdvancedShapeSelectionBoxRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.culling.RtsCullingRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.*;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.PlacedBlockRotationHandleRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/**
 * Central dispatch point for all RTS visual overlay effects.
 * Renders during the AFTER_TRANSLUCENT_BLOCKS stage, delegating to
 * sub-renderers in a fixed order.
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer extends RenderStateShard {
    private static final int GL_LEQUAL = 515;

    // ===== Custom RenderTypes =====

    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CHUNK_XRAY_LINES = RenderType.create(
            "rtsbuilding_chunk_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** Bounding box bracket quads — QUADS mode ensures visibility from any angle */
    private static final RenderType BRACKET_QUADS = RenderType.create(
            "rtsbuilding_bracket_quads",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType TARGET_NO_DEPTH_QUADS = RenderType.create(
            "rtsbuilding_target_no_depth_quads",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CULLING_HANDLE_NO_DEPTH_FILL = RenderType.create(
            "rtsbuilding_culling_handle_no_depth_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CULLING_HANDLE_NO_DEPTH_LINES = RenderType.create(
            "rtsbuilding_culling_handle_no_depth_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 512, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    /** 使用原版 forcefield 纹理渲染 RTS 边界，避免发布包携带额外小资源。 */
    private static final RenderType BOUNDARY_BARRIER = RenderType.entityTranslucent(
            new ResourceLocation("minecraft", "textures/misc/forcefield.png"));

    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.create(
            "rtsbuilding_filled_box",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 512, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // ===== Backing buffers =====

    private static final BufferBuilder CHUNK_FILL_BUFFER = new BufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final BufferBuilder CHUNK_LINE_BUFFER = new BufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(LINES.bufferSize());
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(FILLED_BOX.bufferSize());
    private static final BufferBuilder BRACKET_BUFFER = new BufferBuilder(BRACKET_QUADS.bufferSize());
    private static final BufferBuilder TARGET_NO_DEPTH_BUFFER = new BufferBuilder(TARGET_NO_DEPTH_QUADS.bufferSize());
    private static final BufferBuilder BOUNDARY_BARRIER_BUFFER = new BufferBuilder(BOUNDARY_BARRIER.bufferSize());
    private static final BufferBuilder CULLING_HANDLE_FILL_BUFFER = new BufferBuilder(CULLING_HANDLE_NO_DEPTH_FILL.bufferSize());
    private static final BufferBuilder CULLING_HANDLE_LINE_BUFFER = new BufferBuilder(CULLING_HANDLE_NO_DEPTH_LINES.bufferSize());

    private RtsVisualOverlayRenderer() {
        super("rtsbuilding_visual_overlay", () -> {}, () -> {});
    }

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

            BufferBuilder lineBuffer = beginBuffer(LINES, LINE_BUFFER);
            BufferBuilder fillBuffer = beginBuffer(FILLED_BOX, FILL_BUFFER);
            BufferBuilder bracketBuffer = beginBuffer(BRACKET_QUADS, BRACKET_BUFFER);
            BufferBuilder targetNoDepthBuffer = beginBuffer(TARGET_NO_DEPTH_QUADS, TARGET_NO_DEPTH_BUFFER);
            BufferBuilder cullingHandleFillBuffer = beginBuffer(CULLING_HANDLE_NO_DEPTH_FILL, CULLING_HANDLE_FILL_BUFFER);
            BufferBuilder cullingHandleLineBuffer = beginBuffer(CULLING_HANDLE_NO_DEPTH_LINES, CULLING_HANDLE_LINE_BUFFER);

            BufferBuilder barrierBuffer = beginBuffer(BOUNDARY_BARRIER, BOUNDARY_BARRIER_BUFFER);

            BoundaryLineRenderer.renderBarrierBoundary(poseStack, barrierBuffer, minX, minZ, maxX, maxZ, ay, minecraft.level);
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, bracketBuffer);
            InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller, poseStack, bracketBuffer, targetNoDepthBuffer);
            PlayerMoveTargetRenderer.render(minecraft, poseStack, bracketBuffer, targetNoDepthBuffer);
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            PlacedBlockRotationHandleRenderer.render(
                    minecraft,
                    poseStack,
                    cullingHandleLineBuffer,
                    cullingHandleFillBuffer);
            AdvancedShapeSelectionBoxRenderer.render(minecraft, poseStack, cullingHandleLineBuffer, cullingHandleFillBuffer);
            RtsCullingRenderer.render(poseStack, lineBuffer, fillBuffer, cullingHandleLineBuffer, cullingHandleFillBuffer);
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(
                    poseStack,
                    lineBuffer,
                    fillBuffer,
                    cullingHandleLineBuffer,
                    cullingHandleFillBuffer);
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
        BufferBuilder fillBuffer = beginBuffer(CHUNK_XRAY_FILL, CHUNK_FILL_BUFFER);
        BufferBuilder lineBuffer = beginBuffer(CHUNK_XRAY_LINES, CHUNK_LINE_BUFFER);
        ChunkGuideRenderer.renderChunkGuides(minecraft, camPos, poseStack, fillBuffer, lineBuffer);
        drawNoDepth(CHUNK_XRAY_FILL, fillBuffer);
        drawNoDepth(CHUNK_XRAY_LINES, lineBuffer);
    }

    // ===== Utility methods =====

    /** Forge 1.20.1 的 BufferBuilder 复用入口；生产渲染语义由上层保持不变。 */
    private static BufferBuilder beginBuffer(RenderType type, BufferBuilder buffer) {
        RtsPrivateBufferLifecycle.begin(buffer, type.mode(), type.format());
        return buffer;
    }

    private static void drawIfNotEmpty(RenderType type, BufferBuilder buffer) {
        if (!buffer.building()) return;
        if (buffer.isCurrentBatchEmpty()) {
            buffer.endOrDiscardIfEmpty();
            return;
        }
        type.end(buffer, 0, 0, 0);
    }

    /** Draws interaction target bounding boxes (uses polygon offset to prevent Z-fighting) */
    private static void drawBrackets(BufferBuilder buffer) {
        if (!buffer.building()) return;
        if (buffer.isCurrentBatchEmpty()) {
            buffer.endOrDiscardIfEmpty();
            return;
        }
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -1.0F);
        BRACKET_QUADS.end(buffer, 0, 0, 0);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
    }

    /** Draws with depth test disabled (X-ray see-through effect) */
    private static void drawNoDepth(RenderType type, BufferBuilder buffer) {
        if (!buffer.building()) return;
        if (buffer.isCurrentBatchEmpty()) {
            buffer.endOrDiscardIfEmpty();
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        type.end(buffer, 0, 0, 0);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
    }
}
