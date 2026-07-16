package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RaycastHelper;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Renders the RTS interaction target highlight for Forge 1.20.1.
 *
 * <p>This class mirrors main's player-facing behaviour: it raycasts from the
 * cursor, chooses the nearest block/entity, draws thick corner brackets, adds a
 * translucent fog layer on the hit block face, and suppresses world highlights
 * when the RTS UI owns the cursor. It intentionally keeps target selection and
 * bounding-box decisions here while leaving raw quad geometry to
 * {@link CornerBracketRenderer}.
 */
public final class InteractionTargetRenderer {
    private static final double INFLATE = 0.03D;
    private static final double LINE_OFFSET = 0.01D;
    private static final double NEAR_SKELETON_DISTANCE = 10.0D;
    private static final double FAR_COVER_DISTANCE = 20.0D;
    private static final double FACE_FOG_OFFSET = 0.005D;
    private static final float FACE_FOG_ALPHA_NEAR = 0.045F;
    private static final float FACE_FOG_ALPHA_FAR = 0.5F;
    private static final float NO_DEPTH_BRACKET_ALPHA = 0.32F;
    private static final float NO_DEPTH_FACE_FOG_ALPHA_NEAR = 0.025F;
    private static final float NO_DEPTH_FACE_FOG_ALPHA_FAR = 0.18F;
    private static final float BREATH_SPEED = 0.2F;
    private static final float BREATH_MIN_FACTOR = 0.7F;
    private static final float ENTITY_COLOR_R = 0.50F;
    private static final float ENTITY_COLOR_G = 0.80F;
    private static final float ENTITY_COLOR_B = 1.00F;
    private static final float BLOCK_COLOR_R = 0.965F;
    private static final float BLOCK_COLOR_G = 0.608F;
    private static final float BLOCK_COLOR_B = 0.192F;
    private static final float NEAR_BLOCK_COLOR_R = 1.000F;
    private static final float NEAR_BLOCK_COLOR_G = 0.900F;
    private static final float NEAR_BLOCK_COLOR_B = 0.130F;
    private static final double MAX_REACH = 128.0D;

    private InteractionTargetRenderer() {
    }

    public static void renderHoveredInteractionTarget(Minecraft minecraft, ClientRtsController controller,
            PoseStack poseStack, VertexConsumer bracketBuffer, VertexConsumer noDepthBuffer) {
        if (controller.isRotateCaptured() || minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }
        if (isInteractionBlockedByUI(minecraft)) {
            return;
        }

        float breathFactor = RenderingUtil.getBreathFactor(BREATH_SPEED, BREATH_MIN_FACTOR);
        Vec3 camPos = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 viewDir = resolveHighlightRayDirection(minecraft);
        Vec3 rayEnd = camPos.add(viewDir.scale(MAX_REACH));

        BlockHitResult blockHit = resolveHighlightBlockHit(minecraft, camPos, viewDir);
        EntityHitResult entityHit = RaycastHelper.raycastEntityFromCursor(
                minecraft, camPos, rayEnd, viewDir, MAX_REACH);

        double blockDistSq = blockHit != null ? camPos.distanceToSqr(blockHit.getLocation()) : Double.MAX_VALUE;
        double entityDistSq = entityHit != null ? camPos.distanceToSqr(entityHit.getLocation()) : Double.MAX_VALUE;

        if (entityHit != null && entityDistSq <= blockDistSq) {
            Entity entity = entityHit.getEntity();
            if (InteractionTargetSelection.shouldRenderEntityInsteadOfBlock(
                    entityDistSq, blockDistSq, isWithinBounds(controller, entity.blockPosition()))) {
                double distance = camPos.distanceTo(entity.getBoundingBox().getCenter());
                renderEntityCornerHighlight(poseStack, bracketBuffer, noDepthBuffer, entity, distance, breathFactor);
                return;
            }
        }

        if (blockHit == null || blockHit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        if (!isWithinBounds(controller, pos)) {
            return;
        }

        double distance = camPos.distanceTo(Vec3.atCenterOf(pos));
        renderBlockCornerHighlight(
                minecraft, poseStack, bracketBuffer, noDepthBuffer,
                pos, blockHit.getDirection(), distance, breathFactor);
    }

    private static boolean isInteractionBlockedByUI(Minecraft minecraft) {
        if (!(minecraft.screen instanceof BuilderScreen builderScreen)) {
            return false;
        }

        // BuilderScreen 在固定 RTS UI Scale 的渲染阶段已经把鼠标换算到自己的坐标系。
        // 这里若再次按 Minecraft GUI Scale 换算，屏幕下半部会被误判为底部面板，目标框便会提前消失。
        double mouseX = builderScreen.getCurrentMouseX();
        double mouseY = builderScreen.getCurrentMouseY();

        if (!builderScreen.isWorldArea(mouseX, mouseY)) {
            return true;
        }
        boolean coveredByFloatingWindow = false;
        for (var rtsWindow : builderScreen.getFloatingWindowLayer().frontToBackWindows()) {
            if (rtsWindow.isVisibleWindow() && rtsWindow.isInsideWindow(mouseX, mouseY)) {
                coveredByFloatingWindow = true;
                break;
            }
        }

        return shouldSuppressForBuilderUi(true, coveredByFloatingWindow,
                builderScreen.getShapeController().getShapeBuildSession() != null);
    }

    private static Vec3 resolveHighlightRayDirection(Minecraft minecraft) {
        if (minecraft.screen instanceof BuilderScreen builderScreen) {
            return builderScreen.computeCursorRayDirection();
        }
        return RaycastHelper.computeCursorRayDirection(minecraft);
    }

    private static BlockHitResult resolveHighlightBlockHit(Minecraft minecraft, Vec3 camPos, Vec3 viewDir) {
        return RaycastHelper.raycastBlockFromCursorThroughCulling(minecraft, camPos, viewDir, MAX_REACH, false);
    }

    static boolean shouldSuppressForBuilderUi(boolean cursorInWorld, boolean coveredByFloatingWindow,
            boolean shapePreviewActive) {
        return !cursorInWorld || coveredByFloatingWindow;
    }

    private static void renderEntityCornerHighlight(PoseStack poseStack, VertexConsumer bracketBuffer,
            VertexConsumer noDepthBuffer, Entity entity, double distance, float breathFactor) {
        AABB bounds = entity.getBoundingBox().inflate(INFLATE);
        float r = ENTITY_COLOR_R * breathFactor;
        float g = ENTITY_COLOR_G * breathFactor;
        float b = ENTITY_COLOR_B * breathFactor;

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, bracketBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, distance);
        renderCornerBracketsNoDepth(
                poseStack, noDepthBuffer,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                r, g, b, distance);
    }

