package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;

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
    public static int executeUndo(ServerPlayer player, HistoryEntry entry) {
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
    private static int restoreBlocks(ServerPlayer player, List<HistoryBlockRecord> blocks, net.minecraft.core.Direction face) {
        ServerLevel level = player.level();
        boolean isCreative = player.isCreative();
        int restoredCount = 0;

        for (HistoryBlockRecord record : blocks) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;

            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir() && !currentState.canBeReplaced()) {
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

            level.setBlock(pos, targetState, Block.UPDATE_ALL | Block.UPDATE_CLIENTS);

            // 创造模式：恢复方块实体 NBT 数据（类似 Ultimine-Rewind 的做法）
            // 生存模式不恢复 NBT，防止刷物品漏洞
            if (isCreative) {
                CompoundTag beData = record.blockEntityData();
                if (beData != null) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntity.loadWithComponents(
                                com.rtsbuilding.rtsbuilding.common.persist.RtsNbtCompat.asValueInput(
                                        beData, level.registryAccess()));
                        blockEntity.setChanged();
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
    private static boolean consumeItemForBlock(ServerPlayer player, BlockState state) {
        ItemStack required = new ItemStack(state.getBlock().asItem());
        if (required.isEmpty()) {
            // 没有物品形式（如空气、火、结构方块等），跳过验证
            return true;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.is(required.getItem())) {
                stack.shrink(1);
                inventory.setItem(i, stack.isEmpty() ? ItemStack.EMPTY : stack);
                inventory.setChanged();
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
     * <b>为什么不用 {@link net.minecraft.server.level.ServerLevel#destroyBlock}：</b>
     * <ul>
     *   <li>{@code destroyBlock(pos, true, player)} 会以掉落物实体形式丢出物品</li>
     *   <li>取而代之：移除方块后优先尝试放入链接储存空间</li>
     *   <li>链接储存空间装满后回退到玩家背包</li>
     *   <li>背包也满时生成掉落物作为最终回退</li>
     * </ul>
     */
    private static int breakBlocks(ServerPlayer player, List<HistoryBlockRecord> blocks) {
        ServerLevel level = player.level();
        boolean isCreative = player.isCreative();
        int brokenCount = 0;

        for (HistoryBlockRecord record : blocks) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, net.minecraft.core.Direction.UP)) continue;

            BlockState currentState = level.getBlockState(pos);
            if (currentState.isAir()) continue; // 方块已不存在

            BlockState expectedState = record.state();
            // 只破坏与记录中类型相同的方块（防止误破坏玩家后来放置的其他方块）
            if (!currentState.is(expectedState.getBlock())) continue;

            // 移除方块（不生成掉落物实体）
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS);

            // 生存模式：优先返还到链接储存空间，然后玩家背包，最后掉落物
            if (!isCreative) {
                ItemStack stack = new ItemStack(expectedState.getBlock().asItem());
                if (!stack.isEmpty()) {
                    boolean refunded = false;
                    RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
                    if (session != null) {
                        List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
                        List<RtsItemStorage> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
                        if (!handlers.isEmpty()) {
                            RtsTransferInserter.refundToLinked(handlers, player, stack);
                            refunded = true;
                        }
                    }
                    if (!refunded) {
                        // 没有链接储存时，回退到玩家背包
                        if (!player.addItem(stack)) {
                            Block.popResource(level, pos, stack);
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
