package com.rtsbuilding.rtsbuilding.client.rendering.builder;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从方块集合提取真正可见的外轮廓边。
 *
 * <p>此类只负责几何，不持有渲染状态或世界引用。旧 1.12 移植曾先用 O(n²) 的 AABB
 * 贪心合并占位；不规则区域会保留盒子内部边，而且一万方块压力场景需要数分钟。
 * 现在直接统计暴露面的单位边：同一平面内被两张面共享的网格线被消除，不同朝向
 * 的面相交时保留真实折角。算法复杂度与方块数线性相关，行为与主线布尔体外轮廓一致。</p>
 */
final class UltimineBlockMerger {
    private static final double INFLATION = 0.005D;

    private UltimineBlockMerger() {
    }

    static List<EdgeLine> getEdgeLines(Collection<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) return new ArrayList<EdgeLine>();

        Set<Long> blocks = new HashSet<Long>(Math.max(16, positions.size() * 2));
        for (BlockPos pos : positions) if (pos != null) blocks.add(pos.toLong());

        Map<GridEdge, Exposure> candidates = new HashMap<GridEdge, Exposure>(Math.max(64, blocks.size() * 8));
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (!blocks.contains(new BlockPos(x + 1, y, z).toLong())) addFace(candidates, x, y, z, Face.EAST);
            if (!blocks.contains(new BlockPos(x - 1, y, z).toLong())) addFace(candidates, x, y, z, Face.WEST);
            if (!blocks.contains(new BlockPos(x, y + 1, z).toLong())) addFace(candidates, x, y, z, Face.UP);
            if (!blocks.contains(new BlockPos(x, y - 1, z).toLong())) addFace(candidates, x, y, z, Face.DOWN);
            if (!blocks.contains(new BlockPos(x, y, z + 1).toLong())) addFace(candidates, x, y, z, Face.SOUTH);
            if (!blocks.contains(new BlockPos(x, y, z - 1).toLong())) addFace(candidates, x, y, z, Face.NORTH);
        }

        List<EdgeLine> result = new ArrayList<EdgeLine>();
        for (Map.Entry<GridEdge, Exposure> entry : candidates.entrySet()) {
            Exposure exposure = entry.getValue();
            if (exposure.total == 1 || Integer.bitCount(exposure.faceMask) > 1) {
                result.add(entry.getKey().toLine(exposure.faceMask));
            }
        }
        return result;
    }

    private static void addFace(Map<GridEdge, Exposure> edges, int x, int y, int z, Face face) {
        int x1 = x + 1;
        int y1 = y + 1;
        int z1 = z + 1;
        switch (face) {
            case EAST:
                add(edges, x1, y, z, x1, y1, z, face); add(edges, x1, y1, z, x1, y1, z1, face);
                add(edges, x1, y1, z1, x1, y, z1, face); add(edges, x1, y, z1, x1, y, z, face); break;
            case WEST:
                add(edges, x, y, z, x, y, z1, face); add(edges, x, y, z1, x, y1, z1, face);
                add(edges, x, y1, z1, x, y1, z, face); add(edges, x, y1, z, x, y, z, face); break;
            case UP:
                add(edges, x, y1, z, x, y1, z1, face); add(edges, x, y1, z1, x1, y1, z1, face);
                add(edges, x1, y1, z1, x1, y1, z, face); add(edges, x1, y1, z, x, y1, z, face); break;
            case DOWN:
                add(edges, x, y, z, x1, y, z, face); add(edges, x1, y, z, x1, y, z1, face);
                add(edges, x1, y, z1, x, y, z1, face); add(edges, x, y, z1, x, y, z, face); break;
            case SOUTH:
                add(edges, x, y, z1, x1, y, z1, face); add(edges, x1, y, z1, x1, y1, z1, face);
                add(edges, x1, y1, z1, x, y1, z1, face); add(edges, x, y1, z1, x, y, z1, face); break;
            case NORTH:
                add(edges, x, y, z, x, y1, z, face); add(edges, x, y1, z, x1, y1, z, face);
                add(edges, x1, y1, z, x1, y, z, face); add(edges, x1, y, z, x, y, z, face); break;
            default:
                throw new AssertionError(face);
        }
    }

    private static void add(Map<GridEdge, Exposure> edges,
            int x1, int y1, int z1, int x2, int y2, int z2, Face face) {
        GridEdge edge = GridEdge.of(x1, y1, z1, x2, y2, z2);
        Exposure exposure = edges.get(edge);
        if (exposure == null) {
            exposure = new Exposure();
            edges.put(edge, exposure);
        }
        exposure.total++;
        exposure.faceMask |= face.bit;
    }

    static final class EdgeLine {
        private final double x1, y1, z1, x2, y2, z2;

        EdgeLine(double x1, double y1, double z1, double x2, double y2, double z2) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }

        double x1() { return x1; }
        double y1() { return y1; }
        double z1() { return z1; }
        double x2() { return x2; }
        double y2() { return y2; }
        double z2() { return z2; }
        float xn() { return (float) (x2 - x1); }
        float yn() { return (float) (y2 - y1); }
        float zn() { return (float) (z2 - z1); }
    }

    private enum Face {
        EAST(1, 1, 0, 0), WEST(2, -1, 0, 0),
        UP(4, 0, 1, 0), DOWN(8, 0, -1, 0),
        SOUTH(16, 0, 0, 1), NORTH(32, 0, 0, -1);

        final int bit;
        final int nx;
        final int ny;
        final int nz;

        Face(int bit, int nx, int ny, int nz) {
            this.bit = bit; this.nx = nx; this.ny = ny; this.nz = nz;
        }
    }

    private static final class Exposure {
        int total;
        int faceMask;
    }

    private static final class GridEdge {
        final int x1, y1, z1, x2, y2, z2;

        private GridEdge(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.x1 = x1; this.y1 = y1; this.z1 = z1;
            this.x2 = x2; this.y2 = y2; this.z2 = z2;
        }

        static GridEdge of(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (compare(x1, y1, z1, x2, y2, z2) <= 0) return new GridEdge(x1, y1, z1, x2, y2, z2);
            return new GridEdge(x2, y2, z2, x1, y1, z1);
        }

        EdgeLine toLine(int faceMask) {
            double ox = 0.0D, oy = 0.0D, oz = 0.0D;
            for (Face face : Face.values()) {
                if ((faceMask & face.bit) != 0) {
                    ox += face.nx * INFLATION;
                    oy += face.ny * INFLATION;
                    oz += face.nz * INFLATION;
                }
            }
            return new EdgeLine(x1 + ox, y1 + oy, z1 + oz, x2 + ox, y2 + oy, z2 + oz);
        }

        private static int compare(int x1, int y1, int z1, int x2, int y2, int z2) {
            if (x1 != x2) return x1 < x2 ? -1 : 1;
            if (y1 != y2) return y1 < y2 ? -1 : 1;
            return z1 == z2 ? 0 : (z1 < z2 ? -1 : 1);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof GridEdge)) return false;
            GridEdge that = (GridEdge) other;
            return x1 == that.x1 && y1 == that.y1 && z1 == that.z1
                    && x2 == that.x2 && y2 == that.y2 && z2 == that.z2;
        }

        @Override
        public int hashCode() {
            int hash = x1;
            hash = 31 * hash + y1; hash = 31 * hash + z1;
            hash = 31 * hash + x2; hash = 31 * hash + y2; hash = 31 * hash + z2;
            return hash;
        }
    }
}
