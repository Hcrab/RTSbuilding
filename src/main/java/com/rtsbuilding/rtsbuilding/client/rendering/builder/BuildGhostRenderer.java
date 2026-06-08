package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders build-mode preview layers. The translucent block model and the
 * wireframe outline are intentionally independent so player settings can show
 * ghost-only, wireframe-only, both, or neither.
 */
public final class BuildGhostRenderer {
    static final float BUILD_GHOST_ALPHA = 0.8F;

    private BuildGhostRenderer() {
    }

    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            boolean renderBlockGhost, boolean renderWireframe) {
        if (preview == null || (!renderBlockGhost && !renderWireframe)) {
            return;
        }
        BlockState blockState = resolveBuildBlockState(minecraft);
        if (renderBlockGhost) {
            if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
                renderBuildGhostModels(minecraft, preview, poseStack, blockState);
            } else {
                renderBuildFallbackFill(preview, poseStack, fillBuffer);
            }
        }
        if (renderWireframe) {
            renderBuildWireframes(preview, poseStack, lineBuffer);
        }
    }

    private static BlockState resolveBuildBlockState(Minecraft minecraft) {
        ClientRtsController controller = ClientRtsController.get();
        ItemStack itemPreview = controller.getSelectedItemPreview();
        if (!itemPreview.isEmpty() && itemPreview.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock().defaultBlockState();
        }
        if (minecraft != null && minecraft.player != null) {
            ItemStack mainHand = minecraft.player.getMainHandItem();
            if (mainHand.getItem() instanceof BlockItem blockItem) {
                return blockItem.getBlock().defaultBlockState();
            }
        }
        return null;
    }

    private static void renderBuildGhostModels(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, BlockState blockState) {
        List<BlockPos> blocks = preview.blocks();
        if (minecraft == null || blocks.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        MultiBufferSource translucentBuffer = new GhostAlphaBufferSource(blockBuffer, BUILD_GHOST_ALPHA);

        for (BlockPos pos : blocks) {
            int light = minecraft.level == null ? 0xF000F0 : LevelRenderer.getLightColor(minecraft.level, pos);
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            minecraft.getBlockRenderer().renderSingleBlock(
                    blockState, poseStack, translucentBuffer,
                    light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        blockBuffer.endBatch();
    }

    private static void renderBuildFallbackFill(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer fillBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }
        float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
        float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
        float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
        float fillA = preview.readyConfirm() ? 0.22F : 0.16F;

        for (BlockPos pos : blocks) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack, fillBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    fillR, fillG, fillB, fillA);
        }
    }

    private static void renderBuildWireframes(ShapeDataRecords.GhostPreview preview, PoseStack poseStack,
            VertexConsumer lineBuffer) {
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }
        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;

        for (BlockPos pos : blocks) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.renderLineBox(
                    poseStack, lineBuffer,
                    minX, minY, minZ,
                    maxX, maxY, maxZ,
                    lineR, lineG, lineB, 0.95F);
        }
    }
}
