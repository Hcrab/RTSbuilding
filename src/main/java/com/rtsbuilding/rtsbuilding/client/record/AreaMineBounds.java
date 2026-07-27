package com.rtsbuilding.rtsbuilding.client.record;

/**
 * 3D bounding box result of an area mine operation.
 * Shared between the client preview and server confirmation to eliminate
 * redundant calculations.
 */
public final class AreaMineBounds {
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public AreaMineBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public int minX() { return minX; }
    public int maxX() { return maxX; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int minZ() { return minZ; }
    public int maxZ() { return maxZ; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AreaMineBounds)) return false;
        AreaMineBounds value = (AreaMineBounds) other;
        return minX == value.minX && maxX == value.maxX
                && minY == value.minY && maxY == value.maxY
                && minZ == value.minZ && maxZ == value.maxZ;
    }

    @Override
    public int hashCode() {
        int result = minX;
        result = 31 * result + maxX;
        result = 31 * result + minY;
        result = 31 * result + maxY;
        result = 31 * result + minZ;
        return 31 * result + maxZ;
    }

    @Override
    public String toString() {
        return "AreaMineBounds[minX=" + minX + ", maxX=" + maxX
                + ", minY=" + minY + ", maxY=" + maxY
                + ", minZ=" + minZ + ", maxZ=" + maxZ + ']';
    }
}
