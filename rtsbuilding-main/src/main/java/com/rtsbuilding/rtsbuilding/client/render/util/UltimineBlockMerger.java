package com.rtsbuilding.rtsbuilding.client.render.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Merges adjacent block positions into larger AABBs and extracts individual
 * edge line segments via {@link VoxelShape#forAllEdges} for clean wireframe
 * rendering — inspired by the reference (FTB Ultimine style) rendering pipeline.
 *
 * <p><b>Pipeline:</b>
 * <ol>
 *   <li><b>Merge</b> — axis-aligned rectangles sharing a full face are merged
 *       iteratively into larger AABBs, eliminating internal edges.</li>
 *   <li><b>Shape combination</b> — each AABB is wrapped in a slightly inflated
 *       {@link VoxelShape} ({@value #INFLATION}) to avoid Z-fighting, then all
 *       shapes are combined with {@link BooleanOp#OR} and optimised.</li>
 *   <li><b>Edge extraction</b> — {@code forAllEdges()} yields every outer edge
 *       as an endpoint pair, ready for line rendering.</li>
 * </ol>
 *
 * <p>Used by {@code UltiminePreviewPass} to render the chain-mining preview
 * with the project's corner-bracket style.
 */
public final class UltimineBlockMerger {

    private static final double INFLATION = 0.005D;

    private UltimineBlockMerger() {
    }

    // ---- Public API -------------------------------------------------------

    /**
     * Extracts all outer-edge line segments from a collection of
     * outer-perimeter block positions.
     *
     * @param positions outer-perimeter block positions (must be non-empty)
     * @return list of edge lines, each defined by its two endpoints
     */
    public static List<EdgeLine> getEdgeLines(Collection<BlockPos> positions) {
        List<AABB> merged = merge(positions);

        VoxelShape combined = Shapes.empty();
        for (AABB aabb : merged) {
            VoxelShape box = Shapes.create(aabb.inflate(INFLATION));
            combined = Shapes.joinUnoptimized(combined, box, BooleanOp.OR);
        }
        combined = combined.optimize();

        List<EdgeLine> edges = new ArrayList<>();
        combined.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                edges.add(new EdgeLine(x1, y1, z1, x2, y2, z2)));
        return edges;
    }

    // ---- Edge record ------------------------------------------------------

    /**
     * One wireframe edge segment defined by its two 3-D endpoints in
     * absolute world coordinates.
     *
     * <p>The {@link #xn()}, {@link #yn()}, {@link #zn()} accessors
     * compute the direction (delta) vector of the edge, used as the
     * vertex normal for rendering.
     */
    public record EdgeLine(double x1, double y1, double z1, double x2, double y2, double z2) {

        public float xn() {
            return (float) (x2 - x1);
        }

        public float yn() {
            return (float) (y2 - y1);
        }

        public float zn() {
            return (float) (z2 - z1);
        }
    }

    // ---- Block-merging algorithm (axis-aligned rectangle merge) -----------

    private static List<AABB> merge(Collection<BlockPos> positions) {
        List<AABB> boxes = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            boxes.add(new AABB(
                    pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1));
        }

        boolean merged;
        do {
            merged = false;
            for (int axis = 0; axis < 3 && !merged; axis++) {
                for (int i = 0; i < boxes.size() && !merged; i++) {
                    for (int j = i + 1; j < boxes.size() && !merged; j++) {
                        AABB a = boxes.get(i);
                        AABB b = boxes.get(j);
                        boolean canMerge = switch (axis) {
                            case 0 -> canMergeAlongX(a, b);
                            case 1 -> canMergeAlongY(a, b);
                            case 2 -> canMergeAlongZ(a, b);
                            default -> false;
                        };
                        if (canMerge) {
                            boxes.set(i, switch (axis) {
                                case 0 -> new AABB(
                                        Math.min(a.minX, b.minX), a.minY, a.minZ,
                                        Math.max(a.maxX, b.maxX), a.maxY, a.maxZ);
                                case 1 -> new AABB(
                                        a.minX, Math.min(a.minY, b.minY), a.minZ,
                                        a.maxX, Math.max(a.maxY, b.maxY), a.maxZ);
                                case 2 -> new AABB(
                                        a.minX, a.minY, Math.min(a.minZ, b.minZ),
                                        a.maxX, a.maxY, Math.max(a.maxZ, b.maxZ));
                                default -> a;
                            });
                            boxes.remove(j);
                            merged = true;
                        }
                    }
                }
            }
        } while (merged);

        return boxes;
    }

    private static boolean canMergeAlongX(AABB a, AABB b) {
        return a.minY == b.minY && a.maxY == b.maxY
                && a.minZ == b.minZ && a.maxZ == b.maxZ
                && (a.maxX == b.minX || b.maxX == a.minX);
    }

    private static boolean canMergeAlongY(AABB a, AABB b) {
        return a.minX == b.minX && a.maxX == b.maxX
                && a.minZ == b.minZ && a.maxZ == b.maxZ
                && (a.maxY == b.minY || b.maxY == a.minY);
    }

    private static boolean canMergeAlongZ(AABB a, AABB b) {
        return a.minX == b.minX && a.maxX == b.maxX
                && a.minY == b.minY && a.maxY == b.maxY
                && (a.maxZ == b.minZ || b.maxZ == a.minZ);
    }
}
