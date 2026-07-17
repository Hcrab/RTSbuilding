package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Build-mode ghost renderer (facade class).
 * <p>
 * Orchestrates the complete rendering pipeline for single-block ghost previews,
 * delegating sub-tasks to dedicated sub-renderers:
 * <ul>
 *   <li>{@link BuildGhostBlockStateResolver} — block state resolution</li>
 *   <li>{@link BuildGhostModelRenderer} — translucent block model rendering</li>
 *   <li>{@link BuildGhostFillRenderer} — fallback fill box rendering</li>
 *   <li>{@link BuildGhostWireframeRenderer} — wireframe outline rendering</li>
 * </ul>
 * <p>
 * Public API is kept for backward compatibility.
 */
public final class BuildGhostRenderer {
    static final float BUILD_GHOST_ALPHA = 0.8F;

    private BuildGhostRenderer() {
    }

    /**
     * 渲染建造模式预览。
     *
     * <p>半透明模型层和蓝色线框层分别由玩家的视觉设置控制。
     * 如果预览线框关闭，这里不能再画蓝色放置描边。</p>
     */
    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            boolean renderBlockGhost, boolean renderWireframe) {
        if (preview == null) {
            return;
        }
        List<BlockPos> blocks = preview.blocks();
        if (blocks.isEmpty()) {
            return;
        }
        BlockPos targetPos = blocks.isEmpty() ? null : blocks.get(0);
        boolean readyConfirm = preview.readyConfirm();

        // 1. Resolve placement-direction BlockState
        BlockState blockState = BuildGhostBlockStateResolver.resolve(minecraft, targetPos);

        // 2. Render translucent block model, entity ghost, or fallback fill
        if (renderBlockGhost) {
            if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
                BuildGhostModelRenderer.renderModels(minecraft, blocks, poseStack, blockState);
            } else {
                // 实体虚影在 26.1 必须走实体 RenderState；抢滩阶段保守退化为方框填充，
                // 不在旧即时渲染阶段调用已经失效的实体 renderer。
                BuildGhostFillRenderer.renderFill(blocks, poseStack, fillBuffer, readyConfirm);
            }
        }

        // 3. 仅在玩家启用预览线框时绘制蓝色描边。
        if (renderWireframe) {
            BuildGhostWireframeRenderer.renderWireframes(blocks, poseStack, lineBuffer, readyConfirm);
        }
    }
}
