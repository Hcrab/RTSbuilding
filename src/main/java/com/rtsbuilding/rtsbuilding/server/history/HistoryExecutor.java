package com.rtsbuilding.rtsbuilding.server.history;

import com.rtsbuilding.rtsbuilding.server.protection.RtsClaimProtectionService;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsPlacementExtractor;
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
import net.minecraftforge.items.IItemHandler;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务端 Ctrl+Z 执行器。
 *
 * <p>创造模式恢复世界快照；生存模式执行资源守恒的反向操作。
 * 每个成功位置都会显式返回给历史管理器，跳过的位置仍留在原条目中。</p>
 */
public final class HistoryExecutor {
    private HistoryExecutor() {
    }

    public static HistoryExecutionResult executeUndo(ServerPlayer player, HistoryEntry entry) {
        Set<BlockPos> completed = switch (entry.getOperation()) {
            case CREATIVE_BREAK -> restoreBrokenBlocks(player, entry.getBlocks(), true, -1);
            case SURVIVAL_BREAK -> restoreBrokenBlocks(
                    player, entry.getBlocks(), false, entry.getSourceSlot());
            case CREATIVE_PLACEMENT -> restoreCreativePlacement(player, entry.getBlocks());
            case SURVIVAL_PLACEMENT -> breakSurvivalPlacement(player, entry.getBlocks());
        };
        return new HistoryExecutionResult(completed.size(), completed);
    }

    /** Ctrl+Y 第一阶段只重做创造模式条目；生存条目不会产生任何世界或物品副作用。 */
    public static HistoryExecutionResult executeRedo(ServerPlayer player, HistoryEntry entry) {
        if (!entry.getOperation().creative()) {
            return new HistoryExecutionResult(0, Set.of());
        }
        Set<BlockPos> completed = applyCreativeAfterSnapshot(player, entry);
        return new HistoryExecutionResult(completed.size(), completed);
    }

    /** 恢复破坏前状态；生存模式必须先成功提取对应方块物品。 */
    private static Set<BlockPos> restoreBrokenBlocks(
            ServerPlayer player, List<HistoryBlockRecord> records, boolean creative, int sourceSlot) {
        ServerLevel level = player.getLevel();
        Set<BlockPos> completed = new LinkedHashSet<>();
        for (HistoryBlockRecord record : records) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            if (!level.getBlockState(pos).equals(record.afterState())) continue;

            ItemStack consumed = ItemStack.EMPTY;
            if (!creative) {
                consumed = consumeBlockItem(player, record.state(), sourceSlot);
                if (consumed.isEmpty()) continue;
            }
            if (!level.setBlock(pos, record.state(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS)) {
                if (!creative) refundItem(player, consumed, pos);
                continue;
            }
            if (creative) restoreBlockEntity(level, pos, record.blockEntityData());
            completed.add(pos);
        }
        if (!creative) refreshStorage(player);
        return completed;
    }

