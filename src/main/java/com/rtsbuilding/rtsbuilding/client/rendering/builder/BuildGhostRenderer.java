package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Build 模式虚影渲染器（门面类）。
 * <p>
 * 负责编排单方块放置虚影的完整渲染管道，将各子任务委托给专用的子渲染器：
 * <ul>
 *   <li>{@link BuildGhostBlockStateResolver} — 方块状态解析</li>
 *   <li>{@link BuildGhostModelRenderer} — 半透明方块模型渲染</li>
 *   <li>{@link BuildGhostFillRenderer} — 回退填充色块渲染</li>
 *   <li>{@link BuildGhostWireframeRenderer} — 线框轮廓渲染</li>
 * </ul>
 * <p>
 * 公开 API 保持向后兼容。
 */
public final class BuildGhostRenderer {
    static final float BUILD_GHOST_ALPHA = 0.8F;

    private BuildGhostRenderer() {
    }

    /**
     * 渲染 build 模式的虚影预览。模型层和线框层独立受开关控制。
     */
    static void render(Minecraft minecraft, ShapeDataRecords.GhostPreview preview,
            PoseStack poseStack, VertexConsumer lineBuffer, VertexConsumer fillBuffer,
            boolean renderBlockGhost, boolean renderWireframe) {
        if (preview == null || (!renderBlockGhost && !renderWireframe)) {
            return;
        }
        List<BlockPos> blocks = preview.blocks();
        BlockPos targetPos = blocks.isEmpty() ? null : blocks.get(0);
        boolean readyConfirm = preview.readyConfirm();

        // 1. 解析放置方向的 BlockState
        BlockState blockState = BuildGhostBlockStateResolver.resolve(minecraft, targetPos);

        // 2. 渲染半透明方块模型、实体虚影或回退填充
        if (renderBlockGhost) {
            if (blockState != null && !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL) {
                BuildGhostModelRenderer.renderModels(minecraft, blocks, poseStack, blockState);
            } else {
                ItemStack spawnEggStack = BuildGhostBlockStateResolver.resolveSpawnEggStack(minecraft);
                if (!spawnEggStack.isEmpty()) {
                    EntityGhostRenderer.renderEntities(minecraft, blocks, poseStack, spawnEggStack);
                } else if (!BuildGhostBlockStateResolver.resolveEndCrystalStack(minecraft).isEmpty()) {
                    EntityGhostRenderer.renderEndCrystals(minecraft, blocks, poseStack);
                } else {
                    BuildGhostFillRenderer.renderFill(blocks, poseStack, fillBuffer, readyConfirm);
                }
            }
        }

        // 3. 渲染线框轮廓
        if (renderWireframe) {
            BuildGhostWireframeRenderer.renderWireframes(blocks, poseStack, lineBuffer, readyConfirm);
        }
    }
}
