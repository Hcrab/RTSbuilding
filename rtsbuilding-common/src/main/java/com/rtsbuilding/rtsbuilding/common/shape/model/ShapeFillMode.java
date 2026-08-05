package com.rtsbuilding.rtsbuilding.common.shape.model;

/**
 * Shape fill mode enum, defining the fill strategy for multi-block shapes when generating block positions.
 * <p>
 * Different fill modes determine which block coordinates the shape generator outputs:
 * <ul>
 *   <li>{@link #FILL} — solid fill, includes all positions within the shape</li>
 *   <li>{@link #HOLLOW} — hollow mode, includes only the shell (walls/surface)</li>
 *   <li>{@link #SKELETON} — skeleton mode, includes only edge wireframes (BOX shape only, displays 12 edges)</li>
 * </ul>
 */
public enum ShapeFillMode {

    /** Solid fill — includes all block positions inside the shape */
    FILL,

    /** Hollow mode — includes only the shell/surface layer of the shape */
    HOLLOW,

    /** Skeleton mode — includes only the edge wireframe of the shape (BOX shape only) */
    SKELETON
}
