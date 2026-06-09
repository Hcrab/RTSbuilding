package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rtsbuilding.rtsbuilding.client.rendering.util.GhostAlphaBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 生物实体放置虚影渲染器。
 * <p>
 * 当玩家手持刷怪蛋(Spawn Egg)或末地水晶(End Crystal)在快速建筑模式下预览放置位置时，
 * 以半透明方式在目标方块位置渲染对应的生物实体模型，
 * 提供即时视觉反馈。
 */
public final class EntityGhostRenderer {

    private static final float GHOST_ALPHA = 0.75F;
    private static final float ENTITY_SCALE = 0.95F;

    private EntityGhostRenderer() {
    }

    /**
     * 渲染刷怪蛋生物实体虚影。
     *
     * @param minecraft Minecraft 客户端实例
     * @param blocks    目标方块位置列表
     * @param poseStack 姿势栈
     * @param itemStack 刷怪蛋物品栈
     */
    public static void renderEntities(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack, ItemStack itemStack) {
        if (minecraft == null || minecraft.level == null || blocks == null || blocks.isEmpty()
                || itemStack == null || itemStack.isEmpty()) {
            return;
        }
        if (!(itemStack.getItem() instanceof SpawnEggItem spawnEggItem)) {
            return;
        }
        EntityType<?> entityType = spawnEggItem.getType(itemStack);
        if (entityType == null) {
            return;
        }
        Entity entity = entityType.create(minecraft.level);
        if (entity == null) {
            return;
        }
        renderEntityGhost(minecraft, blocks, poseStack, entity);
    }

    /**
     * 渲染末地水晶虚影。
     *
     * @param minecraft Minecraft 客户端实例
     * @param blocks    目标方块位置列表
     * @param poseStack 姿势栈
     */
    public static void renderEndCrystals(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack) {
        if (minecraft == null || minecraft.level == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        Entity entity = EntityType.END_CRYSTAL.create(minecraft.level);
        if (entity == null) {
            return;
        }
        renderEntityGhost(minecraft, blocks, poseStack, entity);
    }

    /**
     * 通用实体虚影渲染逻辑。
     */
    private static void renderEntityGhost(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack, Entity entity) {
        // 禁用重力，防止实体在渲染时产生位置偏移
        entity.setNoGravity(true);

        // yOffset = 0：实体脚底对齐方块 Y 坐标（点击位置）
        double yOffset = 0.0;

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        // 保留原始 RenderType 只修改 alpha，确保实体纹理正确渲染
        MultiBufferSource alphaBuffer = renderType ->
                new GhostAlphaBufferSource.GhostAlphaVertexConsumer(
                        bufferSource.getBuffer(renderType), GHOST_ALPHA);

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();

        for (BlockPos pos : blocks) {
            // 计算面向玩家的偏航角
            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dz = pos.getZ() + 0.5 - cameraPos.z;
            float yaw = (float) Math.toDegrees(Mth.atan2(-dx, dz));

            // 重置插值用旋转旧值，防止跨帧渲染错乱
            entity.setPos(pos.getX() + 0.5, pos.getY() + yOffset, pos.getZ() + 0.5);
            entity.setYRot(yaw);
            entity.setXRot(0);
            entity.xRotO = 0;
            entity.yRotO = yaw;
            if (entity instanceof LivingEntity living) {
                living.yHeadRot = yaw;
                living.yHeadRotO = yaw;
                living.yBodyRot = yaw;
                living.yBodyRotO = yaw;
            }

            int packedLight = minecraft.level != null
                    ? LevelRenderer.getLightColor(minecraft.level, pos)
                    : 0xF000F0;

            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            poseStack.scale(ENTITY_SCALE, ENTITY_SCALE, ENTITY_SCALE);

            dispatcher.render(entity, 0.5, yOffset, 0.5,
                    yaw, partialTick, poseStack, alphaBuffer, packedLight);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}
