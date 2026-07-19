package com.rtsbuilding.rtsbuilding.platform.neoforge.client.rendering;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsWorldPreviewExtractor;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsWorldPreviewSnapshot;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsRecordedGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge 26.1 世界预览“转换插头”。
 *
 * <p>提取事件冻结业务快照，提交事件把快照转换为 collector 节点。此类是唯一
 * 认识 NeoForge 提交事件的方块虚影代码，可被未来 Loader adapter 整体替换。</p>
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class NeoForgeWorldPreviewBridge {
    private static final Identifier BOUNDARY_TEXTURE =
            Identifier.fromNamespaceAndPath("minecraft", "textures/misc/forcefield.png");
    private static final ContextKey<RtsWorldPreviewSnapshot> PREVIEW_STATE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(
                    RtsbuildingMod.MODID, "world_preview_snapshot"));

    private NeoForgeWorldPreviewBridge() {
    }

    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        event.getRenderState().setRenderData(
                PREVIEW_STATE,
                RtsWorldPreviewExtractor.capture(
                        Minecraft.getInstance(),
                        event.getCamera().position()));
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        RtsWorldPreviewSnapshot snapshot =
                event.getLevelRenderState().getRenderData(PREVIEW_STATE);
        if (snapshot == null) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (RtsWorldPreviewSnapshot.ModelGhost ghost : snapshot.modelGhosts()) {
            submitGhost(event, camera, ghost);
        }
        for (RtsRecordedGeometry.Batch batch : snapshot.geometryBatches()) {
            submitGeometryBatch(event, camera, batch);
        }
    }

    private static void submitGhost(
            SubmitCustomGeometryEvent event,
            Vec3 camera,
            RtsWorldPreviewSnapshot.ModelGhost ghost) {
        var model = Minecraft.getInstance()
                .getModelManager()
                .getBlockStateModelSet()
                .get(ghost.state());
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(
                RandomSource.create(ghost.state().getSeed(ghost.pos())),
                parts);
        if (parts.isEmpty()) {
            return;
        }
        List<BlockStateModelPart> frozenParts = List.copyOf(parts);
        int alpha = Math.round(ghost.alpha() * 255.0F);

        event.getPoseStack().pushPose();
        event.getPoseStack().translate(
                ghost.pos().getX() - camera.x,
                ghost.pos().getY() - camera.y,
                ghost.pos().getZ() - camera.z);
        if (ghost.scale() != 1.0F) {
            event.getPoseStack().translate(0.5D, 0.5D, 0.5D);
            event.getPoseStack().scale(ghost.scale(), ghost.scale(), ghost.scale());
            event.getPoseStack().translate(-0.5D, -0.5D, -0.5D);
        }
        event.getSubmitNodeCollector().submitCustomGeometry(
                event.getPoseStack(),
                RenderTypes.translucentMovingBlock(),
                (pose, consumer) -> renderParts(ghost, frozenParts, alpha, pose, consumer));
        event.getPoseStack().popPose();
    }

    private static void submitGeometryBatch(
            SubmitCustomGeometryEvent event,
            Vec3 camera,
            RtsRecordedGeometry.Batch batch) {
        if (batch.vertices().isEmpty()) {
            return;
        }
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(-camera.x, -camera.y, -camera.z);
        boolean requireLineWidth = requiresLineWidth(batch.layer());
        event.getSubmitNodeCollector().submitCustomGeometry(
                event.getPoseStack(),
                renderTypeFor(batch.layer()),
                (pose, consumer) -> {
                    for (RtsRecordedGeometry.Vertex vertex : batch.vertices()) {
                        vertex.replay(pose, consumer, requireLineWidth);
                    }
                });
        event.getPoseStack().popPose();
    }

    private static boolean requiresLineWidth(RtsRecordedGeometry.Layer layer) {
        return switch (layer) {
            case LINES, CHUNK_XRAY_LINES, CULLING_HANDLE_NO_DEPTH_LINES -> true;
            default -> false;
        };
    }

    private static RenderType renderTypeFor(RtsRecordedGeometry.Layer layer) {
        return switch (layer) {
            case BOUNDARY -> RenderTypes.entityTranslucent(BOUNDARY_TEXTURE);
            case LINES, CHUNK_XRAY_LINES, CULLING_HANDLE_NO_DEPTH_LINES ->
                    RenderTypes.linesTranslucent();
            case BRACKET_QUADS, TARGET_NO_DEPTH_QUADS -> RenderTypes.debugQuads();
            case FILLED_BOX, CHUNK_XRAY_FILL, CULLING_HANDLE_NO_DEPTH_FILL ->
                    RenderTypes.debugFilledBox();
        };
    }

    private static void renderParts(
            RtsWorldPreviewSnapshot.ModelGhost ghost,
            List<BlockStateModelPart> parts,
            int alpha,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose,
            VertexConsumer consumer) {
        for (BlockStateModelPart part : parts) {
            renderQuads(ghost, part.getQuads(null), alpha, pose, consumer);
            for (Direction direction : Direction.values()) {
                renderQuads(ghost, part.getQuads(direction), alpha, pose, consumer);
            }
        }
    }

    private static void renderQuads(
            RtsWorldPreviewSnapshot.ModelGhost ghost,
            List<BakedQuad> quads,
            int alpha,
            com.mojang.blaze3d.vertex.PoseStack.Pose pose,
            VertexConsumer consumer) {
        for (BakedQuad quad : quads) {
            int tintIndex = quad.materialInfo().tintIndex();
            int tint = tintIndex < 0
                    ? 0xFFFFFFFF
                    : ghost.tintColors().getOrDefault(tintIndex, 0xFFFFFFFF);
            int color = (alpha << 24) | (tint & 0x00FFFFFF);
            QuadInstance instance = new QuadInstance();
            instance.setColor(color);
            instance.setLightCoords(ghost.packedLight());
            instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
            consumer.putBakedQuad(pose, quad, instance);
        }
    }
}
