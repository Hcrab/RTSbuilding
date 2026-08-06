package com.rtsbuilding.rtsbuilding.server.service.placement;

import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RtsQuickBuildFrozenStateTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Test
    void detachedQuickBuildDefinitionRoundTripsTheFinalBlockState() {
        IBlockState expected = Blocks.OAK_STAIRS.getDefaultState()
                .withProperty(BlockStairs.FACING, EnumFacing.EAST)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        NBTTagCompound definition = minimalQuickBuildDefinition();
        definition.setTag("frozenPlacementState",
                NBTUtil.writeBlockState(new NBTTagCompound(), expected));

        RtsPlacementBatch.PlaceBatchJob restored = RtsPlacementBatch.PlaceBatchJob.fromNbt(definition);
        IBlockState actual = restored.frozenPlacementState();

        assertNotNull(actual);
        assertEquals(expected, actual);
        NBTTagCompound encoded = restored.toNbt();
        assertEquals(expected, NBTUtil.readBlockState(encoded.getCompoundTag(
                "frozenPlacementState")));
    }

    private static NBTTagCompound minimalQuickBuildDefinition() {
        NBTTagCompound definition = new NBTTagCompound();
        NBTTagList positions = new NBTTagList();
        positions.appendTag(new NBTTagLong(new BlockPos(4, 65, 8).toLong()));
        definition.setTag("positions", positions);
        definition.setByte("face", (byte) EnumFacing.UP.getIndex());
        definition.setDouble("hitOffsetX", 0.5D);
        definition.setDouble("hitOffsetY", 0.5D);
        definition.setDouble("hitOffsetZ", 0.5D);
        definition.setByte("rotateSteps", (byte) 0);
        definition.setString("statePreset", "");
        definition.setBoolean("forcePlace", false);
        definition.setBoolean("skipIfOccupied", false);
        definition.setBoolean("overwriteExisting", false);
        definition.setString("itemId", "minecraft:oak_stairs");
        definition.setBoolean("quickBuild", true);
        definition.setBoolean("forceEmptyHand", false);
        definition.setBoolean("sendRemoteHint", true);
        definition.setInteger("workflowEntryId", 12);
        definition.setInteger("index", 0);
        return definition;
    }
}
