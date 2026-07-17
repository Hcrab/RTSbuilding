package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Chunk guide line renderer.
 * Renders a 3×3 chunk grid centred on the player for visual reference in RTS mode.
 */
public final class ChunkGuideRenderer {
    // Chunk guide range radius (in chunks); 1 renders a 3×3 area around the centre chunk
    private static final int CHUNK_GUIDE_RADIUS_CHUNKS = 1;

    /**
     * Private constructor to prevent instantiation.
     */
    private ChunkGuideRenderer() {
    }

    /**
     * Renders the chunk guide grid.
     *
     * @param minecraft      the Minecraft client instance
     * @param cameraPosition camera position
     * @param poseStack      pose stack for coordinate transforms
     * @param fillBuffer     fill buffer for translucent block rendering
     * @param lineBuffer     line buffer for wireframe rendering
     */
    public static void renderChunkGuides(
            Minecraft minecraft,
            Vec3 cameraPosition,
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer) {
        if (minecraft.level == null) {
            return;
        }

        // Compute the chunk coordinates of the camera position
        BlockPos cameraBlockPos = BlockPos.containing(cameraPosition);
        int centerChunkX = SectionPos.blockToSectionCoord(cameraBlockPos.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(cameraBlockPos.getZ());

        // Compute the rendering range boundaries
        int minChunkX = centerChunkX - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkX = centerChunkX + CHUNK_GUIDE_RADIUS_CHUNKS;
        int minChunkZ = centerChunkZ - CHUNK_GUIDE_RADIUS_CHUNKS;
        int maxChunkZ = centerChunkZ + CHUNK_GUIDE_RADIUS_CHUNKS;

        // Determine the guide line Y-height: prefer player position, fall back to camera position
        int guideYSource = minecraft.player == null ? cameraBlockPos.getY() : minecraft.player.blockPosition().getY();
        int guideY = Mth.clamp(guideYSource, minecraft.level.getMinY(), minecraft.level.getMaxY() - 1);

        // Iterate over all chunks in the range, rendering edge highlights
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                renderChunkEdgeHighlights(minecraft, poseStack, fillBuffer, lineBuffer, cx, cz, guideY);
            }
        }
    }

    /**
     * Renders edge highlights for a single chunk.
     *
     * @param minecraft the Minecraft client instance
     * @param poseStack pose stack
     * @param fillBuffer fill buffer
     * @param lineBuffer line buffer
     * @param chunkX    chunk X coordinate
     * @param chunkZ    chunk Z coordinate
     * @param guideY    guide line Y height
     */
    private static void renderChunkEdgeHighlights(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int chunkX,
            int chunkZ,
            int guideY) {
        // Convert chunk coordinates to world coordinates (each chunk is 16×16)
        int startX = chunkX << 4;  // equivalent to chunkX * 16
        int startZ = chunkZ << 4;
        int endX = startX + 15;
        int endZ = startZ + 15;

        // Optimisation: check chunk loading state at chunk level to avoid per-cell rechecks
        if (!minecraft.level.hasChunkAt(new BlockPos(startX, guideY, startZ))) {
            return;
        }

        // Choose colour based on chunk coordinate parity (checkerboard pattern)
        ChunkGuideColor color = chunkGuideColor(chunkX, chunkZ);

        // Render all block cells on the four edges of the chunk
        // Top and bottom edges (full rows)
        for (int x = startX; x <= endX; x++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, startZ, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, x, endZ, guideY, color);
        }
        // Left and right edges (excluding corner cells to avoid double rendering)
        for (int z = startZ + 1; z < endZ; z++) {
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, startX, z, guideY, color);
            renderChunkGuideCell(poseStack, fillBuffer, lineBuffer, endX, z, guideY, color);
        }
    }

    /**
     * Renders guide highlight for a single cell (fill + wireframe).
     *
     * @param poseStack  pose stack
     * @param fillBuffer fill buffer
     * @param lineBuffer line buffer
     * @param x          world X coordinate
     * @param z          world Z coordinate
     * @param guideY     Y height
     * @param color      colour configuration
     */
    private static void renderChunkGuideCell(
            PoseStack poseStack,
            VertexConsumer fillBuffer,
            VertexConsumer lineBuffer,
            int x,
            int z,
            int guideY,
            ChunkGuideColor color) {
        // Inset by 0.04 units to create a gap between adjacent cells
        double inset = 0.04D;
        double minX = x + inset;
        double minY = guideY + inset;
        double minZ = z + inset;
        double maxX = x + 1.0D - inset;
        double maxY = guideY + 1.0D - inset;
        double maxZ = z + 1.0D - inset;

        // Draw translucent fill
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.addChainedFilledBoxVertices(
                poseStack,
                fillBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                color.r(), color.g(), color.b(), color.a());

        // Draw wireframe (slightly brighter than the fill colour)
        com.rtsbuilding.rtsbuilding.client.rendering.util.RtsLegacyShapeRenderer.renderLineBox(
                poseStack,
                lineBuffer,
                minX, minY, minZ,
                maxX, maxY, maxZ,
                Math.min(1.0F, color.r() + 0.18F),
                Math.min(1.0F, color.g() + 0.18F),
                Math.min(1.0F, color.b() + 0.18F),
                0.92F);
    }

    /**
     * Generates a checkerboard colour based on chunk coordinates.
     * Even chunks use cyan-blue, odd chunks use golden-yellow.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return colour configuration
     */
    private static ChunkGuideColor chunkGuideColor(int chunkX, int chunkZ) {
        return ((chunkX ^ chunkZ) & 1) == 0
                ? new ChunkGuideColor(0.16F, 0.78F, 1.0F, 0.24F)   // Cyan-blue
                : new ChunkGuideColor(1.0F, 0.88F, 0.16F, 0.22F);  // Golden-yellow
    }

    /**
     * Colour record holding RGBA values.
     */
    private record ChunkGuideColor(float r, float g, float b, float a) {
    }
}
