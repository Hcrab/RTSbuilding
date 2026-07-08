package com.rtsbuilding.rtsbuilding.client.rendering.builder;

/**
 * Normalized integer edge segment for skeleton geometry assertions.
 *
 * <p>Endpoints are sorted so the same segment compares equal regardless of
 * draw direction. The record intentionally stores integer grid coordinates:
 * merged skeleton edges are expected to sit on exact block-grid boundaries.</p>
 */
record SkeletonEdgeKey(int x1, int y1, int z1, int x2, int y2, int z2) {

    static SkeletonEdgeKey of(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (compareVertex(x1, y1, z1, x2, y2, z2) <= 0) {
            return new SkeletonEdgeKey(x1, y1, z1, x2, y2, z2);
        }
        return new SkeletonEdgeKey(x2, y2, z2, x1, y1, z1);
    }

    static SkeletonEdgeKey from(UltimineBlockMerger.EdgeLine line) {
        return of(
                exactGrid(line.x1()),
                exactGrid(line.y1()),
                exactGrid(line.z1()),
                exactGrid(line.x2()),
                exactGrid(line.y2()),
                exactGrid(line.z2()));
    }

    private static int exactGrid(double value) {
        int rounded = (int) Math.round(value);
        if (Math.abs(value - rounded) > 0.000001D) {
            throw new IllegalArgumentException("Expected integer-grid skeleton edge coordinate, got " + value);
        }
        return rounded;
    }

    private static int compareVertex(int x1, int y1, int z1, int x2, int y2, int z2) {
        if (x1 != x2) return Integer.compare(x1, x2);
        if (y1 != y2) return Integer.compare(y1, y2);
        return Integer.compare(z1, z2);
    }
}
