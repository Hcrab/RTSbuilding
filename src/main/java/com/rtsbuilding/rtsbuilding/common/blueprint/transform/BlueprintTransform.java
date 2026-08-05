package com.rtsbuilding.rtsbuilding.common.blueprint.transform;

import com.rtsbuilding.rtsbuilding.platform.block.IProperty;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing.Axis;
import com.rtsbuilding.rtsbuilding.platform.block.Rotation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3i;

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
            return BlockPos.ORIGIN;
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
            return BlockPos.ORIGIN;
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
        return centerOffset == null ? rotated : rotated.add(centerOffset);
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
        BlockState out = state.withRotation(rotationForYSteps(ySteps));
        int x = normalizeSteps(xSteps);
        int z = normalizeSteps(zSteps);
        if (x == 0 && z == 0) {
            return out;
        }
        // 遍历所有属性，更新 Direction 和 Axis 类型
        for (IProperty<?> property : out.getPropertyKeys()) {
            Object value = out.getValue(property);
            if (value instanceof EnumFacing) {
                EnumFacing rotated = rotateDirection((EnumFacing) value, x, z);
                out = setValueUnsafe(out, property, rotated);
            } else if (value instanceof Axis) {
                Axis rotated = rotateAxis((Axis) value, x, z);
                out = setValueUnsafe(out, property, rotated);
            }
        }
        return out;
    }

    /**
     * 根据 Y 轴旋转步数获取对应的 {@link Rotation} 枚举。
     */
    private static Rotation rotationForYSteps(int steps) {
        switch (normalizeSteps(steps)) {
            case 1: return Rotation.CLOCKWISE_90;
            case 2: return Rotation.CLOCKWISE_180;
            case 3: return Rotation.COUNTERCLOCKWISE_90;
            default: return Rotation.NONE;
        }
    }

    /**
     * 旋转方向（支持 X 和 Z 轴旋转的组合）。
     */
    private static EnumFacing rotateDirection(EnumFacing direction, int xSteps, int zSteps) {
        int[] normal = new int[] {
                direction.getDirectionVec().getX(),
                direction.getDirectionVec().getY(),
                direction.getDirectionVec().getZ()
        };
        normal = rotateX(normal[0], normal[1], normal[2], xSteps);
        normal = rotateZ(normal[0], normal[1], normal[2], zSteps);
        for (EnumFacing candidate : EnumFacing.values()) {
            if (candidate.getDirectionVec().getX() == normal[0]
                    && candidate.getDirectionVec().getY() == normal[1]
                    && candidate.getDirectionVec().getZ() == normal[2]) {
                return candidate;
            }
        }
        return direction;
    }

    /**
     * 旋转轴。
     */
    private static Axis rotateAxis(Axis axis, int xSteps, int zSteps) {
        EnumFacing positive;
        switch (axis) {
            case X: positive = EnumFacing.EAST; break;
            case Y: positive = EnumFacing.UP; break;
            case Z: positive = EnumFacing.SOUTH; break;
            default: positive = EnumFacing.UP;
        }
        return rotateDirection(positive, xSteps, zSteps).getAxis();
    }

    /** 绕 Y 轴旋转坐标 */
    private static int[] rotateY(int x, int y, int z, int steps) {
        switch (steps) {
            case 1: return new int[] { -z, y, x };
            case 2: return new int[] { -x, y, -z };
            case 3: return new int[] { z, y, -x };
            default: return new int[] { x, y, z };
        }
    }

    /** 绕 X 轴旋转坐标 */
    private static int[] rotateX(int x, int y, int z, int steps) {
        switch (steps) {
            case 1: return new int[] { x, -z, y };
            case 2: return new int[] { x, -y, -z };
            case 3: return new int[] { x, z, -y };
            default: return new int[] { x, y, z };
        }
    }

    /** 绕 Z 轴旋转坐标 */
    private static int[] rotateZ(int x, int y, int z, int steps) {
        switch (steps) {
            case 1: return new int[] { -y, x, z };
            case 2: return new int[] { -x, -y, z };
            case 3: return new int[] { y, -x, z };
            default: return new int[] { x, y, z };
        }
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
    private static <T extends Comparable<T>> BlockState setIfAllowed(BlockState state, IProperty<T> property, T value) {
        return property.getAllowedValues().contains(value)
                ? state.withProperty(property, value)
                : state;
    }

    /**
     * 原始类型版本的 {@link #setIfAllowed}，避开泛型通配符捕获问题。
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static BlockState setValueUnsafe(BlockState state, IProperty property, Comparable value) {
        return property.getAllowedValues().contains(value)
                ? state.withProperty(property, value)
                : state;
    }
}
