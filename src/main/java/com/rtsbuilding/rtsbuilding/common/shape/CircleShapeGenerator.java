package com.rtsbuilding.rtsbuilding.common.shape;

import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.ShapeFillMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Circle (cylinder) shape generator.
 * <p>
 * Generates a filled or hollow circle in the XZ plane.  The radius is derived
 * from the distance between start and end projected onto the plane.
 * Supports FILL and HOLLOW.
 */
public class CircleShapeGenerator extends AreaShapeGenerator {

    @Override
    public String getName() {
        return "circle";
    }

    @Override
    public List<BlockPos> generatePositions(AreaShapeInput input, ShapeFillMode fillMode) {
        int dx = input.end().getX() - input.start().getX();
        int dz = input.end().getZ() - input.start().getZ();

        double radius = Math.sqrt((dx * (double) dx) + (dz * (double) dz));
        int r = Math.max(0, (int) Math.round(radius));
        r = Math.min(r, 64);

        int outer2 = r * r;
        int inner = Math.max(0, r - 1);
        int inner2 = inner * inner;

        List<BlockPos> result = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int dist2 = x * x + z * z;
                boolean inOuter = dist2 <= outer2;
                boolean inInner = dist2 < inner2;
                if (!inOuter || (fillMode != ShapeFillMode.FILL && inInner)) {
                    continue;
                }
                result.add(input.start().offset(x, 0, z));
            }
        }

        if (fillMode == ShapeFillMode.FILL) {
            result = fillInternalHoles(result);
        }

        return result;
    }

    /**
     * Fills internal holes using a simple flood-fill approach on a projected
     * 2D grid.  This handles gaps in the circle rasterisation.
     */
    private static List<BlockPos> fillInternalHoles(List<BlockPos> positions) {
        if (positions.isEmpty()) return positions;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Determine a representative Y level
        int yLevel = positions.get(0).getY();

        java.util.Set<BlockPos> filled = new java.util.HashSet<>(positions);
        java.util.Set<BlockPos> outside = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();

        // Add all border cells as "outside" seeds
        for (int x = minX - 1; x <= maxX + 1; x++) {
            tryEnqueue(new BlockPos(x, yLevel, minZ - 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(x, yLevel, maxZ + 1), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }
        for (int z = minZ; z <= maxZ; z++) {
            tryEnqueue(new BlockPos(minX - 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(new BlockPos(maxX + 1, yLevel, z), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        while (!queue.isEmpty()) {
            BlockPos cur = queue.removeFirst();
            tryEnqueue(cur.east(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.west(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.north(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
            tryEnqueue(cur.south(), filled, outside, queue, minX - 1, maxX + 1, minZ - 1, maxZ + 1, yLevel);
        }

        List<BlockPos> dense = new ArrayList<>(positions);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, yLevel, z);
                if (!filled.contains(pos) && !outside.contains(pos)) {
                    dense.add(pos);
                }
            }
        }
        return dense;
    }

    private static void tryEnqueue(BlockPos pos, java.util.Set<BlockPos> filled,
                                    java.util.Set<BlockPos> outside, java.util.ArrayDeque<BlockPos> queue,
                                    int minX, int maxX, int minZ, int maxZ, int yLevel) {
        if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return;
        if (pos.getY() != yLevel) return;
        if (filled.contains(pos) || outside.contains(pos)) return;
        outside.add(pos);
        queue.addLast(pos);
    }
}
