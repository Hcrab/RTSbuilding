package com.rtsbuilding.rtsbuilding.client.rendering.overlay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Renders the RTS build boundary as vertical barrier walls using a custom
 * stripped texture, producing the same red diagonal stripe effect as the
 * vanilla world border barrier. The walls extend from 5 blocks above the
 * highest surface block on the boundary line down to bedrock.
 */
public final class BoundaryLineRenderer {

    /** Texture tile size in blocks — controls repeat frequency of the stripe pattern */
    private static final float TILE_SIZE = 2.0F;
    /** Pure white vertex color multiplier — the texture provides the actual color */
    private static final float WHITE = 1.0F;
    private static final float BARRIER_A = 0.80F;
    /** Full brightness lightmap values (block=15, sky=15, each shifted left by 4) */
    private static final int FULL_BRIGHT = 0xF0;

    private BoundaryLineRenderer() {
    }

    /**
     * Renders 4 vertical barrier walls at the boundary edges.
     * The walls extend from 5 blocks above the highest surface block
     * along the boundary perimeter down to bedrock.
     *
     * @param poseStack     current pose stack
     * @param barrierBuffer vertex consumer for the barrier render type
     * @param minX          boundary min X
     * @param minZ          boundary min Z
     * @param maxX          boundary max X
     * @param maxZ          boundary max Z
     * @param defaultY      fallback Y if no blocks found on the boundary
     * @param level         world level, used to query surface heights
     */
    public static void renderBarrierBoundary(PoseStack poseStack, VertexConsumer barrierBuffer,
            double minX, double minZ, double maxX, double maxZ, double defaultY, Level level) {
        int highestBlock = findHighestBoundaryBlock(level, minX, minZ, maxX, maxZ);
        float yMax = (highestBlock > Integer.MIN_VALUE)
                ? highestBlock + 5.0F
                : (float) defaultY + 3.0F;
        float yMin = (float) level.getMinY();
        float wallHeight = yMax - yMin;

        var pose = poseStack.last();

        float wallWidthX = (float)(maxX - minX);
        float wallWidthZ = (float)(maxZ - minZ);

        /** Animation scroll offset — smooth diagonal stripe movement matching vanilla world border */
        float scroll = (float)(System.nanoTime() / 1.0e9 * 0.5F);

        // North wall (z = minZ) — faces positive Z
        addTexturedQuad(pose, barrierBuffer,
                (float) minX, yMin, (float) minZ,
                (float) maxX, yMax, (float) minZ,
                wallWidthX / TILE_SIZE, wallHeight / TILE_SIZE,
                0.0F, 0.0F, 1.0F, scroll);

        // South wall (z = maxZ) — faces negative Z
        addTexturedQuad(pose, barrierBuffer,
                (float) maxX, yMin, (float) maxZ,
                (float) minX, yMax, (float) maxZ,
                wallWidthX / TILE_SIZE, wallHeight / TILE_SIZE,
                0.0F, 0.0F, -1.0F, scroll);

        // West wall (x = minX) — faces positive X
        addTexturedQuad(pose, barrierBuffer,
                (float) minX, yMin, (float) minZ,
                (float) minX, yMax, (float) maxZ,
                wallWidthZ / TILE_SIZE, wallHeight / TILE_SIZE,
                1.0F, 0.0F, 0.0F, scroll);

        // East wall (x = maxX) — faces negative X
        addTexturedQuad(pose, barrierBuffer,
                (float) maxX, yMin, (float) maxZ,
                (float) maxX, yMax, (float) minZ,
                wallWidthZ / TILE_SIZE, wallHeight / TILE_SIZE,
                -1.0F, 0.0F, 0.0F, scroll);
    }

    /**
     * Scans the 4 boundary edges to find the highest surface block.
     * Uses the world heightmap for efficient per-block lookups.
     *
     * @return the highest surface Y found, or Integer.MIN_VALUE if unloaded area
     */
    private static int findHighestBoundaryBlock(Level level, double minX, double minZ, double maxX, double maxZ) {
        int highest = Integer.MIN_VALUE;
        int x1 = (int) Math.floor(minX);
        int x2 = (int) Math.floor(maxX);
        int z1 = (int) Math.floor(minZ);
        int z2 = (int) Math.floor(maxZ);

        // North edge (z = z1)
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z1);
            if (h > highest) highest = h;
        }
        // South edge (z = z2)
        for (int x = x1; x <= x2; x++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z2);
            if (h > highest) highest = h;
        }
        // West edge (x = x1, skip corners already scanned)
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x1, z);
            if (h > highest) highest = h;
        }
        // East edge (x = x2, skip corners already scanned)
        for (int z = z1 + 1; z < z2; z++) {
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x2, z);
            if (h > highest) highest = h;
        }

        return highest;
    }

    /**
     * Adds a single textured quad to the barrier buffer.
     * <p>
     * The quad spans from {@code (x1, yMin, z1)} to {@code (x2, yMax, z2)}
     * using the entity translucent (NEW_ENTITY) vertex format with the
     * barrier texture tiled by {@code tileU × tileV} repetitions.
     * A time-based {@code scroll} offset is added to both U and V to create
     * a continuous diagonal stripe animation matching the vanilla world border.
     *
     * @param nx, ny, nz  face normal direction
     * @param scroll      animation scroll offset for diagonal stripe movement
     */
    private static void addTexturedQuad(PoseStack.Pose pose, VertexConsumer buffer,
            float x1, float yMin, float z1,
            float x2, float yMax, float z2,
            float tileU, float tileV,
            float nx, float ny, float nz,
            float scroll) {
        // bottom-left
        buffer.addVertex(pose, x1, yMin, z1).setUv(scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(WHITE, WHITE, WHITE, BARRIER_A)
                .setNormal(nx, ny, nz);
        // bottom-right
        buffer.addVertex(pose, x2, yMin, z2).setUv(tileU + scroll, scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(WHITE, WHITE, WHITE, BARRIER_A)
                .setNormal(nx, ny, nz);
        // top-right
        buffer.addVertex(pose, x2, yMax, z2).setUv(tileU + scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(WHITE, WHITE, WHITE, BARRIER_A)
                .setNormal(nx, ny, nz);
        // top-left
        buffer.addVertex(pose, x1, yMax, z1).setUv(scroll, tileV + scroll)
                .setUv1(0, 10)
                .setUv2(FULL_BRIGHT, FULL_BRIGHT)
                .setColor(WHITE, WHITE, WHITE, BARRIER_A)
                .setNormal(nx, ny, nz);
    }
}
