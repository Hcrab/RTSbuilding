package com.rtsbuilding.rtsbuilding.client.rendering.culling;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.selection.RtsBoxHandleRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.AABB;

/** 渲染范围剔除盒子的世界空间预览。 */
public final class RtsCullingRenderer {
  private RtsCullingRenderer() {}

  public static void render(
      PoseStack poseStack,
      VertexConsumer lineBuffer,
      VertexConsumer fillBuffer,
      VertexConsumer handleLineBuffer,
      VertexConsumer handleFillBuffer) {
    RtsCullingManager manager = RtsCullingClientState.activeManager();
    if (manager == null || !manager.isManagementMode()) {
      return;
    }
    for (RtsCullingBox box : manager.boxes()) {
      AABB renderBox = manager.renderAabb(box);
      if (box.id() == manager.hoveredId()) {
        renderBox(
            poseStack,
            lineBuffer,
            fillBuffer,
            renderBox,
            UiThemeWorldColors.CULLING_HOVER,
            0.22F,
            0.95F);
      } else if (box.id() == manager.selectedId()) {
        renderBox(
            poseStack,
            lineBuffer,
            fillBuffer,
            renderBox,
            UiThemeWorldColors.CULLING_SELECTED,
            0.18F,
            0.98F);
      } else {
        renderBox(
            poseStack,
            lineBuffer,
            fillBuffer,
            renderBox,
            UiThemeWorldColors.CULLING_IDLE,
            0.12F,
            0.82F);
      }
    }
    manager
        .selectedBox()
        .ifPresent(
            box ->
                RtsBoxHandleRenderer.renderAxisHandles(
                    poseStack,
                    handleLineBuffer,
                    handleFillBuffer,
                    manager.renderAabb(box),
                    manager.hoveredHandleDirection(),
                    manager.activeHandleDirection()));
    RtsCullingBox preview = manager.previewBox();
    if (preview != null) {
      renderBox(
          poseStack,
          lineBuffer,
          fillBuffer,
          preview.asAabb(),
          UiThemeWorldColors.CULLING_HOVER,
          0.16F,
          0.92F);
    }
  }

  private static void renderBox(
      PoseStack poseStack,
      VertexConsumer lineBuffer,
      VertexConsumer fillBuffer,
      AABB box,
      UiColor color,
      float fillAlpha,
      float lineAlpha) {
    renderBox(
        poseStack,
        lineBuffer,
        fillBuffer,
        box,
        UiThemeWorldColors.red(color),
        UiThemeWorldColors.green(color),
        UiThemeWorldColors.blue(color),
        fillAlpha,
        lineAlpha);
  }

  private static void renderBox(
      PoseStack poseStack,
      VertexConsumer lineBuffer,
      VertexConsumer fillBuffer,
      AABB box,
      float r,
      float g,
      float b,
      float fillAlpha,
      float lineAlpha) {
    double minX = box.minX - 0.01D;
    double minY = box.minY - 0.01D;
    double minZ = box.minZ - 0.01D;
    double maxX = box.maxX + 0.01D;
    double maxY = box.maxY + 0.01D;
    double maxZ = box.maxZ + 0.01D;
    LevelRenderer.addChainedFilledBoxVertices(
        poseStack, fillBuffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, fillAlpha);
    LevelRenderer.renderLineBox(
        poseStack, lineBuffer, minX, minY, minZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
  }
}
