package com.rtsbuilding.rtsbuilding.common.blueprint.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;

/** Java 8 蓝图方块数据载体，保留缺失方块和旧材料字段的完整语义。 */
public final class RtsBlueprintBlock {
    private final BlockPos relativePos;
    private final IBlockState state;
    private final NBTTagCompound blockEntityTag;
    private final String missingBlockId;
    private final String materialItemId;

    public RtsBlueprintBlock(BlockPos relativePos, IBlockState state, NBTTagCompound blockEntityTag,
                             String missingBlockId, String materialItemId) {
        this.relativePos = relativePos;
        this.state = state;
        this.blockEntityTag = blockEntityTag == null ? new NBTTagCompound() : blockEntityTag;
        this.missingBlockId = missingBlockId == null ? "" : missingBlockId;
        this.materialItemId = materialItemId == null ? "" : materialItemId;
    }

    public RtsBlueprintBlock(BlockPos relativePos, IBlockState state, NBTTagCompound blockEntityTag) {
        this(relativePos, state, blockEntityTag, "", "");
    }

    public RtsBlueprintBlock(BlockPos relativePos, IBlockState state, NBTTagCompound blockEntityTag,
                             String missingBlockId) {
        this(relativePos, state, blockEntityTag, missingBlockId, "");
    }

    public static RtsBlueprintBlock missing(BlockPos relativePos, String missingBlockId,
                                            NBTTagCompound blockEntityTag) {
        return new RtsBlueprintBlock(relativePos, Blocks.AIR.getDefaultState(), blockEntityTag,
                missingBlockId, "");
    }

    public BlockPos relativePos() { return relativePos; }
    public IBlockState state() { return state; }
    public NBTTagCompound blockEntityTag() { return blockEntityTag; }
    public String missingBlockId() { return missingBlockId; }
    public String materialItemId() { return materialItemId; }
    public boolean hasBlockEntityTag() { return !blockEntityTag.isEmpty(); }
    public boolean isMissingBlock() { return !missingBlockId.trim().isEmpty(); }
}
