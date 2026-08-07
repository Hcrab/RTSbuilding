package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementExtractor;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.items.IItemHandler;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端 Ctrl+Z / Ctrl+Y 执行器。
 *
 * <p>每个成功位置都会显式返回给历史管理器；跳过的未加载、被占用或权限拒绝位置仍留在原栈。
 * 本类不拥有历史栈，也不改变 RTS 会话状态。</p>
 */
public final class HistoryExecutor {
    private HistoryExecutor() {}

    public static HistoryExecutionResult executeUndo(EntityPlayerMP player, HistoryEntry entry) {
        Set<BlockPos> completed;
        switch (entry.getOperation()) {
            case CREATIVE_BREAK:
                completed = restoreBrokenBlocks(player, entry.getBlocks(), true, -1);
                break;
            case SURVIVAL_BREAK:
                completed = restoreBrokenBlocks(player, entry.getBlocks(), false, entry.getSourceSlot());
                break;
            case CREATIVE_PLACEMENT:
                completed = restorePlacementSnapshot(player, entry.getBlocks(), true);
                break;
            case SURVIVAL_PLACEMENT:
                completed = restorePlacementSnapshot(player, entry.getBlocks(), false);
                break;
            default:
                completed = new LinkedHashSet<BlockPos>();
        }
        return new HistoryExecutionResult(completed.size(), completed);
    }

    /** 重做第一阶段只支持创造历史；生存记录绝不产生物品或世界副作用。 */
    public static HistoryExecutionResult executeRedo(EntityPlayerMP player, HistoryEntry entry) {
        Set<BlockPos> completed = entry.getOperation().creative()
                ? applyAfterSnapshot(player, entry)
                : new LinkedHashSet<BlockPos>();
        return new HistoryExecutionResult(completed.size(), completed);
    }

    private static Set<BlockPos> restoreBrokenBlocks(
            EntityPlayerMP player, List<HistoryBlockRecord> records, boolean creative, int sourceSlot) {
        WorldServer world = player.getServerWorld();
        Set<BlockPos> completed = new LinkedHashSet<BlockPos>();
        for (HistoryBlockRecord record : records) {
            BlockPos pos = record.pos();
            if (!world.isBlockLoaded(pos) || !RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            if (!world.getBlockState(pos).equals(record.afterState())) continue;

            ItemStack consumed = ItemStack.EMPTY;
            if (!creative) {
                consumed = consumeItemForBlock(player, record.state(), sourceSlot);
                if (consumed.isEmpty()) continue;
            }
            if (!world.setBlockState(pos, record.state(), 3)) {
                if (!creative) refundItem(player, consumed, pos);
                continue;
            }
            if (creative) restoreBlockEntity(world, pos, record.blockEntityData());
            completed.add(pos);
        }
        if (!creative) refreshStorage(player);
        return completed;
    }

    /** 建造撤销恢复操作前快照；生存模式移除成功后才退还对应材料。 */
    private static Set<BlockPos> restorePlacementSnapshot(
            EntityPlayerMP player, List<HistoryBlockRecord> records, boolean creative) {
        WorldServer world = player.getServerWorld();
        Set<BlockPos> completed = new LinkedHashSet<BlockPos>();
        for (HistoryBlockRecord record : records) {
            BlockPos pos = record.pos();
            if (!world.isBlockLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, EnumFacing.UP)) continue;
            if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            if (!world.getBlockState(pos).equals(record.afterState())) continue;
            if (!world.setBlockState(pos, record.state(), 3)) continue;
            if (creative) {
                restoreBlockEntity(world, pos, record.blockEntityData());
            } else {
                refundItem(player, blockItem(record.afterState()), pos);
            }
            completed.add(pos);
        }
        if (!creative) refreshStorage(player);
        return completed;
    }

