package com.rtsbuilding.rtsbuilding.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.PlacementAnimationRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintCaptureRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.blueprint.BlueprintGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.AdvancedShapeSelectionBoxRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.ShapeGhostRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.culling.RtsCullingRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.BoundaryLineRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.ChunkGuideRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.InteractionTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.PlayerMoveTargetRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.overlay.StorageRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsRecordedGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * RTS 世界特效的统一几何提取器。
 *
 * <p>本类只运行既有形状算法并冻结顶点，不再创建 RenderType、直接 draw 或操作共享 buffer。
 * NeoForge 26.1 的 extract/submit 生命周期由平台桥负责，因此这里也能作为以后其它 loader 的
 * 通用“插头母座”。</p>
 */
public final class RtsVisualOverlayRenderer {
    private RtsVisualOverlayRenderer() {
    }

    public static List<RtsRecordedGeometry.Batch> capture(Minecraft minecraft, Vec3 cameraPosition) {
        ClientRtsController controller = ClientRtsController.get();
        if (minecraft == null || minecraft.level == null || !controller.hasBounds()) {
            return List.of();
        }

        PoseStack poseStack = new PoseStack();
        RtsRecordedGeometry.Recorder chunkFill = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder chunkLines = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder boundary = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder lines = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder fill = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder brackets = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder targetNoDepth = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder handleFill = new RtsRecordedGeometry.Recorder();
        RtsRecordedGeometry.Recorder handleLines = new RtsRecordedGeometry.Recorder();

        if (controller.isChunkCurtainVisible()) {
            ChunkGuideRenderer.renderChunkGuides(
                    minecraft, cameraPosition, poseStack, chunkFill, chunkLines);
        }

        double ax = controller.getAnchorX();
        double ay = controller.getAnchorY();
        double az = controller.getAnchorZ();
        double radius = controller.getMaxRadius();
        BoundaryLineRenderer.renderBarrierBoundary(
                poseStack,
                boundary,
                ax - radius,
                az - radius,
                ax + radius,
                az + radius,
                ay,
                minecraft.level);
        StorageRenderer.renderLinkedStorages(minecraft, controller, poseStack, brackets);
        InteractionTargetRenderer.renderHoveredInteractionTarget(
                minecraft, controller, poseStack, brackets, targetNoDepth);
        PlayerMoveTargetRenderer.render(minecraft, poseStack, brackets, targetNoDepth);
        ShapeGhostRenderer.renderShapeGhostPreview(minecraft, poseStack, lines, fill);
        AdvancedShapeSelectionBoxRenderer.render(minecraft, poseStack, handleLines, handleFill);
        RtsCullingRenderer.render(poseStack, lines, fill, handleLines, handleFill);
        BlueprintCaptureRenderer.renderBlueprintCaptureBox(
                poseStack, lines, fill, handleLines, handleFill);
        BlueprintGhostRenderer.renderBlueprintGhostPreview(minecraft, poseStack, lines, fill);
        PlacementAnimationRenderer.render(minecraft, poseStack, lines, fill);

        List<RtsRecordedGeometry.Batch> batches = new ArrayList<>();
        addBatch(batches, RtsRecordedGeometry.Layer.CHUNK_XRAY_FILL, chunkFill);
        addBatch(batches, RtsRecordedGeometry.Layer.CHUNK_XRAY_LINES, chunkLines);
        addBatch(batches, RtsRecordedGeometry.Layer.BOUNDARY, boundary);
        addBatch(batches, RtsRecordedGeometry.Layer.LINES, lines);
        addBatch(batches, RtsRecordedGeometry.Layer.FILLED_BOX, fill);
        addBatch(batches, RtsRecordedGeometry.Layer.BRACKET_QUADS, brackets);
        addBatch(batches, RtsRecordedGeometry.Layer.TARGET_NO_DEPTH_QUADS, targetNoDepth);
        addBatch(batches, RtsRecordedGeometry.Layer.CULLING_HANDLE_NO_DEPTH_FILL, handleFill);
        addBatch(batches, RtsRecordedGeometry.Layer.CULLING_HANDLE_NO_DEPTH_LINES, handleLines);
        return List.copyOf(batches);
    }

    private static void addBatch(
            List<RtsRecordedGeometry.Batch> batches,
            RtsRecordedGeometry.Layer layer,
            RtsRecordedGeometry.Recorder recorder) {
        List<RtsRecordedGeometry.Vertex> vertices = recorder.freeze();
        if (!vertices.isEmpty()) {
            batches.add(new RtsRecordedGeometry.Batch(layer, vertices));
        }
    }
}
