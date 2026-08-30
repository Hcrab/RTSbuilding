package com.rtsbuilding.rtsbuilding.server.service.placement;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Server-authoritative resolver for rotating an already placed block.
 *
 * <p>The client supplies only a property name and serialized value. Both are
 * looked up again on the current server state, and only direction, axis, and
 * vanilla 16-segment rotation properties are accepted.
 */
public final class RtsPlacedBlockRotation {
    private static final String CREATE_KINETIC_BLOCK_ENTITY =
            "com.simibubi.create.content.kinetics.base.KineticBlockEntity";
    /**
     * 数据包可扩展黑名单。普通同方块机器默认允许旋转；只有明确的多方块结构和
     * 被模组包作者列入本标签的控制器会被拒绝。
     */
    public static final TagKey<Block> ROTATION_BLACKLIST = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(
                    RtsbuildingMod.MODID, "rotation_blacklist"));

    private RtsPlacedBlockRotation() {
    }

    static boolean canReadNeighborhood(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return false;
        }
        for (Direction direction : Direction.values()) {
            if (!level.hasChunkAt(pos.relative(direction))) {
                return false;
            }
        }
        return true;
    }

    static boolean applyResolvedState(
            ServerLevel level, BlockPos pos, BlockState current, BlockState requested) {
        if (level == null || pos == null || current == null || requested == null
                || current.getBlock() != requested.getBlock()
                || isUnsafeState(current) || isUnsafeState(requested)) {
            return false;
        }

        BlockState adjusted = Block.updateFromNeighbourShapes(requested, level, pos);
        if (adjusted.getBlock() != current.getBlock()
                || isUnsafeState(adjusted)
                || !adjusted.canSurvive(level, pos)
                || adjusted.equals(current)) {
            return false;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && !blockEntity.getType().isValid(adjusted)) {
            return false;
        }

        boolean changed;
        if (blockEntity != null && isCreateKineticBlockEntity(blockEntity)) {
            changed = switchCreateKineticState(level, pos, adjusted);
        } else {
            // 同一个 Block 的状态切换通常会保留原 BlockEntity。执行后仍要求对象、
            // 类型与有效性完全不变，因此普通模组机器可以工作，而会重建控制器的
            // 多方块结构会被下面的后置检查拒绝。
            changed = level.setBlock(pos, adjusted, Block.UPDATE_ALL);
        }
        if (!changed
                || !level.getBlockState(pos).equals(adjusted)
                || (blockEntity != null
                && (level.getBlockEntity(pos) != blockEntity || blockEntity.isRemoved()))) {
            return false;
        }
        return true;
    }

    /**
     * 刚放置完成的方块尚未进入后续玩法流程，可以尝试同方块状态切换；仍要求方块实体
     * 对象原地保留，失败即关闭，不放宽世界中已有机器的通用旋转策略。
     */
    static boolean applyFreshPlacementState(
            ServerLevel level, BlockPos pos, BlockState current, BlockState requested) {
        if (level == null || pos == null || current == null || requested == null
                || current.getBlock() != requested.getBlock()
                || isUnsafeState(current) || isUnsafeState(requested)) {
            return false;
        }
        if (requested.equals(current)) {
            return true;
        }

        /*
         * 这是刚刚成功放置的同一个方块，不是对世界中未知机器的任意编辑。
         * 玩家在 R 面板已经明确选择了最终 BlockState，因此这里不再调用
         * updateFromNeighbourShapes/canSurvive 让原始点击位置第二次推翻预设。
         * 仍保留同方块、黑名单、方块实体类型和对象身份这几条窄安全边界。
         */
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null && !blockEntity.getType().isValid(requested)) {
            return false;
        }

        boolean changed = blockEntity != null && isCreateKineticBlockEntity(blockEntity)
                ? switchCreateKineticState(level, pos, requested)
                : level.setBlock(pos, requested, Block.UPDATE_ALL);
        return changed
                && level.getBlockState(pos).equals(requested)
                && (blockEntity == null
                || (level.getBlockEntity(pos) == blockEntity && !blockEntity.isRemoved()));
    }

    private static boolean isUnsafeState(BlockState state) {
        if (state.is(ROTATION_BLACKLIST)) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof BedBlock
                || block instanceof DoorBlock
                || block instanceof DoublePlantBlock
                || block instanceof MovingPistonBlock
                || block instanceof PistonHeadBlock) {
            return true;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                || state.hasProperty(BlockStateProperties.BED_PART)) {
            return true;
        }
        if (block instanceof ChestBlock
                && state.hasProperty(BlockStateProperties.CHEST_TYPE)
                && state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
            return true;
        }
        return block instanceof PistonBaseBlock
                && state.hasProperty(BlockStateProperties.EXTENDED)
                && state.getValue(BlockStateProperties.EXTENDED);
    }

    private static boolean isCreateKineticBlockEntity(BlockEntity blockEntity) {
        Class<?> type = blockEntity.getClass();
        while (type != null) {
            if (CREATE_KINETIC_BLOCK_ENTITY.equals(type.getName())) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /**
     * Create must detach/rejoin kinetic networks while changing state. Reflection
     * keeps Create optional while following its public static adapter when present.
     */
    private static boolean switchCreateKineticState(
            ServerLevel level, BlockPos pos, BlockState adjusted) {
        try {
            Class<?> kineticType = Class.forName(
                    CREATE_KINETIC_BLOCK_ENTITY,
                    false,
                    RtsPlacedBlockRotation.class.getClassLoader());
            Method switchMethod = kineticType.getMethod(
                    "switchToBlockState",
                    Level.class,
                    BlockPos.class,
                    BlockState.class);
            switchMethod.invoke(null, level, pos, adjusted);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException
                 | IllegalAccessException | InvocationTargetException ignored) {
            return false;
        }
    }

}
