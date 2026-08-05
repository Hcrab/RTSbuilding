package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.block.BlockSlab;
import com.rtsbuilding.rtsbuilding.platform.block.IProperty;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.init.Blocks;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import com.rtsbuilding.rtsbuilding.platform.block.Rotation;

import java.util.Collection;

/**
 * 把一次受限的正负 90 度旋转手势转换为 1.12 方块状态能够表达的变化。
 *
 * <p>水平旋转优先交给方块自身的 {@link net.minecraft.block.Block#withRotation}，
 * 以保留原版和第三方方块注册的旋转规则。竖直旋转没有统一 API，因此只对名称、
 * 类型和允许值都能确认的 facing、axis、half、slab 和 attach_face 属性做保守映射。</p>
 */
public final class PlacedBlockRotationStep {
    private PlacedBlockRotationStep() {
    }

    public static BlockState rotate(BlockState state, EnumFacing axisDirection, int quarterTurns) {
        if (state == null || state.getBlock() == Blocks.air || axisDirection == null || quarterTurns == 0) {
            return state;
        }
        int step = quarterTurns > 0 ? 1 : -1;
        if (axisDirection.getAxis() == EnumFacing.Axis.Y) {
            if (axisDirection == EnumFacing.DOWN) {
                step = -step;
            }
            Rotation rotation = step > 0 ? Rotation.CLOCKWISE_90 : Rotation.COUNTERCLOCKWISE_90;
            BlockState registered = state.withRotation(rotation);
            if (registered != null && !registered.equals(state)) {
                return registered;
            }
            return rotateStandardHorizontal(state, step);
        }
        return rotateStandardVertical(state, axisDirection, step);
    }

    public static boolean supports(BlockState state, EnumFacing axisDirection, int quarterTurns) {
        BlockState rotated = rotate(state, axisDirection, quarterTurns);
        return rotated != null && !rotated.equals(state);
    }

    private static BlockState rotateStandardHorizontal(BlockState state, int step) {
        BlockState result = state;
        for (IProperty<?> property : state.getPropertyKeys()) {
            String name = property.getName();
            Comparable<?> current = getValue(result, property);
            if (isFacingProperty(name) && current instanceof EnumFacing) {
                EnumFacing facing = (EnumFacing) current;
                if (facing.getAxis() != EnumFacing.Axis.Y) {
                    EnumFacing rotated = step > 0 ? facing.rotateY() : facing.rotateYCCW();
                    result = setAllowedValue(result, property, rotated);
                }
            } else if (isAxisProperty(name)) {
                String axis = valueName(property, current);
                if ("x".equals(axis)) {
                    result = setByName(result, property, "z");
                } else if ("z".equals(axis)) {
                    result = setByName(result, property, "x");
                }
            } else if ("rotation".equals(name) && current instanceof Integer) {
                int rotated = Math.floorMod(((Integer) current) + step * 4, 16);
                result = setByName(result, property, Integer.toString(rotated));
            }
        }
        return result;
    }

