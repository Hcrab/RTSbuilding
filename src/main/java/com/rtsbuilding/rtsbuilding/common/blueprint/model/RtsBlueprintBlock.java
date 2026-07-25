package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 蓝图方块记录 —— 表示蓝图中单个方块的完整信息。
 * <p>
 * 包含该方块在蓝图内的相对坐标、方块状态、方块实体 NBT 数据、
 * 缺失方块标识符以及合成所需的材料物品 ID。
 * 如果方块在当前注册表中不存在，则标记为"缺失方块"。
 *
 * @param relativePos   在蓝图中的相对坐标（从 (0,0,0) 开始）
 * @param state         方块状态
 * @param blockEntityTag 方块实体的 NBT 标签（如箱子内容）
 * @param missingBlockId 缺失方块的 ID（为空字符串时表示方块存在）
 * @param materialItemId 建造该方块所需的材料物品 ID
 */
public record RtsBlueprintBlock(
        BlockPos relativePos,
        BlockState state,
        CompoundTag blockEntityTag,
        String missingBlockId,
        String materialItemId) {

    /**
     * 创建一个无缺失和材料 ID 的简单方块记录。
     *
     * @param relativePos   相对坐标
     * @param state         方块状态
     * @param blockEntityTag 方块实体 NBT
     */
    public RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag) {
        this(relativePos, state, blockEntityTag, "", "");
    }

    /**
     * 创建一个带缺失标识但无材料 ID 的方块记录。
     *
     * @param relativePos   相对坐标
     * @param state         方块状态
     * @param blockEntityTag 方块实体 NBT
     * @param missingBlockId 缺失方块 ID
     */
    public RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag, String missingBlockId) {
        this(relativePos, state, blockEntityTag, missingBlockId, "");
    }

    /**
     * 创建一个标记为"缺失"的方块记录。
     * <p>
     * 当解析的方块在当前注册表中不存在时使用，用于记录缺失信息以便后续处理。
     *
     * @param relativePos   相对坐标
     * @param missingBlockId 缺失方块的 ID
     * @param blockEntityTag 方块实体 NBT
     * @return 标记为缺失的方块记录
     */
    public static RtsBlueprintBlock missing(BlockPos relativePos, String missingBlockId, CompoundTag blockEntityTag) {
        return new RtsBlueprintBlock(
                relativePos,
                Blocks.AIR.defaultBlockState(),
                blockEntityTag == null ? new CompoundTag() : blockEntityTag,
                missingBlockId == null ? "" : missingBlockId,
                "");
    }

    /**
     * 检查该方块是否包含方块实体数据（如箱子、熔炉等）。
     *
     * @return true 如果存在非空方块实体 NBT
     */
    public boolean hasBlockEntityTag() {
        return this.blockEntityTag != null && !this.blockEntityTag.isEmpty();
    }

    /**
     * 检查该方块是否标记为"缺失"（在当前注册表中不存在）。
     *
     * @return true 如果方块缺失
     */
    public boolean isMissingBlock() {
        return this.missingBlockId != null && !this.missingBlockId.isBlank();
    }
}