    private static void renderBlockCornerHighlight(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer bracketBuffer, VertexConsumer noDepthBuffer,
            BlockPos pos, Direction hitFace, double distance, float breathFactor) {
        if (minecraft.level == null) {
            return;
        }

        AABB bounds = computeWorldBounds(minecraft.level, pos);
        if (bounds == null) {
            return;
        }

        BlockHighlightVisual visual = blockHighlightVisual(distance, breathFactor);

        CornerBracketRenderer.renderCornerBrackets(
                poseStack, bracketBuffer,
                bounds.minX - LINE_OFFSET, bounds.minY - LINE_OFFSET, bounds.minZ - LINE_OFFSET,
                bounds.maxX + LINE_OFFSET, bounds.maxY + LINE_OFFSET, bounds.maxZ + LINE_OFFSET,
                visual.r(), visual.g(), visual.b(), distance);
        renderCornerBracketsNoDepth(
                poseStack, noDepthBuffer,
                bounds.minX - LINE_OFFSET, bounds.minY - LINE_OFFSET, bounds.minZ - LINE_OFFSET,
                bounds.maxX + LINE_OFFSET, bounds.maxY + LINE_OFFSET, bounds.maxZ + LINE_OFFSET,
                visual.r(), visual.g(), visual.b(), distance);

        renderHitFaceFog(bracketBuffer, poseStack, bounds, hitFace,
                visual.r(), visual.g(), visual.b(), visual.faceAlpha());
        renderHitFaceFog(noDepthBuffer, poseStack, bounds, hitFace,
                visual.r(), visual.g(), visual.b(), visual.noDepthFaceAlpha());
    }

    static BlockHighlightVisual blockHighlightVisual(double distance, float breathFactor) {
        float nearWeight = 1.0F - smoothstep(NEAR_SKELETON_DISTANCE, FAR_COVER_DISTANCE, distance);
        float farWeight = 1.0F - nearWeight;
        float r = (NEAR_BLOCK_COLOR_R * nearWeight + BLOCK_COLOR_R * farWeight) * breathFactor;
        float g = (NEAR_BLOCK_COLOR_G * nearWeight + BLOCK_COLOR_G * farWeight) * breathFactor;
        float b = (NEAR_BLOCK_COLOR_B * nearWeight + BLOCK_COLOR_B * farWeight) * breathFactor;
        float faceAlpha = FACE_FOG_ALPHA_NEAR * nearWeight + FACE_FOG_ALPHA_FAR * farWeight;
        float noDepthFaceAlpha = NO_DEPTH_FACE_FOG_ALPHA_NEAR * nearWeight + NO_DEPTH_FACE_FOG_ALPHA_FAR * farWeight;
        return new BlockHighlightVisual(r, g, b, faceAlpha, noDepthFaceAlpha);
    }