    /** 创造建造撤回：恢复建造前的空气/方块状态及完整方块实体 NBT。 */
    private static Set<BlockPos> restoreCreativePlacement(
            ServerPlayer player, List<HistoryBlockRecord> records) {
        ServerLevel level = player.getLevel();
        Set<BlockPos> completed = new LinkedHashSet<>();
        for (HistoryBlockRecord record : records) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, net.minecraft.core.Direction.UP)) continue;
            if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            // 撤回建造沿用操作后 BlockState 校验；方块实体落地后可能自行规范化 NBT，
            // 不能因此让正常的箱子、机器等永久失去撤回能力。
            if (!level.getBlockState(pos).equals(record.afterState())) continue;
            BlockState current = level.getBlockState(pos);
            if (!current.equals(record.state())
                    && !level.setBlock(pos, record.state(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS)) continue;
            restoreBlockEntity(level, pos, record.blockEntityData());
            completed.add(pos);
        }
        return completed;
    }

    /**
     * 恢复创造操作的“操作后”快照。执行前必须仍匹配 Ctrl+Z 恢复出的“操作前”快照，
     * 避免玩家在撤回后手动改过方块实体内容时被 Ctrl+Y 静默覆盖。
     */
    private static Set<BlockPos> applyCreativeAfterSnapshot(
            ServerPlayer player, HistoryEntry entry) {
        ServerLevel level = player.getLevel();
        Set<BlockPos> completed = new LinkedHashSet<>();
        for (HistoryBlockRecord record : entry.getBlocks()) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!matchesSnapshot(level, pos, record.state(), record.blockEntityData())) continue;

            if (entry.getOperation() == HistoryOperation.CREATIVE_BREAK) {
                if (!RtsClaimProtectionService.canBreakBlock(
                        player, pos, net.minecraft.core.Direction.UP)) continue;
            } else {
                if (!record.state().isAir() && !RtsClaimProtectionService.canBreakBlock(
                        player, pos, net.minecraft.core.Direction.UP)) continue;
                if (!RtsClaimProtectionService.canPlaceBlock(player, pos)) continue;
            }

            BlockState current = level.getBlockState(pos);
            if (!current.equals(record.afterState())
                    && !level.setBlock(pos, record.afterState(), Block.UPDATE_ALL | Block.UPDATE_CLIENTS)) continue;
            restoreBlockEntity(level, pos, record.afterBlockEntityData());
            completed.add(pos);
        }
        return completed;
    }

    /** NBT 为空表示该端没有方块实体快照；有快照时则要求完整一致。 */
    private static boolean matchesSnapshot(
            ServerLevel level, BlockPos pos, BlockState expectedState, CompoundTag expectedBlockEntityData) {
        if (!level.getBlockState(pos).equals(expectedState)) return false;
        if (expectedBlockEntityData == null) return true;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return false;
        CompoundTag current = blockEntity.saveWithFullMetadata();
        return expectedBlockEntityData.equals(current);
    }

    /** 生存建造撤回：只移除仍与本批结果完全一致的方块并返还材料。 */
    private static Set<BlockPos> breakSurvivalPlacement(
            ServerPlayer player, List<HistoryBlockRecord> records) {
        ServerLevel level = player.getLevel();
        Set<BlockPos> completed = new LinkedHashSet<>();
        for (HistoryBlockRecord record : records) {
            BlockPos pos = record.pos();
            if (!level.isLoaded(pos)) continue;
            if (!RtsClaimProtectionService.canBreakBlock(player, pos, net.minecraft.core.Direction.UP)) continue;
            BlockState current = level.getBlockState(pos);
            if (!current.equals(record.afterState())) continue;
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL | Block.UPDATE_CLIENTS)) continue;
            refundItem(player, new ItemStack(record.afterState().getBlock().asItem()), pos);
            completed.add(pos);
        }
        refreshStorage(player);
        return completed;
    }

    /** 资源来源严格限定为 linked storage 和原操作记录的快捷栏槽位。 */
    private static ItemStack consumeBlockItem(
            ServerPlayer player, BlockState state, int sourceSlot) {
        ItemStack required = new ItemStack(state.getBlock().asItem());
        if (required.isEmpty()) return ItemStack.EMPTY;

        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            List<IItemHandler> handlers = linkedItemHandlers(player, session);
            ItemStack extracted = RtsPlacementExtractor.extractSelectedFromLinkedCached(
                    player, handlers, required.getItem(), ItemStack.EMPTY);
            if (!extracted.isEmpty()) return extracted;
        }

        if (sourceSlot < 0 || sourceSlot > 8) return ItemStack.EMPTY;
        Inventory inventory = player.getInventory();
        ItemStack source = inventory.getItem(sourceSlot);
        if (source.isEmpty() || !source.is(required.getItem())) return ItemStack.EMPTY;
        ItemStack extracted = source.copy();
        extracted.setCount(1);
        source.shrink(1);
        inventory.setItem(sourceSlot, source.isEmpty() ? ItemStack.EMPTY : source);
        inventory.setChanged();
        return extracted;
    }

    /** 真实提取栈的明确回退：linked storage → 玩家背包 → 原地掉落。 */
    private static void refundItem(ServerPlayer player, ItemStack stack, BlockPos pos) {
        if (stack == null || stack.isEmpty()) return;
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) {
            List<IItemHandler> handlers = linkedItemHandlers(player, session);
            if (!handlers.isEmpty()) {
                RtsTransferInserter.refundToLinked(handlers, player, stack);
                return;
            }
        }
        if (!player.addItem(stack)) Block.popResource(player.getLevel(), pos, stack);
    }

    private static List<IItemHandler> linkedItemHandlers(
            ServerPlayer player, RtsStorageSession session) {
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        return RtsLinkedStorageResolver.itemHandlersForInsert(linked);
    }

    private static void restoreBlockEntity(
            ServerLevel level, BlockPos pos, CompoundTag blockEntityData) {
        if (blockEntityData == null) return;
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) return;
        blockEntity.load(blockEntityData);
        blockEntity.setChanged();
    }

    private static void refreshStorage(ServerPlayer player) {
        RtsStorageSession session = ServiceRegistry.getInstance().session().getIfPresent(player);
        if (session != null) ServiceRegistry.getInstance().serviceOp().afterModification(player, session);
    }
}
