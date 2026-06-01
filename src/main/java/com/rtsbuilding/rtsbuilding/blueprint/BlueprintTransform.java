package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public final class BlueprintTransform {
    private BlueprintTransform() {
    }

    public static int normalizeSteps(int steps) {
        return Math.floorMod(steps, 4);
    }

    public static BlockPos rotate(BlockPos pos, int ySteps, int xSteps, int zSteps) {
        if (pos == null) {
            return BlockPos.ZERO;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        int[] xyz = rotateY(x, y, z, normalizeSteps(ySteps));
        xyz = rotateX(xyz[0], xyz[1], xyz[2], normalizeSteps(xSteps));
        xyz = rotateZ(xyz[0], xyz[1], xyz[2], normalizeSteps(zSteps));
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    public static BlockState rotateState(BlockState state, int ySteps, int xSteps, int zSteps) {
        if (state == null) {
            return state;
        }
        BlockState out = state.rotate(rotationForYSteps(ySteps));
        int x = normalizeSteps(xSteps);
        int z = normalizeSteps(zSteps);
        if (x == 0 && z == 0) {
            return out;
        }
        for (Property<?> property : out.getProperties()) {
            Comparable<?> value = out.getValue(property);
            if (value instanceof Direction direction) {
                Direction rotated = rotateDirection(direction, x, z);
                out = setIfAllowed(out, property, rotated);
            } else if (value instanceof Axis axis) {
                Axis rotated = rotateAxis(axis, x, z);
                out = setIfAllowed(out, property, rotated);
            }
        }
        return out;
    }

    private static Rotation rotationForYSteps(int steps) {
        return switch (normalizeSteps(steps)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Direction rotateDirection(Direction direction, int xSteps, int zSteps) {
        int[] normal = new int[] {
                direction.getNormal().getX(),
                direction.getNormal().getY(),
                direction.getNormal().getZ()
        };
        normal = rotateX(normal[0], normal[1], normal[2], xSteps);
        normal = rotateZ(normal[0], normal[1], normal[2], zSteps);
        for (Direction candidate : Direction.values()) {
            if (candidate.getNormal().getX() == normal[0]
                    && candidate.getNormal().getY() == normal[1]
                    && candidate.getNormal().getZ() == normal[2]) {
                return candidate;
            }
        }
        return direction;
    }

    private static Axis rotateAxis(Axis axis, int xSteps, int zSteps) {
        Direction positive = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        return rotateDirection(positive, xSteps, zSteps).getAxis();
    }

    private static int[] rotateY(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { -z, y, x };
            case 2 -> new int[] { -x, y, -z };
            case 3 -> new int[] { z, y, -x };
            default -> new int[] { x, y, z };
        };
    }

    private static int[] rotateX(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { x, -z, y };
            case 2 -> new int[] { x, -y, -z };
            case 3 -> new int[] { x, z, -y };
            default -> new int[] { x, y, z };
        };
    }

    private static int[] rotateZ(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { -y, x, z };
            case 2 -> new int[] { -x, -y, z };
            case 3 -> new int[] { y, -x, z };
            default -> new int[] { x, y, z };
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState setIfAllowed(BlockState state, Property property, Comparable value) {
        return property.getPossibleValues().contains(value)
                ? state.setValue(property, value)
                : state;
    }
}
