package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blueprint block record — represents the full information for a single block in a blueprint.
 * <p>
 * Contains the block's relative coordinates within the blueprint, block state, block entity NBT data,
 * missing block identifier, and the material item ID required for crafting.
 * If the block does not exist in the current registry, it is marked as a "missing block".
 *
 * @param relativePos   relative position in the blueprint (starting from (0,0,0))
 * @param state         block state
 * @param blockEntityTag NBT tag for the block entity (e.g., chest contents)
 * @param missingBlockId ID of the missing block (empty string means the block exists)
 * @param materialItemId material item ID needed to build this block
 */
public record RtsBlueprintBlock(
        BlockPos relativePos,
        BlockState state,
        CompoundTag blockEntityTag,
        String missingBlockId,
        String materialItemId) {

    /**
     * Create a simple block record without missing or material IDs.
     *
     * @param relativePos   relative position
     * @param state         block state
     * @param blockEntityTag block entity NBT
     */
    public RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag) {
        this(relativePos, state, blockEntityTag, "", "");
    }

    /**
     * Create a block record with a missing ID but no material ID.
     *
     * @param relativePos   relative position
     * @param state         block state
     * @param blockEntityTag block entity NBT
     * @param missingBlockId missing block ID
     */
    public RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag, String missingBlockId) {
        this(relativePos, state, blockEntityTag, missingBlockId, "");
    }

    /**
     * Create a block record marked as "missing".
     * <p>
     * Used when a parsed block does not exist in the current registry, recording missing info for later handling.
     *
     * @param relativePos   relative position
     * @param missingBlockId ID of the missing block
     * @param blockEntityTag block entity NBT
     * @return block record marked as missing
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
     * Check whether this block contains block entity data (e.g., chest, furnace, etc.).
     *
     * @return true if non-empty block entity NBT exists
     */
    public boolean hasBlockEntityTag() {
        return this.blockEntityTag != null && !this.blockEntityTag.isEmpty();
    }

    /**
     * Check whether this block is marked as "missing" (does not exist in the current registry).
     *
     * @return true if the block is missing
     */
    public boolean isMissingBlock() {
        return this.missingBlockId != null && !this.missingBlockId.isBlank();
    }
}
