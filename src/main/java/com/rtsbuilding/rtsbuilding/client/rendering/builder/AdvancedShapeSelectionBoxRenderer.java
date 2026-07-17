package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;

/**
 * 快速建造/破坏形状选择框的共享渲染入口。
 *
 * <p>普通和高级模式共用同一个平滑包围盒；高级模式只是在其上增加六向/四向/双向箭头。
 * 具体方块预览仍由 {@link ShapeGhostRenderer} 按建造或破坏语义分别绘制。</p>
 */
public final class AdvancedShapeSelectionBoxRenderer {
    private AdvancedShapeSelectionBoxRenderer() {
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer handleLineBuffer, VertexConsumer handleFillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            return;
        }
        AABB renderBox = screen.getShapeController().shapeSelectionRenderAabb();
        if (renderBox == null) {
            return;
        }
        if (!screen.isQuickBuildRangeDestroyMode()) {
            AABB outline = renderBox.inflate(0.015D);
            com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                    poseStack,
                    handleLineBuffer,
                    outline.minX, outline.minY, outline.minZ,
                    outline.maxX, outline.maxY, outline.maxZ,
                    0.30F, 0.75F, 1.00F, 0.82F);
        }
        if (!screen.isAdvancedShapeMode()) {
            return;
        }
        RtsCullingBox box = screen.getShapeController().advancedRangeDestroyBox();
        if (box == null) {
            return;
        }
        RtsBoxHandleRenderer.renderAxisHandles(
                poseStack,
                handleLineBuffer,
                handleFillBuffer,
                renderBox,
                screen.getShapeController().advancedRangeDestroyHoveredHandle(),
                screen.getShapeController().advancedRangeDestroyActiveHandle(),
                screen.getShapeController().advancedRangeDestroyAllowedHandleDirections());
    }
}
