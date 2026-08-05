package com.rtsbuilding.rtsbuilding.common.geometry;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * Utility for combining, rotating and excluding {@link VoxelShape}s.
 * <p>
 * Ported from Mekanism's {@code mekanism.common.util.VoxelShapeUtils} — blocks
 * define their base shape with {@code box(...)} calls and use these helpers to
 * merge multiple cuboids and rotate them per facing.
 */
public final class RtsVoxelShapeUtils {

    private static final Vec3 fromOrigin = new Vec3(-0.5, -0.5, -0.5);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Direction[] HORIZONTAL = new Direction[] {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private RtsVoxelShapeUtils() {
    }

    /**
     * Converts pixel coordinates (0–16) into a {@link VoxelShape} cuboid.
     *
     * @param x1,y1,z1 The first corner in pixels (0–16).
     * @param x2,y2,z2 The opposite corner in pixels (0–16).
     */
    public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Shapes.create(new AABB(
                Math.min(x1, x2) / 16.0, Math.min(y1, y2) / 16.0, Math.min(z1, z2) / 16.0,
                Math.max(x1, x2) / 16.0, Math.max(y1, y2) / 16.0, Math.max(z1, z2) / 16.0));
    }

    /**
     * Rotates an {@link AABB} to a specific side, similar to how block states rotate models.
     */
    public static AABB rotate(AABB box, Direction side) {
        return switch (side) {
            case DOWN -> box;
            case UP -> new AABB(box.minX, -box.minY, -box.minZ, box.maxX, -box.maxY, -box.maxZ);
            case NORTH -> new AABB(box.minX, -box.minZ, box.minY, box.maxX, -box.maxZ, box.maxY);
            case SOUTH -> new AABB(-box.minX, -box.minZ, -box.minY, -box.maxX, -box.maxZ, -box.maxY);
            case WEST -> new AABB(box.minY, -box.minZ, -box.minX, box.maxY, -box.maxZ, -box.maxX);
            case EAST -> new AABB(-box.minY, -box.minZ, box.minX, -box.maxY, -box.maxZ, box.maxX);
        };
    }

    /**
     * Rotates an {@link AABB} according to a specific rotation.
     */
    public static AABB rotate(AABB box, Rotation rotation) {
        return switch (rotation) {
            case NONE -> box;
            case CLOCKWISE_90 -> new AABB(-box.minZ, box.minY, box.minX, -box.maxZ, box.maxY, box.maxX);
            case CLOCKWISE_180 -> new AABB(-box.minX, box.minY, -box.minZ, -box.maxX, box.maxY, -box.maxZ);
            case COUNTERCLOCKWISE_90 -> new AABB(box.minZ, box.minY, -box.minX, box.maxZ, box.maxY, -box.maxX);
        };
    }

    /**
     * Rotates an {@link AABB} horizontally, the most common rotation setup.
     */
    public static AABB rotateHorizontal(AABB box, Direction side) {
        return switch (side) {
            case NORTH -> rotate(box, Rotation.NONE);
            case SOUTH -> rotate(box, Rotation.CLOCKWISE_180);
            case WEST -> rotate(box, Rotation.COUNTERCLOCKWISE_90);
            case EAST -> rotate(box, Rotation.CLOCKWISE_90);
            default -> box;
        };
    }

    /**
     * Rotates a {@link VoxelShape} to a specific side.
     */
    public static VoxelShape rotate(VoxelShape shape, Direction side) {
        return rotate(shape, side, RtsVoxelShapeUtils::rotate);
    }

    /**
     * Rotates a {@link VoxelShape} according to a specific rotation.
     */
    public static VoxelShape rotate(VoxelShape shape, Rotation rotation) {
        return rotate(shape, rotation, RtsVoxelShapeUtils::rotate);
    }

    /**
     * Rotates a {@link VoxelShape} horizontally to a specific side.
     */
    public static VoxelShape rotateHorizontal(VoxelShape shape, Direction side) {
        return rotate(shape, side, RtsVoxelShapeUtils::rotateHorizontal);
    }

