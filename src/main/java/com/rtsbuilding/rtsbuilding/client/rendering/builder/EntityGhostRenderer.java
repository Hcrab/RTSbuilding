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
 * Ghost entity renderer for spawn eggs and end crystals in quick-build mode.
 * <p>
 * When the player holds a Spawn Egg or End Crystal while previewing a
 * quick-build placement, renders translucent entity models at the target
 * block positions for instant visual feedback.
 */
public final class EntityGhostRenderer {

    private static final float GHOST_ALPHA = 0.75F;
    private static final float ENTITY_SCALE = 0.95F;

    private EntityGhostRenderer() {
    }

    /**
     * Renders spawn egg entity ghosts at the target positions.
     *
     * @param minecraft Minecraft client instance
     * @param blocks    Target block positions
     * @param poseStack Pose stack for coordinate transforms
     * @param itemStack Spawn egg item stack
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
     * Renders end crystal entity ghosts at the target positions.
     *
     * @param minecraft Minecraft client instance
     * @param blocks    Target block positions
     * @param poseStack Pose stack for coordinate transforms
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
     * Shared entity ghost rendering logic.
     */
    private static void renderEntityGhost(Minecraft minecraft, List<BlockPos> blocks,
            PoseStack poseStack, Entity entity) {
        // Disable gravity to prevent position drift during rendering
        entity.setNoGravity(true);

        // yOffset = 0: entity feet align with block Y coordinate (click position)
        double yOffset = 0.0;

        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();

        // Preserve original RenderType, only modify alpha to keep entity textures correct
        MultiBufferSource alphaBuffer = renderType ->
                new GhostAlphaBufferSource.GhostAlphaVertexConsumer(
                        bufferSource.getBuffer(renderType), GHOST_ALPHA);

        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 cameraPos = minecraft.gameRenderer.getMainCamera().position();

        for (BlockPos pos : blocks) {
            // Calculate yaw facing the player
            double dx = pos.getX() + 0.5 - cameraPos.x;
            double dz = pos.getZ() + 0.5 - cameraPos.z;
            float yaw = (float) Math.toDegrees(Mth.atan2(-dx, dz));

            // Reset interpolation rotation old values to prevent cross-frame glitches
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
