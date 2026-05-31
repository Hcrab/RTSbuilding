package com.rtsbuilding.rtsbuilding.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public record RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag, String missingBlockId) {
    public RtsBlueprintBlock(BlockPos relativePos, BlockState state, CompoundTag blockEntityTag) {
        this(relativePos, state, blockEntityTag, "");
    }

    public static RtsBlueprintBlock missing(BlockPos relativePos, String missingBlockId, CompoundTag blockEntityTag) {
        return new RtsBlueprintBlock(
                relativePos,
                Blocks.AIR.defaultBlockState(),
                blockEntityTag == null ? new CompoundTag() : blockEntityTag,
                missingBlockId == null ? "" : missingBlockId);
    }

    public boolean hasBlockEntityTag() {
        return this.blockEntityTag != null && !this.blockEntityTag.isEmpty();
    }

    public boolean isMissingBlock() {
        return this.missingBlockId != null && !this.missingBlockId.isBlank();
    }
}
