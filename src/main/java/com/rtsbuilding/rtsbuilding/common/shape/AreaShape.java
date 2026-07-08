package com.rtsbuilding.rtsbuilding.common.shape;

/**
 * Shared area shape types used by both building and destruction operations.
 * <p>
 * Consolidates the previously separate {@code BuildShape} (placement) and
 * {@code AreaMineShape} (destruction) enums into a single shared set so that
 * both sides of the system use identical ordinal values for network
 * communication and shape generation.
 * <p>
 * Ordinals must remain stable ??they are transmitted over the network as
 * bytes in {@code C2SRtsAreaMinePayload}.
 */
public enum AreaShape {
    /** Single block placement/destruction. */
    BLOCK,
    /** 1D straight line along any axis. */
    LINE,
    /** 2D flat rectangular area. */
    SQUARE,
    /** Vertical wall (extruded line). */
    WALL,
    /** Circle / cylinder (XZ plane). */
    CIRCLE,
    /** 3D cuboid (solid box). */
    BOX,
    /** Cylinder with circular footprint and scroll-adjusted height. */
    CYLINDER,
    /** Sphere centered on the first point. */
    BALL
}
