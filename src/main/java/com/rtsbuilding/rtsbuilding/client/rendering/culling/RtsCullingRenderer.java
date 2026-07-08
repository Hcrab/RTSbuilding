package com.rtsbuilding.rtsbuilding.client.rendering.culling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingAxisHandle;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/**
 * 渲染范围剔除盒子的世界空间预览。
 */
public final class RtsCullingRenderer {
    private static final float BLUE_R = 0.20F;
    private static final float BLUE_G = 0.56F;
    private static final float BLUE_B = 1.00F;
    private static final float YELLOW_R = 1.00F;
    private static final float YELLOW_G = 0.82F;
    private static final float YELLOW_B = 0.16F;
    private static final float SELECT_R = 0.56F;
    private static final float SELECT_G = 0.84F;
    private static final float SELECT_B = 1.00F;
    private static final float HANDLE_X_R = 1.00F;
    private static final float HANDLE_X_G = 0.34F;
    private static final float HANDLE_X_B = 0.32F;
    private static final float HANDLE_Y_R = 0.36F;
    private static final float HANDLE_Y_G = 1.00F;
    private static final float HANDLE_Y_B = 0.42F;
    private static final float HANDLE_Z_R = 0.38F;
    private static final float HANDLE_Z_G = 0.64F;
    private static final float HANDLE_Z_B = 1.00F;
    private static final float ACTIVE_R = 1.00F;
    private static final float ACTIVE_G = 0.78F;
    private static final float ACTIVE_B = 0.18F;

    private RtsCullingRenderer() {
    }

    public static void render(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            VertexConsumer handleLineBuffer, VertexConsumer handleFillBuffer) {
        RtsCullingManager manager = RtsCullingClientState.activeManager();
        if (manager == null || !manager.isManagementMode()) {
            return;
        }
        for (RtsCullingBox box : manager.boxes()) {
            AABB renderBox = manager.renderAabb(box);
            if (box.id() == manager.hoveredId()) {
                renderBox(poseStack, lineBuffer, fillBuffer, renderBox, YELLOW_R, YELLOW_G, YELLOW_B, 0.22F, 0.95F);
            } else if (box.id() == manager.selectedId()) {
                renderBox(poseStack, lineBuffer, fillBuffer, renderBox, SELECT_R, SELECT_G, SELECT_B, 0.18F, 0.98F);
            } else {
                renderBox(poseStack, lineBuffer, fillBuffer, renderBox, BLUE_R, BLUE_G, BLUE_B, 0.12F, 0.82F);
            }
        }
        manager.selectedBox().ifPresent(box -> renderAxisHandles(
                poseStack,
                handleLineBuffer,
                handleFillBuffer,
                manager.renderAabb(box),
                manager.hoveredHandleDirection(),
                manager.activeHandleDirection()));
        RtsCullingBox preview = manager.previewBox();
        if (preview != null) {
            renderBox(poseStack, lineBuffer, fillBuffer, preview.asAabb(), YELLOW_R, YELLOW_G, YELLOW_B, 0.16F, 0.92F);
        }
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            AABB box, float r, float g, float b, float fillAlpha, float lineAlpha) {
        double minX = box.minX - 0.01D;
        double minY = box.minY - 0.01D;
        double minZ = box.minZ - 0.01D;
        double maxX = box.maxX + 0.01D;
        double maxY = box.maxY + 0.01D;
        double maxZ = box.maxZ + 0.01D;
        LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                minX, minY, minZ, maxX, maxY, maxZ, r, g, b, fillAlpha);
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                minX, minY, minZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
    }

    private static void renderAxisHandles(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            AABB box, Direction hoveredDirection, Direction activeDirection) {
        for (RtsCullingAxisHandle.Handle handle : RtsCullingAxisHandle.handles(box)) {
            boolean hovered = handle.direction() == hoveredDirection;
            boolean active = handle.direction() == activeDirection;
            AxisColor axisColor = color(handle.axis());
            AxisColor color = active ? new AxisColor(ACTIVE_R, ACTIVE_G, ACTIVE_B)
                    : hovered ? highlight(axisColor)
                    : axisColor;
            float fillAlpha = active ? 0.58F : hovered ? 0.42F : 0.22F;
            float lineAlpha = active ? 1.00F : hovered ? 0.95F : 0.70F;
            if (hovered && !active) {
                renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.shaft().inflate(0.05D),
                        color, 0.10F, 0.30F);
                renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.head().inflate(0.07D),
                        color, 0.12F, 0.38F);
            }
            if (active) {
                renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.shaft().inflate(0.06D),
                        color, 0.16F, 0.42F);
                renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.head().inflate(0.08D),
                        color, 0.20F, 0.54F);
            }
            renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.shaft(), color, fillAlpha, lineAlpha);
            renderHandleBox(poseStack, lineBuffer, fillBuffer, handle.head(), color, fillAlpha, lineAlpha);
        }
    }

    private static void renderHandleBox(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            AABB box, AxisColor color, float fillAlpha, float lineAlpha) {
        LevelRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                color.r(), color.g(), color.b(), fillAlpha);
        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                color.r(), color.g(), color.b(), lineAlpha);
    }

    private static AxisColor color(Direction.Axis axis) {
        return switch (axis) {
            case X -> new AxisColor(HANDLE_X_R, HANDLE_X_G, HANDLE_X_B);
            case Y -> new AxisColor(HANDLE_Y_R, HANDLE_Y_G, HANDLE_Y_B);
            case Z -> new AxisColor(HANDLE_Z_R, HANDLE_Z_G, HANDLE_Z_B);
        };
    }

    private static AxisColor highlight(AxisColor color) {
        return new AxisColor(
                color.r() + (1.0F - color.r()) * 0.18F,
                color.g() + (1.0F - color.g()) * 0.18F,
                color.b() + (1.0F - color.b()) * 0.18F);
    }

    private record AxisColor(float r, float g, float b) {
    }
}
