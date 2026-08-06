package com.rtsbuilding.rtsbuilding.client.rendering.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.StorageBatchSelectionSession;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 复刻 v2 批量框选的白色流动边框、蓝色体积和储存端点括号。 */
public final class StorageBatchSelectionRenderer {
    private static BlockPos cachedMin;
    private static BlockPos cachedMax;
    private static List<BlockPos> cachedEndpoints = List.of();

    private StorageBatchSelectionRenderer() {
    }

    public static void render(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer brackets, VertexConsumer noDepth,
            float partialTick) {
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

        double minX = box.min().getX() - 0.01D;
        double minY = box.min().getY() - 0.01D;
        double minZ = box.min().getZ() - 0.01D;
        double maxX = box.max().getX() + 1.01D;
        double maxY = box.max().getY() + 1.01D;
        double maxZ = box.max().getZ() + 1.01D;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceTo(new Vec3(
                (minX + maxX) * 0.5D,
                (minY + maxY) * 0.5D,
                (minZ + maxZ) * 0.5D));
        double flow = minecraft.level == null ? 0.0D
                : ((minecraft.level.getGameTime() + partialTick) * 0.02D) % 0.50D;

        CornerBracketRenderer.renderDashedCornerBrackets(
                poseStack, brackets, minX, minY, minZ, maxX, maxY, maxZ,
                1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.90F, distance, flow);
        CornerBracketRenderer.renderDashedCornerBrackets(
                poseStack, noDepth, minX, minY, minZ, maxX, maxY, maxZ,
                1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.10F, distance, flow);

        if (!box.complete()) {
            CornerBracketRenderer.renderFilledFaces(
                    brackets, poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                    0.30F, 0.50F, 1.0F, 0.18F);
            CornerBracketRenderer.renderFilledFaces(
                    noDepth, poseStack, minX, minY, minZ, maxX, maxY, maxZ,
                    0.30F, 0.50F, 1.0F, 0.06F);
            clearCache();
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
                    if (!minecraft.level.hasChunk(chunkX, chunkZ)) continue;
                    for (BlockPos pos : minecraft.level.getChunk(chunkX, chunkZ)
                            .getBlockEntities().keySet()) {
                        if (contains(box, pos)) result.add(pos.immutable());
                    }
                }
            }
        }
        cachedEndpoints = List.copyOf(result);
        return cachedEndpoints;
    }

    private static boolean contains(StorageBatchSelectionSession.SelectionBox box, BlockPos pos) {
        return pos.getX() >= box.min().getX() && pos.getX() <= box.max().getX()
                && pos.getY() >= box.min().getY() && pos.getY() <= box.max().getY()
                && pos.getZ() >= box.min().getZ() && pos.getZ() <= box.max().getZ();
    }

    private static void clearCache() {
        cachedMin = null;
        cachedMax = null;
        cachedEndpoints = List.of();
    }
}
