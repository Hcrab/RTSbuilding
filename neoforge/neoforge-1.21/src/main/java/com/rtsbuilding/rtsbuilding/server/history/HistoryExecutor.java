package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.server.RtsServer;
import com.rtsbuilding.rtsbuilding.server.data.PlacedBlockTrackerData;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
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
 *   <li>生存模式不恢复 NBT（防刷物品漏洞），但破坏时退还方块实体内容物（防蒸发）</li>
 *   <li>跳过已被占用的位置（部分恢复）</li>
 *   <li>破坏时只删除与记录类型相同的方块（防止误破坏）</li>
 *   <li>单次撤销受预算限制，超大批次分多次完成（防单 tick 卡顿）</li>
 * </ul>
 */
public final class HistoryExecutor {

    private HistoryExecutor() {
    }

    /**
     * 单次撤销的执行结果。
     *
     * @param executed 实际成功处理的方块数量
     * @param pending  因单次预算限制尚未处理的方块记录（保持原始顺序，重新入栈后下次撤销继续）
     */
    public record UndoOutcome(int executed, List<HistoryBlockRecord> pending) {
    }

    /**
     * 执行撤回操作。
     * <p>
     * 放置批次→破坏每个方块；破坏批次→恢复每个方块。
     *
     * @param player 操作的玩家
     * @param entry  要撤回的历史记录
     * @param budget 单次最多处理的方块数量（超出部分返回 pending，由调用方重新入栈）
     * @return 执行结果（成功数 + 未处理部分）
     */
    public static UndoOutcome executeUndo(ServerPlayer player, HistoryEntry entry, int budget) {
        if (entry.isDestructive()) {
            // 破坏批次→撤回=重新放置方块
            return restoreBlocks(player, entry.getBlocks(), budget);
        } else {
            // 放置批次→撤回=破坏方块
            return breakBlocks(player, entry.getBlocks(), budget);
        }
    }

    // ======================================================================
    //  内部执行逻辑
    // ======================================================================

    /**
     * 恢复方块（重新放置）。
     * <p>
     * 仅在目标位置为空气或可替换方块时才放置。
     * 跳过已被占用的位置（永久跳过：下次重试无意义）。
     * 生存模式消耗背包/链接存储中的方块物品（防刷物品）。
     * 创造模式额外恢复方块实体 NBT 数据。
     */
    private static UndoOutcome restoreBlocks(ServerPlayer player, List<HistoryBlockRecord> blocks, int budget) {
        ServerLevel level = player.serverLevel();
        boolean isCreative = player.isCreative();
        int restoredCount = 0;
        int processed = 0;
        List<HistoryBlockRecord> pending = new ArrayList<>();

        for (HistoryBlockRecord record : blocks) {
            if (processed >= budget) {
                pending.add(record);
                continue;
            }
            processed++;

            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;

            BlockState currentState = level.getBlockState(pos);
            if (!currentState.isAir() && !currentState.canBeReplaced()) {
                continue; // 位置已被占用，跳过（不重试）
            }

            BlockState targetState = record.state();

            // 生存模式：验证并消耗物品（防止刷物品漏洞）
            if (!isCreative) {
                if (!consumeItemForBlock(player, targetState)) {
                    continue; // 背包与链接存储均无对应物品，跳过（不重试，避免永久挡栈）
                }
            }

            level.setBlock(pos, targetState, Block.UPDATE_ALL | Block.UPDATE_CLIENTS);

            // 创造模式：恢复方块实体 NBT 数据
            // 生存模式不恢复 NBT，防止刷物品漏洞
            if (isCreative) {
                CompoundTag beData = record.blockEntityData();
                if (beData != null) {
                    BlockEntity blockEntity = level.getBlockEntity(pos);
                    if (blockEntity != null) {
                        blockEntity.loadWithComponents(beData, level.registryAccess());
                        blockEntity.setChanged();
                    }
                }
            }

            restoredCount++;
        }

        return new UndoOutcome(restoredCount, pending);
    }

