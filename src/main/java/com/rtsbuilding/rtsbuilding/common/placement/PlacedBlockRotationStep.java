package com.rtsbuilding.rtsbuilding.common.placement;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * 把一次受限的 ±90° 旋转手势转换为方块能够表达的状态。
 *
 * <p>水平旋转优先调用方块注册实现提供的 {@link BlockState#rotate(Rotation)}，
 * 让模组机器沿用自身成熟的旋转规则。Minecraft 没有通用的竖直旋转 API，
 * 因此竖直手势只处理标准 facing、axis、half、slab 和 attach_face。
 * 客户端可用性判断与服务端落地必须共用本类，网络不得直接传 BlockState。</p>
 */
public final class PlacedBlockRotationStep {
    private PlacedBlockRotationStep() {
    }

    public static BlockState rotate(
            BlockState state,
            Direction axisDirection,
            int quarterTurns) {
        if (state == null || state.isAir() || axisDirection == null || quarterTurns == 0) {
            return state;
        }
        int step = quarterTurns > 0 ? 1 : -1;
        if (axisDirection.getAxis() == Direction.Axis.Y) {
            if (axisDirection == Direction.DOWN) {
                step = -step;
            }
            Rotation rotation = step > 0
                    ? Rotation.CLOCKWISE_90
                    : Rotation.COUNTERCLOCKWISE_90;
            BlockState registered = state.rotate(rotation);
            if (!registered.equals(state)) {
                return registered;
            }
            return rotateStandardHorizontal(state, step);
        }
        return rotateStandardVertical(state, axisDirection, step);
    }

    public static boolean supports(
            BlockState state,
            Direction axisDirection,
            int quarterTurns) {
        BlockState rotated = rotate(state, axisDirection, quarterTurns);
        return rotated != null && !rotated.equals(state);
    }

    private static BlockState rotateStandardHorizontal(BlockState state, int step) {
        BlockState result = state;
        if (result.hasProperty(BlockStateProperties.FACING)) {
            Direction facing = result.getValue(BlockStateProperties.FACING);
            if (facing.getAxis().isHorizontal()) {
                result = result.setValue(
                        BlockStateProperties.FACING,
                        step > 0 ? facing.getClockWise() : facing.getCounterClockWise());
            }
        }
        if (result.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            Direction facing = result.getValue(BlockStateProperties.FACING_HOPPER);
            if (facing.getAxis().isHorizontal()) {
                result = result.setValue(
                        BlockStateProperties.FACING_HOPPER,
                        step > 0 ? facing.getClockWise() : facing.getCounterClockWise());
            }
        }
        if (result.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = result.getValue(BlockStateProperties.HORIZONTAL_FACING);
            result = result.setValue(
                    BlockStateProperties.HORIZONTAL_FACING,
                    step > 0 ? facing.getClockWise() : facing.getCounterClockWise());
        }
        if (result.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis axis = result.getValue(BlockStateProperties.AXIS);
            if (axis != Direction.Axis.Y) {
                result = result.setValue(
                        BlockStateProperties.AXIS,
                        axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
            }
        } else if (result.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            Direction.Axis axis = result.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            result = result.setValue(
                    BlockStateProperties.HORIZONTAL_AXIS,
                    axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        if (result.hasProperty(BlockStateProperties.ROTATION_16)) {
            int current = result.getValue(BlockStateProperties.ROTATION_16);
            result = result.setValue(
                    BlockStateProperties.ROTATION_16,
                    Math.floorMod(current + step * 4, 16));
        }
        return result;
    }

    private static BlockState rotateStandardVertical(
            BlockState state,
            Direction axisDirection,
            int step) {
        BlockState result = state;
        boolean fullFacingChanged = false;

        if (result.hasProperty(BlockStateProperties.FACING)) {
            Direction current = result.getValue(BlockStateProperties.FACING);
            Direction rotated = rotateDirection(current, axisDirection, step);
            if (BlockStateProperties.FACING.getPossibleValues().contains(rotated)) {
                result = result.setValue(BlockStateProperties.FACING, rotated);
                fullFacingChanged = rotated != current;
            }
        }
        if (result.hasProperty(BlockStateProperties.FACING_HOPPER)) {
            Direction current = result.getValue(BlockStateProperties.FACING_HOPPER);
            Direction rotated = rotateDirection(current, axisDirection, step);
            if (BlockStateProperties.FACING_HOPPER.getPossibleValues().contains(rotated)) {
                result = result.setValue(BlockStateProperties.FACING_HOPPER, rotated);
                fullFacingChanged |= rotated != current;
            }
        }
        if (result.hasProperty(BlockStateProperties.AXIS)) {
            Direction.Axis current = result.getValue(BlockStateProperties.AXIS);
            Direction representative = positiveDirection(current);
            Direction.Axis rotated = rotateDirection(
                    representative, axisDirection, step).getAxis();
            result = result.setValue(BlockStateProperties.AXIS, rotated);
        }

        // 楼梯、活板门和半砖无法表达侧躺的中间态；上/下手势采用它们原生的两档状态。
        if (!fullFacingChanged && result.hasProperty(BlockStateProperties.HALF)) {
            result = result.setValue(
                    BlockStateProperties.HALF,
                    step > 0 ? Half.TOP : Half.BOTTOM);
        }
        if (!fullFacingChanged
                && result.getBlock() instanceof SlabBlock
                && result.hasProperty(BlockStateProperties.SLAB_TYPE)
                && result.getValue(BlockStateProperties.SLAB_TYPE) != SlabType.DOUBLE) {
            result = result.setValue(
                    BlockStateProperties.SLAB_TYPE,
                    step > 0 ? SlabType.TOP : SlabType.BOTTOM);
        }
        if (!fullFacingChanged && result.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            AttachFace current = result.getValue(BlockStateProperties.ATTACH_FACE);
            result = result.setValue(
                    BlockStateProperties.ATTACH_FACE,
                    nextAttachFace(current, step));
        }
        return result;
    }

    /**
     * 使用右手定则绕带正负号的世界轴旋转一个六向向量。
     */
    static Direction rotateDirection(
            Direction value,
            Direction axisDirection,
            int quarterTurns) {
        int sign = quarterTurns > 0 ? 1 : -1;
        int kx = axisDirection.getStepX();
        int ky = axisDirection.getStepY();
        int kz = axisDirection.getStepZ();
        int vx = value.getStepX();
        int vy = value.getStepY();
        int vz = value.getStepZ();
        int dot = kx * vx + ky * vy + kz * vz;
        int crossX = ky * vz - kz * vy;
        int crossY = kz * vx - kx * vz;
        int crossZ = kx * vy - ky * vx;
        return Direction.getNearest(
                sign * crossX + kx * dot,
                sign * crossY + ky * dot,
                sign * crossZ + kz * dot);
    }

    private static Direction positiveDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static AttachFace nextAttachFace(AttachFace current, int step) {
        return switch (current) {
            case FLOOR -> step > 0 ? AttachFace.WALL : AttachFace.CEILING;
            case WALL -> step > 0 ? AttachFace.CEILING : AttachFace.FLOOR;
            case CEILING -> step > 0 ? AttachFace.FLOOR : AttachFace.WALL;
        };
    }
}
