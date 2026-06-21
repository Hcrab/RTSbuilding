package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;

/**
 * Renders the RTS build boundary as vertical barrier walls using the same
 * player-facing shape as main. The wall height follows the highest surface
 * along the boundary and extends down to the world's minimum build height.
 */
public final class BoundaryLineRenderer {
    private static final float TILE_SIZE = 2.0F;
    private static final float WHITE = 1.0F;
    private static final float BARRIER_ALPHA = 0.80F;

    private BoundaryLineRenderer() {
    }

    public static void renderBarrierBoundary(PoseStack poseStack, VertexConsumer barrierBuffer,
            double minX, double minZ, double maxX, double maxZ, double defaultY, Level level) {
        if (level == null || barrierBuffer == null) {
            return;
        }

        int highestBlock = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
        float yMax = highestBlock > Integer.MIN_VALUE ? highestBlock + 5.0F : (float) defaultY + 3.0F;
        float yMin = level.getMinBuildHeight();
        float wallHeight = yMax - yMin;
        float wallWidthX = (float) (maxX - minX);
        float wallWidthZ = (float) (maxZ - minZ);
        float scroll = (float) (System.nanoTime() / 1.0e9D * 0.5D);

        PoseStack.Pose pose = poseStack.last();

        addTexturedQuad(pose, barrierBuffer,
                (float) minX, yMin, (float) minZ,
                (float) maxX, yMax, (float) minZ,
                wallWidthX / TILE_SIZE, wallHeight / TILE_SIZE,
                0.0F, 0.0F, 1.0F, scroll);
        addTexturedQuad(pose, barrierBuffer,
                (float) maxX, yMin, (float) maxZ,
                (float) minX, yMax, (float) maxZ,
                wallWidthX / TILE_SIZE, wallHeight / TILE_SIZE,
                0.0F, 0.0F, -1.0F, scroll);
        addTexturedQuad(pose, barrierBuffer,
                (float) minX, yMin, (float) minZ,
                (float) minX, yMax, (float) maxZ,
                wallWidthZ / TILE_SIZE, wallHeight / TILE_SIZE,
                1.0F, 0.0F, 0.0F, scroll);
        addTexturedQuad(pose, barrierBuffer,
                (float) maxX, yMin, (float) maxZ,
                (float) maxX, yMax, (float) minZ,
                wallWidthZ / TILE_SIZE, wallHeight / TILE_SIZE,
                -1.0F, 0.0F, 0.0F, scroll);
    }

    private static int findHighestBoundaryBlock(Level level, double minX, double minZ, double maxX, double maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX);
        int x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ);
        int z2 = (int) Math.floor(maxZ);

        for (int x = x1; x <= x2; x++) {
            highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z1));
            highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z2));
        }
        for (int z = z1 + 1; z < z2; z++) {
            highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x1, z));
            highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x2, z));
        }

        return highest;
    }

    private static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float yMin, float z1,
            float x2, float yMax, float z2,
            float tileU, float tileV,
            float nx, float ny, float nz,
            float scroll) {
        Matrix4f matrix = pose.pose();
        addVertex(buffer, matrix, x1, yMin, z1, scroll, scroll, nx, ny, nz);
        addVertex(buffer, matrix, x2, yMin, z2, tileU + scroll, scroll, nx, ny, nz);
        addVertex(buffer, matrix, x2, yMax, z2, tileU + scroll, tileV + scroll, nx, ny, nz);
        addVertex(buffer, matrix, x1, yMax, z1, scroll, tileV + scroll, nx, ny, nz);
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f matrix,
            float x, float y, float z, float u, float v,
            float nx, float ny, float nz) {
        buffer.vertex(matrix, x, y, z)
                .color(WHITE, WHITE, WHITE, BARRIER_ALPHA)
                .uv(u, v)
                .overlayCoords(0, 10)
                .uv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF)
                .normal(nx, ny, nz)
                .endVertex();
    }
}