    /**
     * 从玩家背包中消耗一个对应方块的物品（生存模式防刷物品）。
     * <p>
     * 背包找不到时回退到链接存储空间扣除——RTS 挖掘的掉落物默认存入链接存储，
     * 若只查背包会导致"方块明明在链接存储里却无法恢复"的误判。
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

        // 背包没有时，从链接存储扣除（RTS 挖掘掉落物默认存入链接存储）
        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        if (session != null) {
            List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
            for (IItemHandler handler : handlers) {
                for (int s = 0; s < handler.getSlots(); s++) {
                    ItemStack stack = handler.getStackInSlot(s);
                    if (!stack.isEmpty() && stack.is(required.getItem())) {
                        ItemStack extracted = handler.extractItem(s, 1, false);
                        if (!extracted.isEmpty()) {
                            RtsServer.get().serviceOp().markDirty(player, session);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 破坏方块，并将物品退还到链接储存（而非玩家背包或掉落物实体）。
     * <p>
     * 只破坏与记录中类型相同的方块（防止误破坏玩家后来放置的其他方块）。
     * 非本模组放置标记的位置要求完整状态一致才破坏（防止破坏他人同类型方块）。
     * 破坏前将方块实体内容物取出退还（防止箱子/潜影盒内容蒸发）。
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
    private static UndoOutcome breakBlocks(ServerPlayer player, List<HistoryBlockRecord> blocks, int budget) {
        ServerLevel level = player.serverLevel();
        boolean isCreative = player.isCreative();
        int brokenCount = 0;
        int processed = 0;
        List<HistoryBlockRecord> pending = new ArrayList<>();
        PlacedBlockTrackerData tracker = PlacedBlockTrackerData.get(level);

        for (HistoryBlockRecord record : blocks) {
            if (processed >= budget) {
                pending.add(record);
                continue;
            }
            processed++;

            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;

            BlockState currentState = level.getBlockState(pos);
            if (currentState.isAir()) continue; // 方块已不存在，记录作废

            BlockState expectedState = record.state();
            // 只破坏与记录中类型相同的方块（防止误破坏玩家后来放置的其他方块）
            if (!currentState.is(expectedState.getBlock())) continue;

            // 安全增强：位置未被本模组标记为已放置时，要求完整状态一致才破坏。
            // 标记过的位置允许状态差异（如玩家旋转过方块朝向、门被打开），
            // 未标记的位置要求属性完全一致，防止误破坏其他来源的同类型方块。
            boolean placedByRts = tracker.isPlaced(pos);
            if (!placedByRts && !currentState.equals(expectedState)) continue;

            // 移除方块前，将方块实体内容物取出（退还链接存储→背包→掉落物），
            // 避免箱子/潜影盒等容器内容物随方块一起蒸发
            if (!isCreative) {
                ejectBlockEntityContents(player, level, pos);
            }

            // 移除方块（不生成掉落物实体）
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS);

            // 清理放置标记，防止残留导致后续该位置的破坏判定被误认为"已放置"
            if (placedByRts) {
                tracker.clear(pos);
            }

            // 生存模式：优先返还到链接储存空间，然后玩家背包，最后掉落物
            if (!isCreative) {
                ItemStack stack = new ItemStack(expectedState.getBlock().asItem());
                if (!stack.isEmpty()) {
                    boolean refunded = false;
                    RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
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
            RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
            if (session != null) {
                RtsServer.get().serviceOp().afterModification(player, session);
            }
        }

        return new UndoOutcome(brokenCount, pending);
    }

    /**
     * 取出方块实体中的全部内容物并退还（链接存储 → 背包 → 掉落物）。
     * <p>
     * 在移除方块前调用，防止箱子、潜影盒等容器方块的内容物随撤销一起蒸发。
     * 创造模式不调用（与原版行为一致：创造模式破坏容器不掉落内容物）。
     */
    private static void ejectBlockEntityContents(ServerPlayer player, ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof Container container)) return;

        RtsStorageSession session = RtsServer.get().session().getIfPresent(player);
        List<IItemHandler> handlers = null;
        if (session != null) {
            List<LinkedHandler> activeLinked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            handlers = RtsLinkedStorageResolver.itemHandlersForInsert(activeLinked);
        }

        boolean anyChange = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack content = container.getItem(i);
            if (content.isEmpty()) continue;
            container.setItem(i, ItemStack.EMPTY);
            anyChange = true;

            ItemStack remain = content;
            if (handlers != null && !handlers.isEmpty()) {
                remain = RtsTransferInserter.storeToLinkedOnlyPreferExisting(handlers, remain);
            }
            if (!remain.isEmpty()) {
                remain = RtsTransferInserter.moveToPlayerInventoryOnly(player, remain);
            }
            if (!remain.isEmpty()) {
                Block.popResource(level, pos, remain);
            }
        }

        if (anyChange) {
            container.setChanged();
            if (session != null) {
                RtsServer.get().serviceOp().afterModification(player, session);
            }
        }
    }
}
