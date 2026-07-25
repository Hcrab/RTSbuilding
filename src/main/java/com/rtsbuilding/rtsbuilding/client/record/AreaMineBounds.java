package com.rtsbuilding.rtsbuilding.client.record;

/**
 * 3D bounding box result of an area mine operation.
 * Shared between the client preview and server confirmation to eliminate
 * redundant calculations.
 */
public record AreaMineBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
}
