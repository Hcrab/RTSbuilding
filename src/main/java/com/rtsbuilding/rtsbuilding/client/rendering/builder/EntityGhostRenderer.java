package com.rtsbuilding.rtsbuilding.client.rendering.builder;


import java.util.List;

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

/** Translucent entity previews for spawn eggs and end crystals. */
final class EntityGhostRenderer {
    private static final float GHOST_ALPHA = 0.75F;
    private static final float ENTITY_SCALE = 0.95F;

    private EntityGhostRenderer() {
    }

    static void renderEntities(Minecraft minecraft, List<BlockPos> blocks, PoseStack poseStack, ItemStack itemStack) {
        if (minecraft == null || minecraft.level == null || blocks == null || blocks.isEmpty()
                || itemStack == null || itemStack.isEmpty()
                || !(itemStack.getItem() instanceof SpawnEggItem spawnEggItem)) {
            return;
        }
        EntityType<?> entityType = spawnEggItem.getType(itemStack.getTag());
        Entity entity = entityType == null ? null : entityType.create(minecraft.level);
        if (entity != null) {
            renderEntityGhost(minecraft, blocks, poseStack, entity);
        }
    }

    static void renderEndCrystals(Minecraft minecraft, List<BlockPos> blocks, PoseStack poseStack) {
        if (minecraft == null || minecraft.level == null || blocks == null || blocks.isEmpty()) {
            return;
        }
        Entity entity = EntityType.END_CRYSTAL.create(minecraft.level);
        if (entity != null) {
            renderEntityGhost(minecraft, blocks, poseStack, entity);
        }
    }

    private static void renderEntityGhost(Minecraft minecraft, List<BlockPos> blocks, PoseStack poseStack, Entity entity) {
        entity.setNoGravity(true);
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        MultiBufferSource alphaBuffer = renderType -> new GhostAlphaBufferSource.GhostAlphaVertexConsumer(
                bufferSource.getBuffer(renderType), GHOST_ALPHA);
        float partialTick = minecraft.getFrameTime();
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().getPosition();

        for (BlockPos pos : blocks) {
            double dx = pos.getX() + 0.5D - cameraPos.x;
            double dz = pos.getZ() + 0.5D - cameraPos.z;
            float yaw = (float) Math.toDegrees(Mth.atan2(-dx, dz));
            entity.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
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

            int packedLight = minecraft.level != null ? LevelRenderer.getLightColor(minecraft.level, pos) : 0xF000F0;
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            poseStack.scale(ENTITY_SCALE, ENTITY_SCALE, ENTITY_SCALE);
            dispatcher.render(entity, 0.5D, 0.0D, 0.5D, yaw, partialTick, poseStack, alphaBuffer, packedLight);
            poseStack.popPose();
        }
        bufferSource.endBatch();
    }
}
