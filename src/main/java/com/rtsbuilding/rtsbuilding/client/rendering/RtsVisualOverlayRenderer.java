package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.BoundaryLineRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.ChunkGuideRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.InteractionTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.PlayerMoveTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.StorageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Central dispatch point for RTS world overlay rendering on Forge 1.20.1.
 *
 * <p>The class keeps the older Forge BufferBuilder lifecycle, but mirrors main's
 * visible target-highlight pipeline: normal lines/fill, bracket quads, a
 * no-depth target backstop, and the textured boundary barrier each use their
 * own render type so vertex formats do not bleed into one another.
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer extends RenderStateShard {
    private static final int GL_LEQUAL = 515;

    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            2 * 1024 * 1024,
            false,
            true,
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
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            2 * 1024 * 1024,
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

    private static final RenderType BRACKET_QUADS = RenderType.create(
            "rtsbuilding_bracket_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            false,
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
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            512,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType BARRIER_BOUNDARY = RenderType.entityTranslucent(
            new ResourceLocation(RtsbuildingMod.MODID, "textures/misc/barrier.png"));

    private static final BufferBuilder CHUNK_FILL_BUFFER = new BufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final BufferBuilder CHUNK_LINE_BUFFER = new BufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final BufferBuilder LINE_BUFFER = new BufferBuilder(RenderType.lines().bufferSize());
    private static final BufferBuilder FILL_BUFFER = new BufferBuilder(RenderType.debugFilledBox().bufferSize());
    private static final BufferBuilder BRACKET_BUFFER = new BufferBuilder(BRACKET_QUADS.bufferSize());
    private static final BufferBuilder TARGET_NO_DEPTH_BUFFER = new BufferBuilder(TARGET_NO_DEPTH_QUADS.bufferSize());
    private static final BufferBuilder BARRIER_BUFFER = new BufferBuilder(BARRIER_BOUNDARY.bufferSize());

    private RtsVisualOverlayRenderer() {
        super("rtsbuilding_visual_overlay", () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        try {
            poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

            if (controller.isChunkCurtainVisible()) {
                BufferBuilder chunkFillBuffer = beginBuffer(CHUNK_XRAY_FILL, CHUNK_FILL_BUFFER);
                BufferBuilder chunkLineBuffer = beginBuffer(CHUNK_XRAY_LINES, CHUNK_LINE_BUFFER);
                ChunkGuideRenderer.renderChunkGuides(minecraft, camPos, poseStack, chunkFillBuffer, chunkLineBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_FILL, chunkFillBuffer);
                drawBuiltBufferNoDepth(CHUNK_XRAY_LINES, chunkLineBuffer);
            }

            RenderType lines = RenderType.lines();
            RenderType filledBox = RenderType.debugFilledBox();
            BufferBuilder lineBuffer = beginBuffer(lines, LINE_BUFFER);
            BufferBuilder fillBuffer = beginBuffer(filledBox, FILL_BUFFER);
            BufferBuilder bracketBuffer = beginBuffer(BRACKET_QUADS, BRACKET_BUFFER);
            BufferBuilder targetNoDepthBuffer = beginBuffer(TARGET_NO_DEPTH_QUADS, TARGET_NO_DEPTH_BUFFER);
            BufferBuilder barrierBuffer = beginBuffer(BARRIER_BOUNDARY, BARRIER_BUFFER);

            double ax = controller.getAnchorX();
            double ay = controller.getAnchorY();
            double az = controller.getAnchorZ();
            double radius = controller.getMaxRadius();
            double minX = ax - radius;
            double maxX = ax + radius;
            double minZ = az - radius;
            double maxZ = az + radius;

            BoundaryLineRenderer.renderBarrierBoundary(
                    poseStack, barrierBuffer, minX, minZ, maxX, maxZ, ay, minecraft.level);
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, lineBuffer);
            InteractionTargetRenderer.renderHoveredInteractionTarget(
                    minecraft, controller, poseStack, bracketBuffer, targetNoDepthBuffer);
            PlayerMoveTargetRenderer.render(minecraft, poseStack, bracketBuffer, targetNoDepthBuffer);
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(poseStack, lineBuffer, fillBuffer);
            BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            PlacementAnimationRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);

            drawBuiltBuffer(BARRIER_BOUNDARY, barrierBuffer);
            drawBuiltBuffer(lines, lineBuffer);
            drawBuiltBuffer(filledBox, fillBuffer);
            drawBrackets(bracketBuffer);
            drawBuiltBufferNoDepth(TARGET_NO_DEPTH_QUADS, targetNoDepthBuffer);
        } finally {
            poseStack.popPose();
        }
    }

    private static BufferBuilder beginBuffer(RenderType renderType, BufferBuilder buffer) {
        if (buffer.building()) {
            buffer.discard();
        }
        buffer.begin(renderType.mode(), renderType.format());
        return buffer;
    }

    private static void drawBuiltBuffer(RenderType renderType, BufferBuilder buffer) {
        if (!buffer.building()) {
            return;
        }
        if (buffer.isCurrentBatchEmpty()) {
            buffer.endOrDiscardIfEmpty();
            return;
        }
        renderType.end(buffer, VertexSorting.DISTANCE_TO_ORIGIN);
    }

    private static void drawBrackets(BufferBuilder buffer) {
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0F, -1.0F);
        drawBuiltBuffer(BRACKET_QUADS, buffer);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
    }

    private static void drawBuiltBufferNoDepth(RenderType renderType, BufferBuilder buffer) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        drawBuiltBuffer(renderType, buffer);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL_LEQUAL);
    }
}
