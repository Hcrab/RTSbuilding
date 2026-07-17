package com.rtsbuilding.rtsbuilding.platform.neoforge.client.rendering;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsWorldPreviewExtractor;
import com.rtsbuilding.rtsbuilding.client.rendering.state.RtsWorldPreviewSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
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
    private static final ContextKey<RtsWorldPreviewSnapshot> PREVIEW_STATE =
            new ContextKey<>(Identifier.fromNamespaceAndPath(
                    RtsbuildingMod.MODID, "world_preview_snapshot"));

    private NeoForgeWorldPreviewBridge() {
    }

    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        event.getRenderState().setRenderData(
                PREVIEW_STATE,
                RtsWorldPreviewExtractor.capture(Minecraft.getInstance()));
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        RtsWorldPreviewSnapshot snapshot =
                event.getLevelRenderState().getRenderData(PREVIEW_STATE);
        if (snapshot == null || snapshot.modelGhosts().isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        for (RtsWorldPreviewSnapshot.ModelGhost ghost : snapshot.modelGhosts()) {
            submitGhost(event, camera, ghost);
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
        event.getSubmitNodeCollector().submitCustomGeometry(
                event.getPoseStack(),
                RenderTypes.translucentMovingBlock(),
                (pose, consumer) -> renderParts(ghost, frozenParts, alpha, pose, consumer));
        event.getPoseStack().popPose();
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
