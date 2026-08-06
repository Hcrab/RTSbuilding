package com.rtsbuilding.rtsbuilding.client.rendering.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.rendering.util.CornerBracketRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.StorageBatchSelectionSession;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

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

        // Fabric 分支尚未引入可复用的任意 AABB 动画器；框体仍直接读取同一选区状态，
        // 因此玩家看到的选区与服务端要扫描的两角点始终一致。
        AABB animated = new AABB(
                box.min().getX() - 0.01D,
                box.min().getY() - 0.01D,
                box.min().getZ() - 0.01D,
                box.max().getX() + 1.01D,
                box.max().getY() + 1.01D,
                box.max().getZ() + 1.01D);
        double minX = animated.minX;
        double minY = animated.minY;
        double minZ = animated.minZ;
        double maxX = animated.maxX;
        double maxY = animated.maxY;
        double maxZ = animated.maxZ;
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double distance = camera.distanceTo(new Vec3(
                (minX + maxX) * 0.5D,
                (minY + maxY) * 0.5D,
                (minZ + maxZ) * 0.5D));
        float alpha = box.complete() ? 0.90F : 0.58F;
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, brackets, minX, minY, minZ, maxX, maxY, maxZ,
                0.30F, 0.60F, 1.0F, alpha, distance);
        CornerBracketRenderer.renderCornerBrackets(
                poseStack, noDepth, minX, minY, minZ, maxX, maxY, maxZ,
                0.30F, 0.60F, 1.0F, 0.10F, distance);

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
        clearEndpointCache();
    }

    private static void clearEndpointCache() {
        cachedMin = null;
        cachedMax = null;
        cachedEndpoints = List.of();
    }
}
