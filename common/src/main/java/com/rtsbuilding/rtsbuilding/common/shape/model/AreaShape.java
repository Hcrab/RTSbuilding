package com.rtsbuilding.rtsbuilding.common.shape.model;

/**
 * Area shape type enum, shared by both build and destroy operations.
 * <p>
 * Unifies the previously separate {@code BuildShape} (placement) and {@code AreaMineShape} (destroy) enums,
 * so that both sides of the system use the same ordinal values for network communication and shape generation.
 * <p>
 * Note: enum ordinals must remain stable as they are transmitted over the network as bytes.
 */
public enum AreaShape {
    /** Single block mode — placement or destruction of a single block */
    BLOCK,
    /** Line mode — a straight line along any axis */
    LINE,
    /** Square/rectangle mode — 2D planar area */
    SQUARE,
    /** Wall mode — vertical wall (baseline stretched along the XZ plane) */
    WALL,
    /** Circle mode — circle or cylinder on the XZ plane */
    CIRCLE,
    /** Box mode — 3D cuboid (solid block) */
    BOX
}