    /** 重做前必须仍与撤销留下的前快照一致，避免覆盖玩家撤销后的手动修改。 */
    private static Set<BlockPos> applyAfterSnapshot(EntityPlayerMP player, HistoryEntry entry) {
        WorldServer world = player.getServerWorld();
        Set<BlockPos> completed = new LinkedHashSet<BlockPos>();
        for (HistoryBlockRecord record : entry.getBlocks()) {
            BlockPos pos = record.pos();
            if (!world.isBlockLoaded(pos) || !matchesSnapshot(world, pos, record.state(), record.blockEntityData())) {
                continue;
            }
            if (entry.getOperation() == HistoryOperation.CREATIVE_BREAK) {
                if (!RtsClaimProtectionService.canBreakBlock(player, pos, EnumFacing.UP)) continue;
            } else {
                if (record.state().getBlock() != Blocks.AIR
                        && !RtsClaimProtectionService.canBreakBlock(player, pos, EnumFacing.UP)) continue;
                if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            }
            if (!world.setBlockState(pos, record.afterState(), 3)) continue;
            restoreBlockEntity(world, pos, record.afterBlockEntityData());
            completed.add(pos);
        }
        return completed;
    }

    private static boolean matchesSnapshot(
            WorldServer world, BlockPos pos, IBlockState expectedState, NBTTagCompound expectedNbt) {
        if (!world.getBlockState(pos).equals(expectedState)) return false;
        if (expectedNbt == null) return true;
        TileEntity blockEntity = world.getTileEntity(pos);
        return blockEntity != null
                && expectedNbt.equals(blockEntity.writeToNBT(new NBTTagCompound()));
    }

    /** 返回真实抽取的一件物品，失败时返回空栈，供失败回滚保持 NBT。 */
    private static ItemStack consumeItemForBlock(
            EntityPlayerMP player, IBlockState state, int sourceSlot) {
        ItemStack required = blockItem(state);
        if (required.isEmpty()) return ItemStack.EMPTY;

        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForExtract(linked);
            ItemStack extracted = RtsPlacementExtractor.extractSelectedFromLinkedCached(
                    player, handlers, required.getItem(), required);
            if (!extracted.isEmpty()) return extracted;
        }

        if (sourceSlot < 0 || sourceSlot > 8) return ItemStack.EMPTY;
        InventoryPlayer inventory = player.inventory;
        ItemStack source = inventory.getStackInSlot(sourceSlot);
        if (source.isEmpty() || source.getItem() != required.getItem()
                || source.getMetadata() != required.getMetadata()) return ItemStack.EMPTY;
        ItemStack extracted = source.copy();
        extracted.setCount(1);
        source.shrink(1);
        inventory.setInventorySlotContents(
                sourceSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        inventory.markDirty();
        return extracted;
    }

    /** 1.12 方块物品身份包含 damage/metadata；不能退化成固定 metadata=0。 */
    private static ItemStack blockItem(IBlockState state) {
        Item item = Item.getItemFromBlock(state.getBlock());
        return item == null ? ItemStack.EMPTY
                : new ItemStack(item, 1, state.getBlock().damageDropped(state));
    }

    /** 真实栈回退顺序：linked storage → 玩家背包 → 原地掉落。 */
    private static void refundItem(EntityPlayerMP player, ItemStack stack, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return;
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
            if (!handlers.isEmpty()) {
                RtsTransferInserter.refundToLinked(handlers, player, stack);
                return;
            }
        }
        if (!player.inventory.addItemStackToInventory(stack)) {
            Block.spawnAsEntity(player.getServerWorld(), pos, stack);
        }
    }

    private static void restoreBlockEntity(WorldServer world, BlockPos pos, NBTTagCompound data) {
        if (data == null) return;
        TileEntity blockEntity = world.getTileEntity(pos);
        if (blockEntity == null) return;
        blockEntity.readFromNBT(data);
        blockEntity.markDirty();
    }

    private static void refreshStorage(EntityPlayerMP player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
    }
}
