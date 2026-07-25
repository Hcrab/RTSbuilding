package com.rtsbuilding.rtsbuilding.common.blueprint.transform;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 蓝图变换工具 —— 提供蓝图的旋转和变换操作。
 * <p>
 * 支持绕 Y、X、Z 三个轴分别旋转任意 90° 倍数的角度，
 * 并正确旋转方块状态中的方向属性和轴属性。
 * 所有旋转步数都会归一化到 0~3 范围（每步 = 90°）。
 */
public final class BlueprintTransform {

    private BlueprintTransform() {
    }

    /**
     * 将旋转步数归一化到 0~3 范围（每步 = 90°）。
     *
     * @param steps 旋转步数（可为正负任意整数）
     * @return 归一化后的步数（0~3）
     */
    public static int normalizeSteps(int steps) {
        return Math.floorMod(steps, 4);
    }

    /**
     * 绕三个轴旋转一个坐标点。
     *
     * @param pos    要旋转的坐标
     * @param ySteps Y 轴旋转步数
     * @param xSteps X 轴旋转步数
     * @param zSteps Z 轴旋转步数
     * @return 旋转后的新坐标
     */
    public static BlockPos rotate(BlockPos pos, int ySteps, int xSteps, int zSteps) {
        if (pos == null) {
            return BlockPos.ZERO;
        }
        int[] xyz = rotateRaw(pos.getX(), pos.getY(), pos.getZ(), ySteps, xSteps, zSteps);
        return new BlockPos(xyz[0], xyz[1], xyz[2]);
    }

    /**
     * 计算旋转后需要居中偏移的量。
     * <p>
     * 通过对包围盒的 8 个角点全部旋转，计算新的包围盒范围，
     * 然后返回使旋转后的整体居中的偏移量。
     *
     * @param size    原始大小
     * @param ySteps  Y 轴旋转步数
     * @param xSteps  X 轴旋转步数
     * @param zSteps  Z 轴旋转步数
     * @return 居中偏移量
     */
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

        // 遍历包围盒的 8 个角点，计算旋转后的范围
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

        // 计算使旋转后整体居中的偏移量
        return new BlockPos(
                nearestInteger((maxX * 0.5D) - ((minRotX + maxRotX) * 0.5D)),
                nearestInteger((maxY * 0.5D) - ((minRotY + maxRotY) * 0.5D)),
                nearestInteger((maxZ * 0.5D) - ((minRotZ + maxRotZ) * 0.5D)));
    }

    /**
     * 绕中心旋转坐标点。
     * <p>
     * 先执行旋转，再应用居中偏移。
     *
     * @param pos          要旋转的坐标
     * @param ySteps       Y 轴旋转步数
     * @param xSteps       X 轴旋转步数
     * @param zSteps       Z 轴旋转步数
     * @param centerOffset 居中偏移量
     * @return 旋转并居中后的坐标
     */
    public static BlockPos rotateAroundCenter(BlockPos pos, int ySteps, int xSteps, int zSteps, BlockPos centerOffset) {
        BlockPos rotated = rotate(pos, ySteps, xSteps, zSteps);
        return centerOffset == null ? rotated : rotated.offset(centerOffset);
    }

    /**
     * 旋转方块状态。
     * <p>
     * 先使用 Minecraft 内置的 Y 轴旋转（Rotation），
     * 再手动处理 X/Z 轴旋转，更新所有 Direction 和 Axis 类型的属性值。
     *
     * @param state  要旋转的方块状态
     * @param ySteps Y 轴旋转步数
     * @param xSteps X 轴旋转步数
     * @param zSteps Z 轴旋转步数
     * @return 旋转后的方块状态
     */
    public static BlockState rotateState(BlockState state, int ySteps, int xSteps, int zSteps) {
        if (state == null) {
            return state;
        }
        // 先应用原版的 Y 轴旋转
        BlockState out = state.rotate(rotationForYSteps(ySteps));
        int x = normalizeSteps(xSteps);
        int z = normalizeSteps(zSteps);
        if (x == 0 && z == 0) {
            return out;
        }
        // 遍历所有属性，更新 Direction 和 Axis 类型
        for (Property<?> property : out.getProperties()) {
            Object value = out.getValue(property);
            if (value instanceof Direction direction) {
                Direction rotated = rotateDirection(direction, x, z);
                out = setValueUnsafe(out, property, rotated);
            } else if (value instanceof Axis axis) {
                Axis rotated = rotateAxis(axis, x, z);
                out = setValueUnsafe(out, property, rotated);
            }
        }
        return out;
    }

    /**
     * 根据 Y 轴旋转步数获取对应的 {@link Rotation} 枚举。
     */
    private static Rotation rotationForYSteps(int steps) {
        return switch (normalizeSteps(steps)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * 旋转方向（支持 X 和 Z 轴旋转的组合）。
     */
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

    /**
     * 旋转轴。
     */
    private static Axis rotateAxis(Axis axis, int xSteps, int zSteps) {
        Direction positive = switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
        return rotateDirection(positive, xSteps, zSteps).getAxis();
    }

    /** 绕 Y 轴旋转坐标 */
    private static int[] rotateY(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { -z, y, x };
            case 2 -> new int[] { -x, y, -z };
            case 3 -> new int[] { z, y, -x };
            default -> new int[] { x, y, z };
        };
    }

    /** 绕 X 轴旋转坐标 */
    private static int[] rotateX(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { x, -z, y };
            case 2 -> new int[] { x, -y, -z };
            case 3 -> new int[] { x, z, -y };
            default -> new int[] { x, y, z };
        };
    }

    /** 绕 Z 轴旋转坐标 */
    private static int[] rotateZ(int x, int y, int z, int steps) {
        return switch (steps) {
            case 1 -> new int[] { -y, x, z };
            case 2 -> new int[] { -x, -y, z };
            case 3 -> new int[] { y, -x, z };
            default -> new int[] { x, y, z };
        };
    }

    /** 执行三个轴的组合旋转 */
    private static int[] rotateRaw(int x, int y, int z, int ySteps, int xSteps, int zSteps) {
        int[] xyz = rotateY(x, y, z, normalizeSteps(ySteps));
        xyz = rotateX(xyz[0], xyz[1], xyz[2], normalizeSteps(xSteps));
        return rotateZ(xyz[0], xyz[1], xyz[2], normalizeSteps(zSteps));
    }

    /** 计算最接近的整数值（四舍五入） */
    private static int nearestInteger(double value) {
        return (int) Math.floor(value + 0.5D);
    }

    /**
     * 如果属性值在允许范围内，则设置属性值。
     * 防止旋转产生不合法的方块状态属性值。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <T extends Comparable<T>> BlockState setIfAllowed(BlockState state, Property<T> property, T value) {
        return property.getPossibleValues().contains(value)
                ? state.setValue(property, value)
                : state;
    }

    /**
     * 原始类型版本的 {@link #setIfAllowed}，避开泛型通配符捕获问题。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState setValueUnsafe(BlockState state, Property property, Comparable value) {
        return property.getPossibleValues().contains(value)
                ? state.setValue(property, value)
                : state;
    }
}