    /**
     * Rotates a {@link VoxelShape} using a transformation function per {@link AABB}.
     */
    public static VoxelShape rotate(VoxelShape shape, UnaryOperator<AABB> rotateFunction) {
        List<VoxelShape> rotatedPieces = new ArrayList<>();
        for (AABB sourceBoundingBox : shape.toAabbs()) {
            rotatedPieces.add(Shapes.create(rotateFunction.apply(sourceBoundingBox.move(
                    fromOrigin.x, fromOrigin.y, fromOrigin.z)).move(-fromOrigin.x, -fromOrigin.z, -fromOrigin.z)));
        }
        return combine(rotatedPieces);
    }

    /**
     * Rotates a {@link VoxelShape} using a per-{@link AABB} transformation with data.
     */
    public static <DATA> VoxelShape rotate(VoxelShape shape, DATA data, BiFunction<AABB, DATA, AABB> rotateFunction) {
        List<VoxelShape> rotatedPieces = new ArrayList<>();
        for (AABB sourceBoundingBox : shape.toAabbs()) {
            rotatedPieces.add(Shapes.create(rotateFunction.apply(sourceBoundingBox.move(
                    fromOrigin.x, fromOrigin.y, fromOrigin.z), data).move(-fromOrigin.x, -fromOrigin.z, -fromOrigin.z)));
        }
        return combine(rotatedPieces);
    }

    /**
     * Combines multiple shapes (OR).
     */
    public static VoxelShape combine(VoxelShape... shapes) {
        return batchCombine(Shapes.empty(), BooleanOp.OR, true, shapes);
    }

    /**
     * Combines a collection of shapes (OR).
     */
    public static VoxelShape combine(Collection<VoxelShape> shapes) {
        return batchCombine(Shapes.empty(), BooleanOp.OR, true, shapes);
    }

    /**
     * Returns everything that is not part of the given shapes (cut-out of a full cube).
     */
    public static VoxelShape exclude(VoxelShape... shapes) {
        return batchCombine(Shapes.block(), BooleanOp.ONLY_FIRST, true, shapes);
    }

    /**
     * Combines shapes using a specific {@link BooleanOp} and a start shape.
     */
    public static VoxelShape batchCombine(VoxelShape initial, BooleanOp function, boolean simplify, Collection<VoxelShape> shapes) {
        VoxelShape combinedShape = initial;
        for (VoxelShape shape : shapes) {
            combinedShape = Shapes.joinUnoptimized(combinedShape, shape, function);
        }
        return simplify ? combinedShape.optimize() : combinedShape;
    }

    /**
     * Combines shapes using a specific {@link BooleanOp} and a start shape.
     */
    public static VoxelShape batchCombine(VoxelShape initial, BooleanOp function, boolean simplify, VoxelShape... shapes) {
        VoxelShape combinedShape = initial;
        for (VoxelShape shape : shapes) {
            combinedShape = Shapes.joinUnoptimized(combinedShape, shape, function);
        }
        return simplify ? combinedShape.optimize() : combinedShape;
    }

    /**
     * Rotates a base shape and stores it for every axis.
     */
    public static void setShape(VoxelShape shape, VoxelShape[] dest, boolean verticalAxis) {
        setShape(shape, dest, verticalAxis, false);
    }

    /**
     * Rotates a base shape and stores it for every axis, optionally inverted.
     */
    public static void setShape(VoxelShape shape, VoxelShape[] dest, boolean verticalAxis, boolean invert) {
        Direction[] dirs = verticalAxis ? DIRECTIONS : HORIZONTAL;
        for (Direction side : dirs) {
            dest[verticalAxis ? side.ordinal() : side.ordinal() - 2] =
                    verticalAxis ? rotate(shape, invert ? side.getOpposite() : side) : rotateHorizontal(shape, side);
        }
    }

    /**
     * Rotates a base shape horizontally and stores it for every horizontal facing.
     */
    public static void setShape(VoxelShape shape, VoxelShape[] dest) {
        setShape(shape, dest, false, false);
    }
}
