package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
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
        int[] xyz = rotateRaw(pos.getX(), pos.getY(), pos.getZ(), ySteps, xSteps, zSteps);
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    public static BlockPos centerRotationOffset(Vec3i size, int ySteps, int xSteps, int zSteps) {
        if (size == null || size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0) {
            return BlockPos.ZERO;
        }
        int maxX = size.getX() - 1;
        int maxY = size.getY() - 1;
        int maxZ = size.getZ() - 1;
        int minRotX = Integer.MAX_VALUE;
        int minRotY = Integer.MAX_VALUE;
        int minRotZ = Integer.MAX_VALUE;
        int maxRotX = Integer.MIN_VALUE;
        int maxRotY = Integer.MIN_VALUE;
        int maxRotZ = Integer.MIN_VALUE;

        int[] xs = new int[] { 0, maxX };
        int[] ys = new int[] { 0, maxY };
        int[] zs = new int[] { 0, maxZ };
        for (int x : xs) {
            for (int y : ys) {
                for (int z : zs) {
                    int[] rotated = rotateRaw(x, y, z, ySteps, xSteps, zSteps);
                    minRotX = Math.min(minRotX, rotated[0]);
                    minRotY = Math.min(minRotY, rotated[1]);
                    minRotZ = Math.min(minRotZ, rotated[2]);
                    maxRotX = Math.max(maxRotX, rotated[0]);
                    maxRotY = Math.max(maxRotY, rotated[1]);
                    maxRotZ = Math.max(maxRotZ, rotated[2]);
                }
            }
        }

        return new BlockPos(
                nearestInteger((maxX * 0.5D) - ((minRotX + maxRotX) * 0.5D)),
                nearestInteger((maxY * 0.5D) - ((minRotY + maxRotY) * 0.5D)),
                nearestInteger((maxZ * 0.5D) - ((minRotZ + maxRotZ) * 0.5D)));
    }

    public static BlockPos rotateAroundCenter(BlockPos pos, int ySteps, int xSteps, int zSteps, BlockPos centerOffset) {
        BlockPos rotated = rotate(pos, ySteps, xSteps, zSteps);
        return centerOffset == null ? rotated : rotated.offset(centerOffset);
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

    private static int[] rotateRaw(int x, int y, int z, int ySteps, int xSteps, int zSteps) {
        int[] xyz = rotateY(x, y, z, normalizeSteps(ySteps));
        xyz = rotateX(xyz[0], xyz[1], xyz[2], normalizeSteps(xSteps));
        return rotateZ(xyz[0], xyz[1], xyz[2], normalizeSteps(zSteps));
    }

    private static int nearestInteger(double value) {
        return (int) Math.floor(value + 0.5D);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState setIfAllowed(BlockState state, Property property, Comparable value) {
        return property.getPossibleValues().contains(value)
                ? state.setValue(property, value)
                : state;
    }
}
