package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPendingPlacementService;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.items.IItemHandler;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import com.rtsbuilding.rtsbuilding.server.task.BufferDrainTaskPayload;
import com.rtsbuilding.rtsbuilding.server.task.buffer.BufferDrainSliceResult;
import com.rtsbuilding.rtsbuilding.server.service.buffer.RtsBufferEscrowExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 挖掘掉落物吸收器，负责在方块被远程破坏后自动收集掉落物品。
 *
 * <p>当会话启用了 {@code autoStoreMinedDrops}（且进度/插件系统允许 {@code AUTO_STORE_MINED_DROPS} 功能）时，
 * 在破坏位置周围 1.25 格半径内扫描 {@link ItemEntity}，优先存入链接存储处理器，
 * 回退到玩家背包。若两个目标均已满，剩余物品留在世界中。
 *
 * <p>无状态工具类，所有配置存在于会话和进度/插件系统中。
 * 核心方法：
 * <ul>
 *   <li>{@link #absorbNearbyMinedDrops} — 执行扫描和吸收逻辑</li>
 *   <li>{@link #absorbMinedDropsImmediately} — 便捷包装，吸收后自动触发任务检测和恢复挂起放置</li>
 *   <li>{@link #absorbMinedDropsBatch} — 连锁/区域挖掘批量入口，复用同一个储存上下文</li>
 * </ul>
 */
public final class RtsDropAbsorber {

    /** 方块破坏位置周围搜索物品实体的半径。 */
    private RtsDropAbsorber() {
    }

    /**
     * 扫描开采位置周围 1.25 格半径内的 {@link ItemEntity}，将每个匹配的掉落物优先存入
     * 链接储存，再存入玩家背包。如果两个目标都已满，剩余物品留在世界中。
     *
     * @return 至少吸收了一个掉落物时返回 {@code true}
     */
    public static boolean absorbNearbyMinedDrops(ServerPlayer player, BlockPos center, RtsStorageSession session) {
        if (player == null || center == null || session == null) {
            return false;
        }
        DropInsertContext insertContext = createInsertContext(player, session);
        boolean changed = absorbNearbyMinedDrops(player, center, insertContext);
        notifyStorageChanged(player, insertContext, changed);
        return changed;
    }

    private static boolean absorbNearbyMinedDrops(ServerPlayer player, BlockPos center, DropInsertContext insertContext) {
        if (player == null || center == null || insertContext == null) {
            return false;
        }
        return absorbDrops(player, collectDrops(player, List.of(center)), insertContext);
    }

    private static List<ItemEntity> collectDrops(ServerPlayer player, List<BlockPos> positions) {
        Set<ItemEntity> uniqueDrops = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            AABB box = new AABB(pos).inflate(Config.dropScanRadius());
            uniqueDrops.addAll(player.serverLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    box,
                    entity -> entity != null && entity.isAlive() && !entity.getItem().isEmpty()));
        }
        return List.copyOf(uniqueDrops);
    }

    private static boolean absorbDrops(ServerPlayer player, List<ItemEntity> drops, DropInsertContext insertContext) {
        List<DropGroup> groups = groupDrops(drops);
        boolean changed = false;
        for (DropGroup group : groups) {
            int remaining = group.totalCount();
            int maxStackSize = Math.max(1, group.template().getMaxStackSize());
            while (remaining > 0) {
                int chunkSize = Math.min(remaining, maxStackSize);
                ItemStack chunk = group.template().copyWithCount(chunkSize);
                ItemStack remainder = insertContext.store(chunk);
                if (!remainder.isEmpty()) {
                    remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, remainder);
                }
                int absorbed = chunkSize - remainder.getCount();
                if (absorbed <= 0) {
                    break;
                }
                remaining -= absorbed;
                changed = true;
                if (!remainder.isEmpty()) {
                    break;
                }
            }
            consumeAbsorbedDrops(group.entities(), group.totalCount() - remaining);
        }
        return changed;
    }

    private static List<DropGroup> groupDrops(List<ItemEntity> drops) {
        List<DropGroup> groups = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem();
            if (!drop.isAlive() || stack.isEmpty()) {
                continue;
            }
            DropGroup matching = null;
            for (DropGroup group : groups) {
                if (ItemStack.isSameItemSameComponents(group.template(), stack)) {
                    matching = group;
                    break;
                }
            }
            if (matching == null) {
                matching = new DropGroup(stack.copyWithCount(1), new ArrayList<>(), 0);
                groups.add(matching);
            }
            matching.entities().add(drop);
            matching.addCount(stack.getCount());
        }
        return groups;
    }

    private static void consumeAbsorbedDrops(List<ItemEntity> entities, int absorbedCount) {
        int remaining = absorbedCount;
        for (ItemEntity entity : entities) {
            if (remaining <= 0) {
                return;
            }
            ItemStack stack = entity.getItem();
            int consumed = Math.min(remaining, stack.getCount());
            remaining -= consumed;
            if (consumed == stack.getCount()) {
                entity.discard();
            } else {
                entity.setItem(stack.copyWithCount(stack.getCount() - consumed));
            }
        }
    }

    /** 同类掉落在本 tick 内合并插入，但仍保留原实体以便精确回写未吸收部分。 */
    private static final class DropGroup {
        private final ItemStack template;
        private final List<ItemEntity> entities;
        private int totalCount;

        private DropGroup(ItemStack template, List<ItemEntity> entities, int totalCount) {
            this.template = template;
            this.entities = entities;
            this.totalCount = totalCount;
        }

        private ItemStack template() {
            return template;
        }

        private List<ItemEntity> entities() {
            return entities;
        }

        private int totalCount() {
            return totalCount;
        }

        private void addCount(int count) {
            totalCount += count;
        }
    }

    private static DropInsertContext createInsertContext(ServerPlayer player, RtsStorageSession session) {
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty()) {
            return new DropInsertContext(aggregate, List.of());
        }
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        List<IItemHandler> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        return new DropInsertContext(null, handlers);
    }

    private static void notifyStorageChanged(ServerPlayer player, DropInsertContext insertContext, boolean changed) {
        if (changed && insertContext.usesAggregate()) {
            RtsStorageTickService.INSTANCE.alert(player.getUUID());
        }
    }

    private record DropInsertContext(RtsAggregateStorage aggregate, List<IItemHandler> handlers) {
        ItemStack store(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (aggregate != null && !aggregate.isEmpty()) {
                return aggregate.insert(stack, false);
            }
            return handlers.isEmpty()
                    ? stack.copy()
                    : RtsTransferInserter.storeToLinkedOnly(handlers, stack);
        }

        boolean usesAggregate() {
            return aggregate != null && !aggregate.isEmpty();
        }
    }

    /**
     * 便捷包装方法：调用 {@link #absorbNearbyMinedDrops}，如果吸收了任何掉落物，
     * 则触发任务检测。
     */
    public static boolean absorbMinedDropsImmediately(ServerPlayer player, RtsStorageSession session, BlockPos pos) {
        if (player == null || session == null || pos == null) {
            return false;
        }
        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitBufferEscrow(player, List.of(pos));
    }

    /**
     * 批量吸收连锁/区域挖掘产生的掉落物。
     * <p>
     * 这里故意只解析一次储存上下文，避免每个方块都重新解析大量 linked storage。
     * 如果聚合储存缓存已经挂载，则优先走缓存的批量插入路径；否则回退到旧的 handler 列表。
     *
     * @return 本批次至少吸收了一个掉落物时返回 {@code true}
     */
    public static boolean absorbMinedDropsBatch(ServerPlayer player, RtsStorageSession session, List<BlockPos> positions) {
        if (player == null || session == null || positions == null || positions.isEmpty()) {
            return false;
        }
        return com.rtsbuilding.rtsbuilding.server.task.RtsTaskEngine.INSTANCE
                .submitBufferEscrow(player, positions);
    }

    /**
     * 只建立世界掉落的 durable claim 意图，不修改实体，也不访问 AE/RS。
     *
     * <p>中央 Task Engine 必须先持久化返回的 SOURCE_PREPARED 状态并收到 ACK，随后才可调用
     * {@link #claimPreparedEscrowSlice}。为保证来源校验精确，达到容量边界时保守地留下整个实体，
     * 不在 PREPARED 阶段切分它。</p>
     */
    public static BufferDrainTaskPayload prepareEscrowTask(
            ServerPlayer player, List<BlockPos> positions, UUID escrowId) {
        return RtsBufferEscrowExecutor.prepareEscrowTask(player, positions, escrowId);
    }

    /**
     * 在 SOURCE_PREPARED revision 已持久化 ACK 后，按预算把来源实体转入 escrow。
     *
     * <p>实体 UUID、完整 ItemStack 组件与数量必须全部匹配；任何偏差都进入 RECOVERY_REQUIRED，
     * 绝不凭快照复制出一份新物品。返回状态必须立即交回 TaskStore。</p>
     */
    public static BufferDrainSliceResult claimPreparedEscrowSlice(
            ServerPlayer player,
            BufferDrainTaskPayload payload,
            int maxStacks,
            long deadlineNanos) {
        return RtsBufferEscrowExecutor.claimPreparedEscrowSlice(
                player, payload, maxStacks, deadlineNanos);
    }

    /**
     * 执行已 ACK 的 DRAIN_RESERVED 批次。
     *
     * <p>Session 在这里仅用于本 tick 解析链接端点；它不保存 escrow、游标或生命周期。
     * 外部 handler 无事务能力，因此调用后返回 DRAIN_APPLIED 写后记录，中央层必须立刻持久化；
     * 在该 revision ACK 前不得调用 {@link BufferEscrowState#confirmAppliedAfterAck()}。</p>
     */
    public static BufferDrainSliceResult executeReservedDrainSlice(
            ServerPlayer player,
            RtsStorageSession transientSession,
            BufferDrainTaskPayload payload,
            int maxStacks,
            long deadlineNanos) {
        return RtsBufferEscrowExecutor.executeReservedDrainSlice(
                player, transientSession, payload, maxStacks, deadlineNanos);
    }

    /**
     * 一次性读取旧 Session 缓存，形成已由 escrow 持有的迁移快照。
     *
     * <p>本方法不清空旧缓存。中央层必须使用已经冻结的 handoff 身份提交并 ACK 任务，再通过
     * DROP_BUFFER revision ticket 清除旧 Session shadow。</p>
     */
    public static BufferDrainTaskPayload snapshotLegacyBuffer(
            ServerPlayer player, RtsStorageSession session,
            com.rtsbuilding.rtsbuilding.server.task.buffer.LegacyBufferHandoffState handoff) {
        return RtsBufferEscrowExecutor.snapshotLegacyBuffer(player, session, handoff);
    }

    /**
     * 快速把世界掉落转移到有界缓存；这里只复制/缩减实体，不触碰 AE/RS 网络。
     */
    private static boolean enqueueDrops(ServerPlayer player, RtsStorageSession session, List<ItemEntity> drops) {
        var buffer = session.miningDropBuffer;
        boolean changed = false;
        for (ItemEntity entity : drops) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) continue;
            if (buffer.stacks.size() >= com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
                break;
            }
            int accepted = Math.min(buffer.remainingCapacity(), entity.getItem().getCount());
            if (accepted <= 0) break;
            buffer.stacks.addLast(entity.getItem().copyWithCount(accepted));
            buffer.bufferedItems += accepted;
            if (buffer.firstQueuedGameTime < 0L) {
                buffer.firstQueuedGameTime = player.serverLevel().getGameTime();
            }
            int remaining = entity.getItem().getCount() - accepted;
            if (remaining <= 0) entity.discard();
            else entity.setItem(entity.getItem().copyWithCount(remaining));
            changed = true;
        }
        if (buffer.isFull() && !buffer.fullNoticeSent) {
            player.displayClientMessage(Component.translatable("message.rtsbuilding.drop_buffer.full"), true);
            buffer.fullNoticeSent = true;
        }
        if (changed) {
            RtsEffectAccumulator.INSTANCE.markPersistence(
                    player.getUUID(), player.level().dimension());
        }
        return changed;
    }

    /**
     * 每 Tick 少量写入储存；三秒仍未完成时回退背包，再把余量合并掉落到玩家附近。
     * 返回消费的缓存栈数量，供统一任务预算累计。
     */
    public static int drainDropBuffer(ServerPlayer player, RtsStorageSession session,
            int maxStacks, long deadlineNanos) {
        var buffer = session.miningDropBuffer;
        if (buffer.isEmpty() || maxStacks <= 0) return 0;
        boolean timeout = buffer.firstQueuedGameTime >= 0L
                && player.serverLevel().getGameTime() - buffer.firstQueuedGameTime >= 60L;
        DropInsertContext insertContext = timeout ? null : createInsertContext(player, session);
        int processed = 0;
        boolean storageChanged = false;
        List<ItemStack> timedOutRemainders = new ArrayList<>();
        int stackLimit = timeout ? Math.min(maxStacks, 16) : maxStacks;
        while (processed < stackLimit && System.nanoTime() < deadlineNanos && !buffer.stacks.isEmpty()) {
            ItemStack original = buffer.stacks.removeFirst();
            ItemStack remainder = timeout ? original.copy() : insertContext.store(original.copy());
            int stored = original.getCount() - remainder.getCount();
            storageChanged |= stored > 0;
            if (timeout && !remainder.isEmpty()) {
                remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, remainder);
                if (!remainder.isEmpty()) {
                    mergeRemainder(timedOutRemainders, remainder);
                }
            } else if (!timeout && !remainder.isEmpty()) {
                buffer.stacks.addFirst(remainder);
            }
            buffer.bufferedItems -= timeout ? original.getCount() : stored;
            processed++;
            if (!timeout && stored <= 0) break;
        }
        for (ItemStack remainder : timedOutRemainders) {
            player.drop(remainder, false);
        }
        if (insertContext != null) {
            notifyStorageChanged(player, insertContext, storageChanged);
        }
        if (storageChanged) {
            QuestService.runQuestDetect(player, session, false);
        }
        if (timeout && buffer.isEmpty()) {
            RtsDeveloperMetrics.recordBufferFallback(player);
            player.displayClientMessage(Component.translatable("message.rtsbuilding.drop_buffer.fallback"), false);
        }
        buffer.clearTimingWhenEmpty();
        if (processed > 0) {
            RtsEffectAccumulator.INSTANCE.markPersistence(
                    player.getUUID(), player.level().dimension());
        }
        return processed;
    }

    private static void mergeRemainder(List<ItemStack> merged, ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : merged) {
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved <= 0) continue;
            existing.grow(moved);
            remaining.shrink(moved);
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty()) {
            int count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            merged.add(remaining.copyWithCount(count));
            remaining.shrink(count);
        }
    }

    /** 退出时同步回退，确保未持久化缓存不会吞掉物品。 */
    public static void flushDropBufferToPlayer(ServerPlayer player, RtsStorageSession session) {
        var buffer = session.miningDropBuffer;
        while (!buffer.stacks.isEmpty()) {
            ItemStack remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, buffer.stacks.removeFirst());
            if (!remainder.isEmpty()) player.drop(remainder, false);
        }
        buffer.clearTimingWhenEmpty();
    }
}
