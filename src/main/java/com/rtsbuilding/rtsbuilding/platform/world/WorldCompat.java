package com.rtsbuilding.rtsbuilding.platform.world;

import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;

/**
 * 收口 World 在 1.7.10 与后续版本之间的查询差异。
 *
 * <p>这里的“已加载”只查询现有区块，不主动生成或加载新区块。远程 GUI、储存和施工若需要
 * 强加载，应继续走各自的租约机制，不能把一个廉价查询悄悄变成加载动作。</p>
 */
public final class WorldCompat {
    private WorldCompat() {}

    public static boolean isBlockLoaded(World world, BlockPos pos) {
        return world != null && pos != null && world.blockExists(pos.getX(), pos.getY(), pos.getZ());
    }

    /** 兼容后续版本的 allowEmpty 形参；1.7.10 没有对应的空区块状态，仍执行纯查询。 */
    public static boolean isBlockLoaded(World world, BlockPos pos, boolean allowEmpty) {
        return isBlockLoaded(world, pos);
    }

    /** 1.7.10 的破坏进度仍使用拆开的整数坐标。 */
    public static void sendBlockBreakProgress(
            World world, int breakerId, BlockPos pos, int progress) {
        if (world == null || pos == null) return;
        world.destroyBlockInWorldPartially(
                breakerId, pos.getX(), pos.getY(), pos.getZ(), progress);
    }

    public static TileEntity getTileEntity(World world, BlockPos pos) {
        return world == null || pos == null ? null
                : world.getTileEntity(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isAirBlock(World world, BlockPos pos) {
        return world == null || pos == null || world.isAirBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isReplaceable(World world, BlockPos pos) {
        return world != null && pos != null && world.getBlock(pos.getX(), pos.getY(), pos.getZ())
                .isReplaceable(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean isBlockModifiable(World world, EntityPlayer player, BlockPos pos) {
        return world != null && player != null && pos != null
                && world.canMineBlock(player, pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean destroyBlock(World world, BlockPos pos, boolean drop) {
        if (world == null || pos == null) return false;
        if (drop) world.func_147480_a(pos.getX(), pos.getY(), pos.getZ(), true);
        else world.setBlockToAir(pos.getX(), pos.getY(), pos.getZ());
        return world.isAirBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void notifyBlockUpdate(World world, BlockPos pos, BlockState oldState,
            BlockState newState, int flags) {
        if (world == null || pos == null) return;
        world.markBlockForUpdate(pos.getX(), pos.getY(), pos.getZ());
    }

    public static boolean spawnItem(World world, BlockPos pos, ItemStack stack) {
        if (world == null || pos == null
                || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return false;
        EntityItem entity = new EntityItem(world,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        entity.delayBeforeCanPickup = 10;
        return world.spawnEntityInWorld(entity);
    }
}
