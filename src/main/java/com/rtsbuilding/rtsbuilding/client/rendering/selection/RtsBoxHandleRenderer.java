package com.rtsbuilding.rtsbuilding.client.rendering.selection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingAxisHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.Set;

/**
 * 世界空间盒子编辑手柄的统一渲染器。
 *
 * <p>范围剔除和蓝图框选共用同一组六向箭头：轴颜色、悬停高亮和锁定金色都必须保持一致。
 * 这里不关心盒子的业务含义，也不绘制外框颜色；调用方只传入当前用于渲染的 AABB。</p>
 */
public final class RtsBoxHandleRenderer {
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

    private RtsBoxHandleRenderer() {
    }

    public static void renderAxisHandles(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            AABB box, Direction hoveredDirection, Direction activeDirection) {
        renderAxisHandles(poseStack, lineBuffer, fillBuffer, box, hoveredDirection, activeDirection, null);
    }

    public static void renderAxisHandles(PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            AABB box, Direction hoveredDirection, Direction activeDirection, Set<Direction> allowedDirections) {
        if (box == null) {
            return;
        }
        for (RtsCullingAxisHandle.Handle handle : RtsCullingAxisHandle.handles(box, allowedDirections)) {
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
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(poseStack, fillBuffer,
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                color.r(), color.g(), color.b(), fillAlpha);
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(poseStack, lineBuffer,
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
