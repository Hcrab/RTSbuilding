package com.rtsbuilding.rtsbuilding.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Renders RTS overlay elements: linked-storage highlights,
 * hovered-target highlight, and shape-ghost preview.
 * <p>
 * Chunk curtain / chunk boundary rendering has been removed as a workaround
 * for an Oculus/Iris G-buffer contamination bug (duplicate / misaligned
 * lines when shaders are active).  A proper fix will be applied once the
 * matching 0.0.4 source code is available.
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsBoundaryRenderer {

    private RtsBoundaryRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        final ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        final Vec3 camPos = event.getCamera().getPosition();
        final PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        final MultiBufferSource.BufferSource bufferSource =
                minecraft.renderBuffers().bufferSource();
        final VertexConsumer lineBuffer =
                bufferSource.getBuffer(RenderType.lines());

        // ── Linked‑storage highlights ──────────────────────────────
        if (minecraft.level != null
                && !controller.getLinkedStoragePositions().isEmpty()) {
            for (final BlockPos pos : controller.getLinkedStoragePositions()) {
                if (!minecraft.level.hasChunkAt(pos)) {
                    continue;
                }
                final BlockState state = minecraft.level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        pos.getX() - 0.002D, pos.getY() - 0.002D,
                        pos.getZ() - 0.002D,
                        pos.getX() + 1.002D, pos.getY() + 1.002D,
                        pos.getZ() + 1.002D,
                        0.24F, 0.55F, 1.00F, 1.0F);
            }
        }

        // ── Hovered interaction target ─────────────────────────────
        if (!controller.isRotateCaptured()
                && minecraft.level != null
                && minecraft.getCameraEntity() != null) {
            final Vec3 cam = minecraft.gameRenderer.getMainCamera().getPosition();
            final Vec3 viewDir = computeCursorRayDirection(minecraft);
            final Vec3 to = cam.add(viewDir.scale(128.0D));
            final BlockHitResult blockHit = raycastBlockFromCursor(minecraft, cam, to, false);
            final EntityHitResult entityHit = raycastEntityFromCursor(minecraft, cam, to, viewDir, 128.0D);
            final double blockDist = blockHit != null
                    ? cam.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
            final double entityDist = entityHit != null
                    ? cam.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

            if (entityHit != null && entityDist <= blockDist) {
                final Entity entity = entityHit.getEntity();
                final AABB bb = entity.getBoundingBox().inflate(0.03D);
                LevelRenderer.renderLineBox(poseStack, lineBuffer,
                        bb.minX, bb.minY, bb.minZ,
                        bb.maxX, bb.maxY, bb.maxZ,
                        0.35F, 1.0F, 0.55F, 1.0F);
            } else if (blockHit != null && blockHit.getType() == HitResult.Type.BLOCK) {
                final BlockPos pos = blockHit.getBlockPos();
                final BlockState state = minecraft.level.getBlockState(pos);
                if (state.isAir()) {
                    LevelRenderer.renderLineBox(poseStack, lineBuffer,
                            pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D,
                            1.0F, 0.95F, 0.2F, 1.0F);
                } else {
                    final var shape = state.getShape(minecraft.level, pos);
                    for (final AABB box : shape.isEmpty()
                            ? java.util.List.of(new AABB(pos))
                            : shape.toAabbs()) {
                        LevelRenderer.renderLineBox(poseStack, lineBuffer,
                                pos.getX() + box.minX, pos.getY() + box.minY,
                                pos.getZ() + box.minZ,
                                pos.getX() + box.maxX, pos.getY() + box.maxY,
                                pos.getZ() + box.maxZ,
                                1.0F, 0.95F, 0.2F, 1.0F);
                    }
                }
            }
        }

        // ── Shape ghost preview ────────────────────────────────────
        if (minecraft.screen instanceof final BuilderScreen builderScreen) {
            final BuilderScreen.ShapeGhostPreview preview =
                    builderScreen.getShapeGhostPreview();
            if (!preview.blocks().isEmpty()) {
                final float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
                final float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
                final float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
                final float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
                final float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
                final float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
                final float fillA = preview.readyConfirm() ? 0.22F : 0.16F;

                final VertexConsumer fillBuf =
                        bufferSource.getBuffer(RenderType.debugFilledBox());
                for (final BlockPos pos : preview.blocks()) {
                    LevelRenderer.addChainedFilledBoxVertices(
                            poseStack, fillBuf,
                            pos.getX() + 0.03D, pos.getY() + 0.03D,
                            pos.getZ() + 0.03D,
                            pos.getX() + 0.97D, pos.getY() + 0.97D,
                            pos.getZ() + 0.97D,
                            fillR, fillG, fillB, fillA);
                }
                bufferSource.endBatch(RenderType.debugFilledBox());

                for (final BlockPos pos : preview.blocks()) {
                    LevelRenderer.renderLineBox(poseStack, lineBuffer,
                            pos.getX() + 0.03D, pos.getY() + 0.03D,
                            pos.getZ() + 0.03D,
                            pos.getX() + 0.97D, pos.getY() + 0.97D,
                            pos.getZ() + 0.97D,
                            lineR, lineG, lineB, 0.95F);
                }
            }
        }

        bufferSource.endBatch(RenderType.lines());
        poseStack.popPose();
    }

    // ── Raycasting helpers ──────────────────────────────────────────

    private static BlockHitResult raycastBlockFromCursor(
            final Minecraft minecraft, final Vec3 camPos, final Vec3 to,
            final boolean includeFluidSource) {
        final ClipContext.Fluid fluidMode = includeFluidSource
                ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        final HitResult hit = minecraft.level.clip(
                new ClipContext(camPos, to, ClipContext.Block.OUTLINE, fluidMode,
                        minecraft.getCameraEntity()));
        if (hit instanceof final BlockHitResult bhr
                && hit.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        return null;
    }

    private static EntityHitResult raycastEntityFromCursor(
            final Minecraft minecraft, final Vec3 camPos, final Vec3 to,
            final Vec3 viewDir, final double reach) {
        final Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }
        final AABB search = cameraEntity.getBoundingBox()
                .expandTowards(viewDir.scale(reach)).inflate(1.0D);
        return ProjectileUtil.getEntityHitResult(
                cameraEntity, camPos, to, search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != minecraft.player,
                reach * reach);
    }

    private static Vec3 computeCursorRayDirection(final Minecraft minecraft) {
        final double mouseX = minecraft.mouseHandler.xpos();
        final double mouseY = minecraft.mouseHandler.ypos();
        final double width  = Math.max(1.0D, minecraft.getWindow().getScreenWidth());
        final double height = Math.max(1.0D, minecraft.getWindow().getScreenHeight());

        final double nx = (mouseX / width)  * 2.0D - 1.0D;
        final double ny = 1.0D - (mouseY / height) * 2.0D;

        final float  yawDeg   = minecraft.gameRenderer.getMainCamera().getYRot();
        final float  pitchDeg = minecraft.gameRenderer.getMainCamera().getXRot();
        final double yaw      = Math.toRadians(yawDeg);
        final double pitch    = Math.toRadians(pitchDeg);

        final Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                 Math.cos(yaw) * Math.cos(pitch)).normalize();
        final Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        final Vec3 up    = forward.cross(right).normalize();

        final double fovY = Math.toRadians(minecraft.options.fov().get());
        final double tanY = Math.tan(fovY * 0.5D);
        final double tanX = tanY * (width / height);

        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }
}
