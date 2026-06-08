package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.BoundaryLineRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.ChunkGuideRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.InteractionTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.StorageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 所有 RTS 视觉叠加效果的统一调度入口。
 * 在 AFTER_TRANSLUCENT_BLOCKS 阶段按固定顺序委托给各子渲染器。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class RtsVisualOverlayRenderer {
    private static final int GL_LEQUAL = 515;

    // ===== 自定义 RenderType =====

    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP,
            2 * 1024 * 1024, false, true,
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
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES,
            2 * 1024 * 1024,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    /** 包围盒线框矩形 — QUADS 模式确保从任意视角可见 */
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

    private static final RenderType LINES = RenderType.lines();
    private static final RenderType FILLED_BOX = RenderType.debugFilledBox();

    // ===== 后备缓冲区 =====

    private static final ByteBufferBuilder CHUNK_FILL_BACKING = new ByteBufferBuilder(CHUNK_XRAY_FILL.bufferSize());
    private static final ByteBufferBuilder CHUNK_LINE_BACKING = new ByteBufferBuilder(CHUNK_XRAY_LINES.bufferSize());
    private static final ByteBufferBuilder LINE_BACKING = new ByteBufferBuilder(LINES.bufferSize());
    private static final ByteBufferBuilder FILL_BACKING = new ByteBufferBuilder(FILLED_BOX.bufferSize());
    private static final ByteBufferBuilder BRACKET_BACKING = new ByteBufferBuilder(BRACKET_QUADS.bufferSize());
    private static final ByteBufferBuilder TARGET_NO_DEPTH_BACKING = new ByteBufferBuilder(TARGET_NO_DEPTH_QUADS.bufferSize());

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

            // 1. 区块引导网格（X 射线透视）
            if (controller.isChunkCurtainVisible()) {
                renderChunkGuides(minecraft, camPos, poseStack);
            }

            // 2. 通用渲染管线 (lines + filledBox + brackets)
            double ax = controller.getAnchorX(), ay = controller.getAnchorY(), az = controller.getAnchorZ();
            double r = controller.getMaxRadius();
            double minX = ax - r, maxX = ax + r, minZ = az - r, maxZ = az + r;

            BufferBuilder lineBuffer = bufferFor(LINES, LINE_BACKING);
            BufferBuilder fillBuffer = bufferFor(FILLED_BOX, FILL_BACKING);
            BufferBuilder bracketBuffer = bufferFor(BRACKET_QUADS, BRACKET_BACKING);
            BufferBuilder targetNoDepthBuffer = bufferFor(TARGET_NO_DEPTH_QUADS, TARGET_NO_DEPTH_BACKING);

            BoundaryLineRenderer.renderRedBoundary(poseStack, lineBuffer, minX, minZ, maxX, maxZ, ay);
            StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, bracketBuffer);
            InteractionTargetRenderer.renderHoveredInteractionTarget(minecraft, controller, poseStack, bracketBuffer, targetNoDepthBuffer);
            ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            BlueprintCaptureRenderer.renderBlueprintCaptureBox(poseStack, lineBuffer, fillBuffer);
            BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer);
            PlacementAnimationRenderer.render(minecraft, poseStack, lineBuffer, fillBuffer);

            drawIfNotEmpty(LINES, lineBuffer);
            drawIfNotEmpty(FILLED_BOX, fillBuffer);
            drawBrackets(bracketBuffer);
            drawNoDepth(TARGET_NO_DEPTH_QUADS, targetNoDepthBuffer);
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

    // ===== 工具方法 =====

    private static BufferBuilder bufferFor(RenderType type, ByteBufferBuilder backing) {
        return new BufferBuilder(backing, type.mode, type.format);
    }

    private static void drawIfNotEmpty(RenderType type, BufferBuilder buffer) {
        MeshData data = buffer.build();
        if (data != null) type.draw(data);
    }

    /** 绘制交互目标包围盒（启用 polygon offset 消除 Z-fighting） */
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

    /** 禁用深度测试绘制（X 射线透视效果） */
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
