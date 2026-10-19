package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
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
        // 物理体上以盒子 min 角为锚建立局部帧，改画 0..size 的局部盒，避免大 plot 坐标漂移。
        BlockPos anchor = BlockPos.containing(renderBox.minX, renderBox.minY, renderBox.minZ);
        RtsSableClientSpatialCompat.renderInBlockFrame(minecraft.level, anchor, poseStack, localFrame -> {
            AABB box = localFrame
                    ? renderBox.move(-anchor.getX(), -anchor.getY(), -anchor.getZ())
                    : renderBox;
            if (!screen.isQuickBuildRangeDestroyMode()) {
                AABB outline = box.inflate(0.015D);
                float red = UiThemeWorldColors.red(UiThemeWorldColors.SHAPE_SELECTION);
                float green = UiThemeWorldColors.green(UiThemeWorldColors.SHAPE_SELECTION);
                float blue = UiThemeWorldColors.blue(UiThemeWorldColors.SHAPE_SELECTION);
                LevelRenderer.renderLineBox(
                        poseStack,
                        handleLineBuffer,
                        outline.minX, outline.minY, outline.minZ,
                        outline.maxX, outline.maxY, outline.maxZ,
                        red, green, blue, 0.82F);
            }
            if (!screen.isAdvancedShapeMode()) {
                return;
            }
            RtsCullingBox cullingBox = screen.getShapeController().advancedRangeDestroyBox();
            if (cullingBox == null) {
                return;
            }
            RtsBoxHandleRenderer.renderAxisHandles(
                    poseStack,
                    handleLineBuffer,
                    handleFillBuffer,
                    box,
                    screen.getShapeController().advancedRangeDestroyHoveredHandle(),
                    screen.getShapeController().advancedRangeDestroyActiveHandle(),
                    screen.getShapeController().advancedRangeDestroyAllowedHandleDirections());
        });
    }
}
