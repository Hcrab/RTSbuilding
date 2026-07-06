package com.rtsbuilding.rtsbuilding.client.screen.panel.select;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 方块条目——对应框选内的一个可交互方块。
 *
 * @param blockPos    方块坐标
 * @param blockHit    方块命中结果
 * @param displayName 显示名称
 * @param hitLocation 命中位置
 */
public record BlockEntry(BlockPos blockPos, BlockHitResult blockHit, String displayName, Vec3 hitLocation)
        implements SelectableEntry {

    @Override
    public Object identifier() {
        return blockPos;
    }

    /**
     * 获取方块对应的物品图标栈（每帧调用，少量方块时开销可接受）。
     * <p>如需批量优化，可添加外部缓存层。</p>
     */
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
