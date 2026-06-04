package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 交互目标高亮渲染器
 * 负责渲染玩家鼠标悬停的方块或实体，提供视觉反馈
 */
public final class InteractionTargetRenderer {

    /**
     * 私有构造函数，防止实例化
     */
    private InteractionTargetRenderer() {
    }

    /**
     * 渲染当前鼠标悬停的交互目标（方块或实体）
     *
     * @param minecraft Minecraft客户端实例
     * @param controller RTS控制器
     * @param poseStack 姿势栈，用于坐标变换
     * @param lineBuffer 线条缓冲区
     */
    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller,
            PoseStack poseStack, VertexConsumer lineBuffer) {
        // 在旋转捕获模式或未加载世界时不渲染
        if (controller.isRotateCaptured() || minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        // 计算鼠标射线
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = RaycastHelper.computeCursorRayDirection(minecraft);
        Vec3 to = camPos.add(viewDir.scale(128.0D));

        // 执行射线检测
        BlockHitResult blockHit = RaycastHelper.raycastBlockFromCursor(minecraft, camPos, to, false);
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(minecraft, camPos, to, viewDir, 128.0D);

        // 计算距离，优先选择更近的目标
        double blockDist = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        // 如果实体更近，渲染实体边框
        if (entityHit != null && entityDist <= blockDist) {
            renderEntityHighlight(poseStack, lineBuffer, entityHit.getEntity());
            return;
        }

        // 如果没有命中方块，直接返回
        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        // 渲染方块高亮
        renderBlockHighlight(minecraft, poseStack, lineBuffer, blockHit.getBlockPos());
    }

    /**
     * 渲染实体高亮框（绿色）
     *
     * @param poseStack 姿势栈
     * @param lineBuffer 线条缓冲区
     * @param entity 目标实体
     */
    private static void renderEntityHighlight(PoseStack poseStack, VertexConsumer lineBuffer, Entity entity) {
        AABB bb = entity.getBoundingBox().inflate(0.03D);
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                bb.minX, bb.minY, bb.minZ,
                bb.maxX, bb.maxY, bb.maxZ,
                0.35F, 1.0F, 0.55F, 1.0F);  // 绿色
    }

    /**
     * 渲染方块高亮（黄色）
     * 根据方块状态选择合适的渲染方式
     *
     * @param minecraft Minecraft客户端实例
     * @param poseStack 姿势栈
     * @param lineBuffer 线条缓冲区
     * @param pos 方块位置
     */
    private static void renderBlockHighlight(Minecraft minecraft, PoseStack poseStack, VertexConsumer lineBuffer, BlockPos pos) {
        BlockState state = null;
        if (minecraft.level != null) {
            state = minecraft.level.getBlockState(pos);
        }

        // 空气方块：渲染完整立方体
        if (state != null && state.isAir()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);  // 黄色
            return;
        }

        // 获取方块的碰撞形状
        VoxelShape shape = null;
        if (state != null) {
            shape = state.getShape(minecraft.level, pos);
        }

        // 空形状：渲染完整立方体
        if (shape != null && shape.isEmpty()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);
            return;
        }

        // 有形状：按实际碰撞盒渲染（支持楼梯、台阶等不规则方块）
        if (shape != null) {
            for (AABB box : shape.toAabbs()) {
                LevelRenderer.renderLineBox(
                        poseStack,
                        lineBuffer,
                        pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ,
                        pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ,
                        1.0F, 0.95F, 0.2F, 1.0F);
            }
        }
    }
}
