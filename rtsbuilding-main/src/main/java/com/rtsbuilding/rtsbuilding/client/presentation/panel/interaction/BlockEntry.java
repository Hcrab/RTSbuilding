package com.rtsbuilding.rtsbuilding.client.presentation.panel.interaction;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public record BlockEntry(BlockPos blockPos, BlockHitResult blockHit, String displayName, Vec3 hitLocation)
        implements SelectableEntry {

    /**
     * 归一化标识：多方块共用一个 GUI 的方块（如大箱子左右两半）统一返回组内代表坐标，
     * 用于标签去重与容器匹配（语义与 {@link ContainerGroupResolver} 一致）。
     */
    @Override
    public Object identifier() {
        return ContainerGroupResolver.normalize(blockPos);
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
