package com.rtsbuilding.rtsbuilding.client.rendering.animation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostBlockModelRenderer;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Brief grow-in overlay shown after the server confirms a remote placement.
 * This is visual feedback only; the real block is already server-authoritative.
 */
final class ConfirmedPlacementRenderer {
    private static final long PLACE_DURATION_MS = 220L;
    private static final float MODEL_ALPHA = 0.58F;

    private static final Map<Long, PlacementEntry> PLACEMENTS = new LinkedHashMap<>();

    private ConfirmedPlacementRenderer() {
    }

    static void add(BlockPos pos, BlockState state) {
        if (pos == null || state == null || state.isAir()) {
            return;
        }
        PLACEMENTS.put(pos.asLong(), new PlacementEntry(pos.immutable(), state, System.currentTimeMillis()));
    }

    static void renderModels(Minecraft minecraft, PoseStack poseStack, VertexConsumer fillBuffer) {
        if (minecraft == null || minecraft.level == null || PLACEMENTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        MultiBufferSource.BufferSource blockBuffer = minecraft.renderBuffers().bufferSource();
        Iterator<Map.Entry<Long, PlacementEntry>> iterator = PLACEMENTS.entrySet().iterator();

        while (iterator.hasNext()) {
            PlacementEntry entry = iterator.next().getValue();
            long elapsed = now - entry.addedAtMs;
            if (elapsed > PLACE_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (!isWithinBounds(minecraft, entry.pos)) {
                continue;
            }
            float scale = computeGrowScale(elapsed);
            RtsSableClientSpatialCompat.renderInFrame(minecraft.level, entry.pos, poseStack, () -> {
                if (entry.state.getRenderShape() == RenderShape.MODEL) {
                    GhostBlockModelRenderer.renderAt(minecraft, poseStack, blockBuffer,
                            entry.state, entry.pos, MODEL_ALPHA, scale);
                } else {
                    renderFilledBox(poseStack, fillBuffer, entry.pos, scale);
                }
            });
        }
        blockBuffer.endBatch();
    }

    static void renderWireframes(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer) {
        if (PLACEMENTS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Long, PlacementEntry>> iterator = PLACEMENTS.entrySet().iterator();
        while (iterator.hasNext()) {
            PlacementEntry entry = iterator.next().getValue();
            long elapsed = now - entry.addedAtMs;
            if (elapsed > PLACE_DURATION_MS) {
                iterator.remove();
                continue;
            }
            if (!isWithinBounds(minecraft, entry.pos)) {
                continue;
            }
            float scale = computeGrowScale(elapsed);
            float red = UiThemeWorldColors.red(UiThemeWorldColors.PLACEMENT_CONFIRMED);
            float green = UiThemeWorldColors.green(UiThemeWorldColors.PLACEMENT_CONFIRMED);
            float blue = UiThemeWorldColors.blue(UiThemeWorldColors.PLACEMENT_CONFIRMED);
            RtsSableClientSpatialCompat.renderInFrame(minecraft.level, entry.pos, poseStack,
                    () -> renderLineBox(poseStack, lineBuffer, entry.pos, scale,
                            red, green, blue, 0.82F));
        }
    }

    private static void renderFilledBox(PoseStack poseStack, VertexConsumer fillBuffer, BlockPos pos, float scale) {
        double inset = 0.5D - scale * 0.46D;
        float red = UiThemeWorldColors.red(UiThemeWorldColors.PLACEMENT_CONFIRMED_FILL);
        float green = UiThemeWorldColors.green(UiThemeWorldColors.PLACEMENT_CONFIRMED_FILL);
        float blue = UiThemeWorldColors.blue(UiThemeWorldColors.PLACEMENT_CONFIRMED_FILL);
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack, fillBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                red, green, blue, 0.16F);
    }

    private static void renderLineBox(PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos, float scale,
            float r, float g, float b, float alpha) {
        double inset = 0.5D - scale * 0.46D;
        LevelRenderer.renderLineBox(
                poseStack, lineBuffer,
                pos.getX() + inset, pos.getY() + inset, pos.getZ() + inset,
                pos.getX() + 1.0D - inset, pos.getY() + 1.0D - inset, pos.getZ() + 1.0D - inset,
                r, g, b, alpha);
    }

    private static float computeGrowScale(long elapsedMs) {
        float progress = Math.min(1.0F, Math.max(0.0F, elapsedMs / (float) PLACE_DURATION_MS));
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        return 0.12F + eased * 0.86F;
    }

    private static boolean isWithinBounds(Minecraft minecraft, BlockPos pos) {
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) return true;
        return RtsSableClientSpatialCompat.isWithinBounds(
                minecraft.level, pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private record PlacementEntry(BlockPos pos, BlockState state, long addedAtMs) {
    }
}
