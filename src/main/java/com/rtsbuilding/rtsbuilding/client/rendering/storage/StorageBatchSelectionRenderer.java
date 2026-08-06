package com.rtsbuilding.rtsbuilding.client.rendering.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.StorageBatchSelectionSession;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量储存框选预览。
 *
 * <p>范围变化复用现有盒子补间器；渲染只表达客户端选择，不把本地发现的端点当成服务端结果。</p>
 */
public final class StorageBatchSelectionRenderer {
    private static final RtsSelectionBoxAnimator BOX_ANIMATOR =
            new RtsSelectionBoxAnimator();
    private static BlockPos cachedMin;
    private static BlockPos cachedMax;
    private static List<BlockPos> cachedEndpoints = List.of();

    private StorageBatchSelectionRenderer() {
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer brackets, VertexConsumer noDepth, float partialTick) {
        if (!(minecraft.screen instanceof BuilderScreen screen)) {
            clearCache();
            return;
        }
        StorageBatchSelectionSession.SelectionBox box =
                screen.getStorageBatchSelection().selectionBox();
        if (box == null) {
            clearCache();
            return;
        }

        AABB animated = BOX_ANIMATOR.renderAabb(
                new RtsCullingBox(0, box.min(), box.max())).inflate(0.01D);
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceTo(animated.getCenter());
        float red = box.complete() ? 1.0F : 0.30F;
        float green = box.complete() ? 1.0F : 0.55F;
        float blue = 1.0F;
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, brackets,
                animated.minX, animated.minY, animated.minZ,
                animated.maxX, animated.maxY, animated.maxZ,
                red, green, blue, 0.90F, distance);
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, noDepth,
                animated.minX, animated.minY, animated.minZ,
                animated.maxX, animated.maxY, animated.maxZ,
                red, green, blue, 0.10F, distance);

        if (!box.complete()) {
            clearEndpointCache();
            return;
        }
        for (BlockPos endpoint : endpoints(minecraft, box)) {
            double endpointDistance = camera.distanceTo(Vec3.atCenterOf(endpoint));
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, brackets,
                    endpoint.getX() - 0.01D, endpoint.getY() - 0.01D, endpoint.getZ() - 0.01D,
                    endpoint.getX() + 1.01D, endpoint.getY() + 1.01D, endpoint.getZ() + 1.01D,
                    0.24F, 0.55F, 1.0F, 0.75F, endpointDistance);
            CornerBracketRenderer.renderCornerBrackets(
                    poseStack, noDepth,
                    endpoint.getX() - 0.01D, endpoint.getY() - 0.01D, endpoint.getZ() - 0.01D,
                    endpoint.getX() + 1.01D, endpoint.getY() + 1.01D, endpoint.getZ() + 1.01D,
                    0.24F, 0.55F, 1.0F, 0.10F, endpointDistance);
        }
    }

    private static List<BlockPos> endpoints(
            Minecraft minecraft, StorageBatchSelectionSession.SelectionBox box) {
        if (box.min().equals(cachedMin) && box.max().equals(cachedMax)) {
            return cachedEndpoints;
        }
        cachedMin = box.min().immutable();
        cachedMax = box.max().immutable();
        List<BlockPos> result = new ArrayList<>();
        if (minecraft.level != null) {
            int minChunkX = box.min().getX() >> 4;
            int maxChunkX = box.max().getX() >> 4;
            int minChunkZ = box.min().getZ() >> 4;
            int maxChunkZ = box.max().getZ() >> 4;
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    if (!minecraft.level.hasChunk(chunkX, chunkZ)) {
                        continue;
                    }
                    for (BlockPos pos : minecraft.level.getChunk(chunkX, chunkZ)
                            .getBlockEntities().keySet()) {
                        if (contains(box, pos)) {
                            result.add(pos.immutable());
                        }
                    }
                }
            }
        }
        cachedEndpoints = List.copyOf(result);
        return cachedEndpoints;
    }

    private static boolean contains(
            StorageBatchSelectionSession.SelectionBox box, BlockPos pos) {
        return pos.getX() >= box.min().getX() && pos.getX() <= box.max().getX()
                && pos.getY() >= box.min().getY() && pos.getY() <= box.max().getY()
                && pos.getZ() >= box.min().getZ() && pos.getZ() <= box.max().getZ();
    }

    private static void clearCache() {
        clearEndpointCache();
        BOX_ANIMATOR.clear();
    }

    private static void clearEndpointCache() {
        cachedMin = null;
        cachedMax = null;
        cachedEndpoints = List.of();
    }
}
