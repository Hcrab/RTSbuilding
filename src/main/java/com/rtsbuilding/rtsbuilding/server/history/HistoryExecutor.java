package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import com.rtsbuilding.rtsbuilding.platform.storage.IItemHandler;

import java.util.List;

/**
 * 历史记录执行器（类似 Ultimine-Rewind 的 RewindExecutor）。
 * <p>
 * 负责实际执行撤回/重做操作，包括放置和破坏方块。
 * 所有操作在服务端执行，保证数据一致性。
 * <p>
 * 设计要点（基于 Ultimine-Rewind 的经验）：
 * <ul>
 *   <li>创造模式恢复方块实体 NBT 数据</li>
 *   <li>生存模式不恢复 NBT（防刷物品漏洞）</li>
 *   <li>跳过已被占用的位置（部分恢复）</li>
 *   <li>破坏时只删除与记录类型相同的方块（防止误破坏）</li>
 * </ul>
 */
public final class HistoryExecutor {

    private HistoryExecutor() {
    }

    /**
     * 执行撤回操作。
     * <p>
     * 放置批次→破坏每个方块；破坏批次→恢复每个方块。
     *
     * @param player 操作的玩家
     * @param entry  要撤回的历史记录
     * @return 实际成功处理的方块数量（可能小于总数，如位置已被占用时跳过）
     */
    public static int executeUndo(EntityPlayerMP player, HistoryEntry entry) {
        if (entry.isDestructive()) {
            // 破坏批次→撤回=重新放置方块
            return restoreBlocks(player, entry.getBlocks(), entry.getFace());
        } else {
            // 放置批次→撤回=破坏方块
            return breakBlocks(player, entry.getBlocks());
        }
    }

    // ======================================================================
    //  内部执行逻辑
    // ======================================================================

    /**
     * 恢复方块（重新放置）。
     * <p>
     * 仅在目标位置为空气或可替换方块时才放置。
     * 跳过已被占用的位置。
     * 创造模式额外恢复方块实体 NBT 数据（类似 Ultimine-Rewind 的 RewindExecutor）。
     */
    private static int restoreBlocks(EntityPlayerMP player, List<HistoryBlockRecord> blocks, com.rtsbuilding.rtsbuilding.platform.math.EnumFacing face) {
        WorldServer level = player.getServerForPlayer();
        boolean isCreative = player.capabilities.isCreativeMode;
        int restoredCount = 0;

        for (HistoryBlockRecord record : blocks) {
            BlockPos pos = record.pos();
            if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) continue;
            if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;

            BlockState currentState = BlockState.fromWorld(level, pos);
            if (currentState.getBlock() != Blocks.air
                    && !com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isReplaceable(level, pos)) {
                continue; // 位置已被占用，跳过
            }

            BlockState targetState = record.state();

            // 生存模式：验证并消耗物品（防止刷物品漏洞）
            // 类似 Ultimine-Rewind 的 RewindExecutor 在恢复前检查物品
            if (!isCreative) {
                if (!consumeItemForBlock(player, targetState)) {
                    continue; // 物品不足，跳过此方块
                }
            }

            targetState.setInWorld(level, pos, 3);

            // 创造模式：恢复方块实体 NBT 数据（类似 Ultimine-Rewind 的做法）
            // 生存模式不恢复 NBT，防止刷物品漏洞
            if (isCreative) {
                NBTTagCompound beData = record.blockEntityData();
                if (beData != null) {
                    TileEntity blockEntity = com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.getTileEntity(level, pos);
                    if (blockEntity != null) {
                        blockEntity.readFromNBT(beData);
                        blockEntity.markDirty();
                    }
                }
            }

            restoredCount++;
        }

        return restoredCount;
    }

    /**
     * 从玩家背包中消耗一个对应方块的物品（生存模式防刷物品）。
     * <p>
     * 类似 Ultimine-Rewind 的 RewindExecutor 消耗物品逻辑。
     *
     * @param player 操作的玩家
     * @param state  要放置的方块状态
     * @return true 如果找到了对应物品并成功消耗
     */
    private static boolean consumeItemForBlock(EntityPlayerMP player, BlockState state) {
        ItemStack required = new ItemStack(state.getBlock());
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(required)) {
            // 没有物品形式（如空气、火、结构方块等），跳过验证
            return true;
        }
        InventoryPlayer inventory = player.inventory;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) && stack.getItem() == required.getItem()) {
                com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.shrink(stack, 1);
                inventory.setInventorySlotContents(i, com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack) ? null : stack);
                inventory.markDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * 破坏方块，并将物品退还到链接储存（而非玩家背包或掉落物实体）。
     * <p>
     * 只破坏与记录中类型相同的方块（防止误破坏玩家后来放置的其他方块）。
     * <p>
     * 退还优先级：链接储存空间 → 玩家背包 → 原地掉落物。
     * <p>
     * <b>为什么不用 {@link net.minecraft.world.WorldServer#destroyBlock}：</b>
     * <ul>
     *   <li>{@code destroyBlock(pos, true, player)} 会以掉落物实体形式丢出物品</li>
     *   <li>取而代之：移除方块后优先尝试放入链接储存空间</li>
     *   <li>链接储存空间装满后回退到玩家背包</li>
     *   <li>背包也满时生成掉落物作为最终回退</li>
     * </ul>
     */
    private static int breakBlocks(EntityPlayerMP player, List<HistoryBlockRecord> blocks) {
        WorldServer level = player.getServerForPlayer();
        boolean isCreative = player.capabilities.isCreativeMode;
        int brokenCount = 0;

        for (HistoryBlockRecord record : blocks) {
            BlockPos pos = record.pos();
            if (!com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.isBlockLoaded(level, pos)) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, com.rtsbuilding.rtsbuilding.platform.math.EnumFacing.UP)) continue;

            BlockState currentState = BlockState.fromWorld(level, pos);
            if (currentState.getBlock() == Blocks.air) continue; // 方块已不存在

            BlockState expectedState = record.state();
            // 只破坏与记录中类型相同的方块（防止误破坏玩家后来放置的其他方块）
            if (currentState.getBlock() != expectedState.getBlock()) continue;

            // 移除方块（不生成掉落物实体）
            BlockState.defaultState(Blocks.air).setInWorld(level, pos, 3);

            // 生存模式：优先返还到链接储存空间，然后玩家背包，最后掉落物
            if (!isCreative) {
                ItemStack stack = new ItemStack(expectedState.getBlock());
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) {
                    boolean refunded = false;
                    RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
                    if (session != null) {
                        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
                        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
                        if (!handlers.isEmpty()) {
                            RtsTransferInserter.refundToLinked(handlers, player, stack);
                            refunded = true;
                        }
                    }
                    if (!refunded) {
                        // 没有链接储存时，回退到玩家背包
                        if (!player.inventory.addItemStackToInventory(stack)) {
                            com.rtsbuilding.rtsbuilding.platform.world.WorldCompat.spawnItem(level, pos, stack);
                        }
                    }
                }
            }

            brokenCount++;
        }

        // 撤回后强制刷新 RTS 页面，确保退还到链接储存后的数量正确显示
        if (!isCreative) {
            RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
            if (session != null) {
                ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
            }
        }

        return brokenCount;
    }

}
