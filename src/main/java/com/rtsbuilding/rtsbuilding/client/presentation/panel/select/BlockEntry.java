package com.rtsbuilding.rtsbuilding.client.presentation.panel.select;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;


public record BlockEntry(BlockPos blockPos, BlockHitResult blockHit, String displayName, Vec3 hitLocation)
        implements SelectableEntry {

    @Override
    public Object identifier() {
        return blockPos;
    }

    
    public ItemStack createStack() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            BlockState blockState = mc.level.getBlockState(blockPos);
            Block block = blockState.getBlock();
            return new ItemStack(block);
        }
        return ItemStack.EMPTY;
    }
}
