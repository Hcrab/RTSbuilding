package com.rtsbuilding.rtsbuilding.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
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

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RtsBoundaryRenderer extends RenderStateShard {
    private static final int CHUNK_GUIDE_RADIUS_CHUNKS = 1;

    private static final RenderType CHUNK_XRAY_FILL = RenderType.create(
            "rtsbuilding_chunk_xray_fill",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLE_STRIP,
            2 * 1024 * 1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private static final RenderType CHUNK_XRAY_LINES = RenderType.create(
            "rtsbuilding_chunk_xray_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            2 * 1024 * 1024,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(DEFAULT_LINE)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .setOutputState(MAIN_TARGET)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    private RtsBoundaryRenderer() {
        super("rtsbuilding_boundary", () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void onRenderLevel(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        Vec3 camPos = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        if (controller.isChunkCurtainVisible()) {
            renderChunkGuides(
                    minecraft,
                    camPos,
                    poseStack,
                    bufferSource.getBuffer(CHUNK_XRAY_FILL),
                    bufferSource.getBuffer(CHUNK_XRAY_LINES));
            bufferSource.endBatch(CHUNK_XRAY_FILL);
            bufferSource.endBatch(CHUNK_XRAY_LINES);
        }

        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());

        double ax = controller.getAnchorX();
        double ay = controller.getAnchorY();
        double az = controller.getAnchorZ();
        double r = controller.getMaxRadius();

        double minX = ax - r;
        double maxX = ax + r;
        double minZ = az - r;
        double maxZ = az + r;

        LevelRenderer.renderLineBox(poseStack, lineBuffer, minX, ay - 0.25D, minZ, maxX, ay + 0.25D, maxZ,
                1.0F, 0.25F, 0.25F, 1.0F);

        renderLinkedStorages(minecraft, controller, poseStack, lineBuffer);
        renderHoveredInteractionTarget(minecraft, controller, poseStack, lineBuffer);
        renderShapeGhostPreview(minecraft, poseStack, bufferSource);

        bufferSource.endBatch(RenderType.lines());
        bufferSource.endBatch(RenderType.debugFilledBox());
        poseStack.popPose();
    }

    private static void renderChunkGuides(
            final Minecraft minecraft,
            final Vec3 cameraPosition,
            final PoseStack poseStack,
            final VertexConsumer fillBuffer,
            final VertexConsumer lineBuffer) {
        if (minecraft.level == null) {
            return;
        }
        BlockPos cameraBlockPos = BlockPos.containing(cameraPosition);
        int centerChunkX = SectionPos.blockToSectionCoord(cameraBlockPos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(cameraBlockPos.getZ());
        int minChunkX = centerChunkX - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkX = centerChunkX + CHUNK_GUIDE_RADIUS_CHUNKS;
        int minChunkZ = centerChunkZ - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkZ = centerChunkZ + CHUNK_GUIDE_RADIUS_CHUNKS;
        int guideYSource = minecraft.player == null ? cameraBlockPos.getY() : minecraft.player.blockPosition().getY();
        int guideY = Mth.clamp(guideYSource, minecraft.level.getMinBuildHeight(), minecraft.level.getMaxBuildHeight() - 1);

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                renderChunkEdgeHighlights(minecraft, poseStack, fillBuffer, lineBuffer, cx, cz, guideY);
            }
        }
    }

    private static void renderChunkEdgeHighlights(
            final Minecraft minecraft,
            final PoseStack poseStack,
            final VertexConsumer fillBuffer,
            final VertexConsumer lineBuffer,
            final int chunkX,
            final int chunkZ,
            final int guideY) {
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;
        if (!minecraft.level.hasChunkAt(new BlockPos(startX, guideY, startZ))) {
            return;
        }

        ChunkGuideColor color = chunkGuideColor(chunkX, chunkZ);
        for (int x = startX; x <= endX; x++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, startZ, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, endZ, guideY, color);
        }
        for (int z = startZ + 1; z < endZ; z++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, startX, z, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, endX, z, guideY, color);
        }
    }

    private static void renderChunkGuideCell(
            final PoseStack poseStack,
            final VertexConsumer fillBuffer,
            final VertexConsumer lineBuffer,
            final int x,
            final int z,
            final int guideY,
            final ChunkGuideColor color) {
        double inset = 0.04D;
        double minX = x + inset;
        double minY = guideY + inset;
        double minZ = z + inset;
        double maxX = x + 1.0D - inset;
        double maxY = guideY + 1.0D - inset;
        double maxZ = z + 1.0D - inset;
        LevelRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                color.r(),
                color.g(),
                color.b(),
                color.a());
        LevelRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                Math.min(1.0F, color.r() + 0.18F),
                Math.min(1.0F, color.g() + 0.18F),
                Math.min(1.0F, color.b() + 0.18F),
                0.92F);
    }

    private static ChunkGuideColor chunkGuideColor(final int chunkX, final int chunkZ) {
        return ((chunkX ^ chunkZ) & 1) == 0
                ? new ChunkGuideColor(0.16F, 0.78F, 1.0F, 0.24F)
                : new ChunkGuideColor(1.0F, 0.88F, 0.16F, 0.22F);
    }

    private record ChunkGuideColor(float r, float g, float b, float a) {
    }

    private static void renderLinkedStorages(final Minecraft minecraft, final ClientRtsController controller, final PoseStack poseStack,
            final VertexConsumer lineBuffer) {
        if (minecraft.level == null || controller.getLinkedStoragePositions().isEmpty()) {
            return;
        }

        for (BlockPos pos : controller.getLinkedStoragePositions()) {
            if (!minecraft.level.hasChunkAt(pos)) {
                continue;
            }
            BlockState state = minecraft.level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }

            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() - 0.002D,
                    pos.getY() - 0.002D,
                    pos.getZ() - 0.002D,
                    pos.getX() + 1.002D,
                    pos.getY() + 1.002D,
                    pos.getZ() + 1.002D,
                    0.24F, 0.55F, 1.00F, 1.0F);
        }
    }

    private static void renderHoveredInteractionTarget(final Minecraft minecraft, final ClientRtsController controller,
            final PoseStack poseStack, final VertexConsumer lineBuffer) {
        if (controller.isRotateCaptured() || minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }

        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = computeCursorRayDirection(minecraft);
        Vec3 to = camPos.add(viewDir.scale(128.0D));
        BlockHitResult blockHit = raycastBlockFromCursor(minecraft, camPos, to, false);
        EntityHitResult entityHit = raycastEntityFromCursor(minecraft, camPos, to, viewDir, 128.0D);
        double blockDist = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDist = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (entityHit != null && entityDist <= blockDist) {
            Entity entity = entityHit.getEntity();
            AABB bb = entity.getBoundingBox().inflate(0.03D);
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    bb.minX,
                    bb.minY,
                    bb.minZ,
                    bb.maxX,
                    bb.maxY,
                    bb.maxZ,
                    0.35F,
                    1.0F,
                    0.55F,
                    1.0F);
            return;
        }
        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        if (state.isAir()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);
            return;
        }

        var shape = state.getShape(minecraft.level, pos);
        if (shape.isEmpty()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D,
                    1.0F, 0.95F, 0.2F, 1.0F);
            return;
        }

        for (AABB box : shape.toAabbs()) {
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    pos.getX() + box.minX,
                    pos.getY() + box.minY,
                    pos.getZ() + box.minZ,
                    pos.getX() + box.maxX,
                    pos.getY() + box.maxY,
                    pos.getZ() + box.maxZ,
                    1.0F, 0.95F, 0.2F, 1.0F);
        }
    }

    private static void renderShapeGhostPreview(final Minecraft minecraft, final PoseStack poseStack,
            final MultiBufferSource.BufferSource bufferSource) {
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return;
        }
        BuilderScreen.ShapeGhostPreview preview = builderScreen.getShapeGhostPreview();
        if (preview.blocks().isEmpty()) {
            return;
        }

        float lineR = preview.readyConfirm() ? 0.45F : 0.30F;
        float lineG = preview.readyConfirm() ? 0.95F : 0.75F;
        float lineB = preview.readyConfirm() ? 0.45F : 1.00F;
        float fillR = preview.readyConfirm() ? 0.24F : 0.16F;
        float fillG = preview.readyConfirm() ? 0.72F : 0.55F;
        float fillB = preview.readyConfirm() ? 0.24F : 0.90F;
        float fillA = preview.readyConfirm() ? 0.22F : 0.16F;
        VertexConsumer fillBuffer = bufferSource.getBuffer(RenderType.debugFilledBox());

        for (BlockPos pos : preview.blocks()) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.addChainedFilledBoxVertices(
                    poseStack,
                    fillBuffer,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    fillR,
                    fillG,
                    fillB,
                    fillA);
        }

        VertexConsumer lineBuffer = bufferSource.getBuffer(RenderType.lines());
        for (BlockPos pos : preview.blocks()) {
            double minX = pos.getX() + 0.03D;
            double minY = pos.getY() + 0.03D;
            double minZ = pos.getZ() + 0.03D;
            double maxX = pos.getX() + 0.97D;
            double maxY = pos.getY() + 0.97D;
            double maxZ = pos.getZ() + 0.97D;
            LevelRenderer.renderLineBox(
                    poseStack,
                    lineBuffer,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    lineR,
                    lineG,
                    lineB,
                    0.95F);
        }
    }

    private static BlockHitResult raycastBlockFromCursor(final Minecraft minecraft, final Vec3 camPos, final Vec3 to,
            final boolean includeFluidSource) {
        ClipContext.Fluid fluidMode = includeFluidSource ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        HitResult hit = minecraft.level.clip(new ClipContext(camPos, to, ClipContext.Block.OUTLINE, fluidMode,
                minecraft.getCameraEntity()));
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        return null;
    }

    private static EntityHitResult raycastEntityFromCursor(final Minecraft minecraft, final Vec3 camPos, final Vec3 to, final Vec3 viewDir,
            final double reach) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }
        AABB search = cameraEntity.getBoundingBox().expandTowards(viewDir.scale(reach)).inflate(1.0D);
        return ProjectileUtil.getEntityHitResult(
                cameraEntity,
                camPos,
                to,
                search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != minecraft.player,
                reach * reach);
    }

    private static Vec3 computeCursorRayDirection(final Minecraft minecraft) {
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        double width = Math.max(1.0D, minecraft.getWindow().getScreenWidth());
        double height = Math.max(1.0D, minecraft.getWindow().getScreenHeight());

        double nx = (mouseX / width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / height) * 2.0D;

        float yawDeg = minecraft.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = minecraft.gameRenderer.getMainCamera().getXRot();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();

        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        Vec3 up = forward.cross(right).normalize();

        double fovY = Math.toRadians(minecraft.options.fov().get());
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / height);

        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }
}