    private static float smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0D, Math.min(1.0D, (value - edge0) / (edge1 - edge0)));
        return (float) (t * t * (3.0D - 2.0D * t));
    }

    record BlockHighlightVisual(float r, float g, float b, float faceAlpha, float noDepthFaceAlpha) {
    }

    private static void renderCornerBracketsNoDepth(PoseStack poseStack, VertexConsumer noDepthBuffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float r, float g, float b, double distance) {
        if (noDepthBuffer == null) {
            return;
        }
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, noDepthBuffer,
                minX, minY, minZ, maxX, maxY, maxZ,
                r, g, b, NO_DEPTH_BRACKET_ALPHA, distance);
    }

    private static void renderHitFaceFog(VertexConsumer consumer, PoseStack poseStack,
            AABB bounds, Direction face, float r, float g, float b, float alpha) {
        if (consumer == null) {
            return;
        }

        double off = FACE_FOG_OFFSET;
        double x1 = bounds.minX;
        double x2 = bounds.maxX;
        double y1 = bounds.minY;
        double y2 = bounds.maxY;
        double z1 = bounds.minZ;
        double z2 = bounds.maxZ;

        switch (face) {
            case DOWN -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1 - off, z1, x2, y1 - off, z1, x2, y1 - off, z2, x1, y1 - off, z2, r, g, b, alpha);
            case UP -> RenderingUtil.quad(consumer, poseStack,
                    x1, y2 + off, z1, x1, y2 + off, z2, x2, y2 + off, z2, x2, y2 + off, z1, r, g, b, alpha);
            case NORTH -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1, z1 - off, x2, y1, z1 - off, x2, y2, z1 - off, x1, y2, z1 - off, r, g, b, alpha);
            case SOUTH -> RenderingUtil.quad(consumer, poseStack,
                    x1, y1, z2 + off, x1, y2, z2 + off, x2, y2, z2 + off, x2, y1, z2 + off, r, g, b, alpha);
            case WEST -> RenderingUtil.quad(consumer, poseStack,
                    x1 - off, y1, z1, x1 - off, y2, z1, x1 - off, y2, z2, x1 - off, y1, z2, r, g, b, alpha);
            case EAST -> RenderingUtil.quad(consumer, poseStack,
                    x2 + off, y1, z1, x2 + off, y1, z2, x2 + off, y2, z2, x2 + off, y2, z1, r, g, b, alpha);
        }
    }

    private static boolean isWithinBounds(ClientRtsController controller, BlockPos pos) {
        if (!controller.hasBounds()) {
            return true;
        }
        return RenderingUtil.isWithinBounds(
                pos, controller.getAnchorX(), controller.getAnchorZ(), controller.getMaxRadius());
    }

    private static AABB computeWorldBounds(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (isMultiBlockStructure(state)) {
            return computeMultiBlockBoundsBfs(level, pos);
        }
        return computeSingleBlockAABB(level, pos);
    }

    private static boolean isMultiBlockStructure(BlockState state) {
        return state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART);
    }

    private static AABB computeMultiBlockBoundsBfs(Level level, BlockPos pos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(pos);
        visited.add(pos);

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            AABB aabb = computeSingleBlockAABB(level, current);
            if (aabb != null) {
                minX = Math.min(minX, aabb.minX);
                minY = Math.min(minY, aabb.minY);
                minZ = Math.min(minZ, aabb.minZ);
                maxX = Math.max(maxX, aabb.maxX);
                maxY = Math.max(maxY, aabb.maxY);
                maxZ = Math.max(maxZ, aabb.maxZ);
            }

            for (Direction direction : getConnectionDirections(currentState)) {
                BlockPos neighbor = current.relative(direction);
                if (!visited.add(neighbor)) {
                    continue;
                }

                BlockState neighborState = level.getBlockState(neighbor);
                if (neighborState.is(currentState.getBlock()) && isMultiBlockStructure(neighborState)) {
                    queue.add(neighbor);
                }
            }
        }

        if (minX == Double.MAX_VALUE) {
            return null;
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static Direction[] getConnectionDirections(BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            DoubleBlockHalf half = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF);
            return new Direction[]{half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN};
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)) {
            BedPart part = state.getValue(BlockStateProperties.BED_PART);
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return new Direction[]{part == BedPart.HEAD ? facing.getOpposite() : facing};
        }
        return new Direction[0];
    }

    private static AABB computeSingleBlockAABB(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
        if (shape.isEmpty()) {
            return null;
        }

        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;

        for (AABB box : shape.toAabbs()) {
            minX = Math.min(minX, pos.getX() + box.minX);
            minY = Math.min(minY, pos.getY() + box.minY);
            minZ = Math.min(minZ, pos.getZ() + box.minZ);
            maxX = Math.max(maxX, pos.getX() + box.maxX);
            maxY = Math.max(maxY, pos.getY() + box.maxY);
            maxZ = Math.max(maxZ, pos.getZ() + box.maxZ);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
