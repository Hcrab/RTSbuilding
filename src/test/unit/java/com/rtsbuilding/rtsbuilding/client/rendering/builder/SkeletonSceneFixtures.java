package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import net.minecraft.core.BlockPos;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Builds and loads merged-skeleton stress scenes.
 *
 * <p>Small deterministic synthetic scenes cover exact geometric edge cases.
 * The resource-backed world-spawn scene represents a pre-generated selection
 * exported once from a world slice, so tests do not pay world-generation cost
 * or drift with generator changes.</p>
 */
final class SkeletonSceneFixtures {

    private static final String WORLD_SPAWN_SCENE =
            "/rtsbuilding/skeleton-scenes/world_spawn_area_10k.rtscene";

    private SkeletonSceneFixtures() {
    }

    static SkeletonScene solidCube(int size) {
        List<BlockPos> blocks = new ArrayList<>(size * size * size);
        Map<Long, String> ids = new HashMap<>();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    add(blocks, ids, new BlockPos(x, y, z), "minecraft:stone");
                }
            }
        }
        return new SkeletonScene("solid_" + size + "_cube", "deterministic cuboid", blocks, ids);
    }

    static SkeletonScene chunkSeamTenK() {
        List<BlockPos> blocks = new ArrayList<>();
        Map<Long, String> ids = new HashMap<>();
        for (int y = 58; y <= 67; y++) {
            for (int x = -20; x <= 27; x++) {
                for (int z = -20; z <= 27; z++) {
                    boolean seamCut = (x == -1 || x == 16 || z == -1 || z == 16) && y % 3 == 0;
                    boolean support = y <= 61;
                    boolean roughStone = ((x * 31 + z * 17 + y * 13) & 7) != 0;
                    if (support || (roughStone && !seamCut)) {
                        add(blocks, ids, new BlockPos(x, y, z), y > 63 ? "minecraft:dirt" : "minecraft:stone");
                    }
                }
            }
        }
        return new SkeletonScene("chunk_seam_10k", "deterministic chunk-boundary volume", blocks, ids);
    }

    static SkeletonScene treeCanopyTenK() {
        List<BlockPos> blocks = new ArrayList<>();
        Map<Long, String> ids = new HashMap<>();
        int[][] trunks = {
                {-12, -12, 10}, {0, -8, 12}, {13, -10, 11},
                {-9, 8, 9}, {8, 10, 12}, {18, 3, 8},
                {-22, 2, 9}, {-2, 20, 10}, {20, 18, 9}
        };
        for (int[] trunk : trunks) {
            int cx = trunk[0];
            int cz = trunk[1];
            int height = trunk[2];
            for (int y = 0; y < height; y++) {
                add(blocks, ids, new BlockPos(cx, y, cz), "minecraft:oak_log");
                if (y > 4 && y % 3 == 0) {
                    addBranch(blocks, ids, cx, y, cz, 1, 0, 4);
                    addBranch(blocks, ids, cx, y + 1, cz, -1, 0, 4);
                    addBranch(blocks, ids, cx, y, cz, 0, 1, 4);
                    addBranch(blocks, ids, cx, y + 1, cz, 0, -1, 4);
                }
            }
            addLeafBlob(blocks, ids, cx, height + 1, cz, 9, 5, 9);
            addLeafBlob(blocks, ids, cx + 5, height - 1, cz + 2, 7, 4, 6);
            addLeafBlob(blocks, ids, cx - 4, height, cz - 3, 6, 4, 7);
        }
        return new SkeletonScene("tree_canopy_10k", "deterministic multi-tree canopy", blocks, ids);
    }

    static SkeletonScene worldSpawnAreaTenK() {
        return loadScene(WORLD_SPAWN_SCENE);
    }

    static List<SkeletonScene> reportScenes() {
        return List.of(
                solidCube(22),
                chunkSeamTenK(),
                treeCanopyTenK(),
                worldSpawnAreaTenK());
    }

    static SkeletonScene loadScene(String resourcePath) {
        try (InputStream in = SkeletonSceneFixtures.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing skeleton scene resource: " + resourcePath);
            }
            return parseScene(resourcePath, in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skeleton scene: " + resourcePath, e);
        }
    }

    private static SkeletonScene parseScene(String resourcePath, InputStream in) throws IOException {
        String name = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
        String source = "resource";
        List<BlockPos> blocks = new ArrayList<>();
        Map<Long, String> ids = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals > 0) {
                    String key = line.substring(0, equals).trim();
                    String value = line.substring(equals + 1).trim();
                    if ("name".equals(key)) {
                        name = value;
                    } else if ("source".equals(key)) {
                        source = value;
                    }
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length != 6 || !"span".equals(parts[0])) {
                    throw new IllegalArgumentException(resourcePath + ":" + lineNumber
                            + " expected: span <blockId> <x0> <x1> <y> <z>");
                }
                String blockId = parts[1];
                int x0 = Integer.parseInt(parts[2]);
                int x1 = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);
                int z = Integer.parseInt(parts[5]);
                int minX = Math.min(x0, x1);
                int maxX = Math.max(x0, x1);
                for (int x = minX; x <= maxX; x++) {
                    add(blocks, ids, new BlockPos(x, y, z), blockId);
                }
            }
        }
        return new SkeletonScene(name, source, blocks, ids);
    }

    private static void addBranch(List<BlockPos> blocks, Map<Long, String> ids,
            int x, int y, int z, int dx, int dz, int length) {
        for (int i = 1; i <= length; i++) {
            add(blocks, ids, new BlockPos(x + dx * i, y + (i / 3), z + dz * i), "minecraft:oak_log");
        }
    }

    private static void addLeafBlob(List<BlockPos> blocks, Map<Long, String> ids,
            int cx, int cy, int cz, int rx, int ry, int rz) {
        double rx2 = rx * (double) rx;
        double ry2 = ry * (double) ry;
        double rz2 = rz * (double) rz;
        for (int x = cx - rx; x <= cx + rx; x++) {
            for (int y = cy - ry; y <= cy + ry; y++) {
                for (int z = cz - rz; z <= cz + rz; z++) {
                    double v = ((x - cx) * (double) (x - cx)) / rx2
                            + ((y - cy) * (double) (y - cy)) / ry2
                            + ((z - cz) * (double) (z - cz)) / rz2;
                    int rough = Math.floorMod((x * 7349) ^ (y * 9151) ^ (z * 11117), 17);
                    if (v <= 1.0D && rough != 0) {
                        add(blocks, ids, new BlockPos(x, y, z), "minecraft:oak_leaves");
                    }
                }
            }
        }
    }

    private static void add(List<BlockPos> blocks, Map<Long, String> ids, BlockPos pos, String blockId) {
        if (!ids.containsKey(pos.asLong())) {
            blocks.add(pos);
        }
        ids.put(pos.asLong(), blockId);
    }
}