    private static BlockState rotateStandardVertical(
            BlockState state, EnumFacing axisDirection, int step) {
        BlockState result = state;
        boolean fullFacingChanged = false;

        for (IProperty<?> property : state.getPropertyKeys()) {
            String name = property.getName();
            Comparable<?> current = getValue(result, property);
            if (isFacingProperty(name) && current instanceof EnumFacing) {
                EnumFacing rotated = rotateDirection((EnumFacing) current, axisDirection, step);
                BlockState changed = setAllowedValue(result, property, rotated);
                fullFacingChanged |= !changed.equals(result);
                result = changed;
            } else if (isAxisProperty(name)) {
                String axis = valueName(property, current);
                EnumFacing representative = positiveDirection(axis);
                if (representative != null) {
                    String rotatedAxis = axisName(
                            rotateDirection(representative, axisDirection, step).getAxis());
                    result = setByName(result, property, rotatedAxis);
                }
            }
        }

        if (!fullFacingChanged) {
            IProperty<?> half = findProperty(result, "half");
            if (half != null) {
                result = setByNames(result, half,
                        step > 0 ? new String[]{"top", "upper"} : new String[]{"bottom", "lower"});
            }

            if (result.getBlock() instanceof BlockSlab
                    && !com.rtsbuilding.rtsbuilding.platform.block.BlockCompat.isDoubleSlab(
                            (BlockSlab) result.getBlock())) {
                IProperty<?> slabHalf = findProperty(result, "half");
                if (slabHalf != null) {
                    result = setByName(result, slabHalf, step > 0 ? "top" : "bottom");
                }
            }

            IProperty<?> attachFace = findProperty(result, "attach_face");
            if (attachFace != null) {
                String current = valueName(attachFace, getValue(result, attachFace));
                String next = nextAttachFace(current, step);
                if (next != null) {
                    result = setByName(result, attachFace, next);
                }
            }
        }
        return result;
    }

    /** 使用右手定则绕带正负号的世界轴旋转一个六方向向量。 */
    static EnumFacing rotateDirection(EnumFacing value, EnumFacing axisDirection, int quarterTurns) {
        int sign = quarterTurns > 0 ? 1 : -1;
        int kx = axisDirection.getXOffset();
        int ky = axisDirection.getYOffset();
        int kz = axisDirection.getZOffset();
        int vx = value.getXOffset();
        int vy = value.getYOffset();
        int vz = value.getZOffset();
        int dot = kx * vx + ky * vy + kz * vz;
        int crossX = ky * vz - kz * vy;
        int crossY = kz * vx - kx * vz;
        int crossZ = kx * vy - ky * vx;
        return EnumFacing.getFacingFromVector(
                sign * crossX + kx * dot,
                sign * crossY + ky * dot,
                sign * crossZ + kz * dot);
    }

    private static boolean isFacingProperty(String name) {
        return "facing".equals(name) || "horizontal_facing".equals(name);
    }

    private static boolean isAxisProperty(String name) {
        return "axis".equals(name) || "horizontal_axis".equals(name);
    }

    private static EnumFacing positiveDirection(String axisName) {
        if ("x".equals(axisName)) return EnumFacing.EAST;
        if ("y".equals(axisName)) return EnumFacing.UP;
        if ("z".equals(axisName)) return EnumFacing.SOUTH;
        return null;
    }

    private static String axisName(EnumFacing.Axis axis) {
        if (axis == EnumFacing.Axis.X) return "x";
        if (axis == EnumFacing.Axis.Y) return "y";
        return "z";
    }

    private static String nextAttachFace(String current, int step) {
        if ("floor".equals(current)) return step > 0 ? "wall" : "ceiling";
        if ("wall".equals(current)) return step > 0 ? "ceiling" : "floor";
        if ("ceiling".equals(current)) return step > 0 ? "floor" : "wall";
        return null;
    }

    private static IProperty<?> findProperty(BlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (name.equals(property.getName())) return property;
        }
        return null;
    }

    private static BlockState setByNames(BlockState state, IProperty<?> property, String[] names) {
        for (String name : names) {
            BlockState changed = setByName(state, property, name);
            if (!changed.equals(state)) return changed;
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setByName(BlockState state, IProperty property, String valueName) {
        Collection<? extends Comparable> allowed = property.getAllowedValues();
        for (Comparable candidate : allowed) {
            if (valueName.equals(property.getName(candidate))) {
                return state.withProperty(property, candidate);
            }
        }
        return state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setAllowedValue(BlockState state, IProperty property, Comparable value) {
        return property.getAllowedValues().contains(value) ? state.withProperty(property, value) : state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Comparable<?> getValue(BlockState state, IProperty property) {
        return state.getValue(property);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String valueName(IProperty property, Comparable value) {
        return value == null ? "" : property.getName(value);
    }
}
