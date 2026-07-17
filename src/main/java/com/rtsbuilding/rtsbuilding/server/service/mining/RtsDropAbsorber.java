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
import com.rtsbuilding.rtsbuilding.server.storage.port.RtsItemStorage;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 挖掘掉落物缓存入口与限量写回器。
 *
 * <p>RTS 连锁挖掘的主路径由 {@link RtsMiningDropCapture} 在掉落实体生成前精确接管，
 * 这里只把最终 {@link ItemStack} 放进有界内存缓存；AE/RS 与普通容器写入留到 Tick 末限量执行。
 * 基于世界实体的扫描入口仅保留给其他破坏流程与兼容回退，不能重新接入连锁挖掘热路径。</p>
 *
 * <p>无状态工具类，所有配置和缓冲状态存在于玩家会话中。核心方法：</p>
 * <ul>
 *   <li>{@link #enqueueCapturedDrops} — 接收尚未生成到世界的精确掉落</li>
 *   <li>{@link #drainDropBuffer} — 按 Tick 预算写入链接储存</li>
 *   <li>{@link #absorbNearbyMinedDrops} — 兼容流程使用的世界实体扫描回退</li>
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
            uniqueDrops.addAll(player.level().getEntitiesOfClass(
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
        List<RtsItemStorage> handlers = RtsLinkedStorageResolver.itemHandlersForInsert(linked);
        return new DropInsertContext(null, handlers);
    }

    private static void notifyStorageChanged(ServerPlayer player, DropInsertContext insertContext, boolean changed) {
        if (changed && insertContext.usesAggregate()) {
            RtsStorageTickService.INSTANCE.alert(player.getUUID());
        }
    }

    private record DropInsertContext(RtsAggregateStorage aggregate, List<RtsItemStorage> handlers) {
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
        return enqueueDrops(player, session, collectDrops(player, List.of(pos)));
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
        return enqueueDrops(player, session, collectDrops(player, positions));
    }

    /**
     * 把本 Tick 已经生成的世界掉落快速转移到有界内存缓存。
     *
     * <p>这里只做实体缩减与 {@link ItemStack} 入队，不解析 AE/RS 网络，也不等待任务存档 ACK。
     * 真正的外部储存写入由 Tick 末的限量 drain 完成，因此挖掘热路径不会被磁盘或网络储存拖住。</p>
     */
    private static boolean enqueueDrops(ServerPlayer player, RtsStorageSession session, List<ItemEntity> drops) {
        var buffer = session.miningDropBuffer;
        boolean changed = false;
        for (ItemEntity entity : drops) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) continue;
            int accepted = enqueueStack(buffer, entity.getItem());
            if (accepted <= 0) break;
            int remaining = entity.getItem().getCount() - accepted;
            if (remaining <= 0) entity.discard();
            else entity.setItem(entity.getItem().copyWithCount(remaining));
            changed = true;
        }
        finishEnqueue(player, buffer, changed);
        return changed;
    }

    /**
     * 接管尚未生成到世界的 NeoForge 方块掉落。
     * 已接受的实体从事件列表移除，缓存装不下的余量继续交给原版生成，不会吞物品。
     */
    static boolean enqueueCapturedDrops(
            ServerPlayer player, RtsStorageSession session, List<ItemEntity> drops) {
        var buffer = session.miningDropBuffer;
        boolean changed = false;
        Iterator<ItemEntity> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemEntity entity = iterator.next();
            if (entity == null || entity.getItem().isEmpty()) continue;
            int accepted = enqueueStack(buffer, entity.getItem());
            if (accepted <= 0) break;
            int remaining = entity.getItem().getCount() - accepted;
            if (remaining <= 0) iterator.remove();
            else entity.setItem(entity.getItem().copyWithCount(remaining));
            changed = true;
        }
        finishEnqueue(player, buffer, changed);
        return changed;
    }

    private static int enqueueStack(
            com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState buffer,
            ItemStack stack) {
        if (buffer.stacks.size()
                >= com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState.MAX_STACKS) {
            return 0;
        }
        int accepted = Math.min(buffer.remainingCapacity(), stack.getCount());
        if (accepted <= 0) return 0;
        buffer.stacks.addLast(stack.copyWithCount(accepted));
        buffer.bufferedItems += accepted;
        return accepted;
    }

    private static void finishEnqueue(
            ServerPlayer player,
            com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState buffer,
            boolean changed) {
        if (buffer.isFull() && !buffer.fullNoticeSent) {
            player.sendSystemMessage(Component.translatable("message.rtsbuilding.drop_buffer.full"), true);
            buffer.fullNoticeSent = true;
        }
        if (changed) {
            RtsEffectAccumulator.INSTANCE.markPersistence(
                    player.getUUID(), player.level().dimension());
        }
    }

    /**
     * 每 Tick 少量写入储存；三秒仍未完成时回退背包，再把余量合并掉落到玩家附近。
     * 返回消费的缓存栈数量，供统一任务预算累计。
     */
    public static int drainDropBuffer(ServerPlayer player, RtsStorageSession session,
            int maxStacks, long deadlineNanos) {
        var buffer = session.miningDropBuffer;
        if (buffer.isEmpty() || maxStacks <= 0) return 0;
        long gameTime = player.level().getGameTime();
        boolean fallbackEligible = buffer.fallbackEligible(gameTime, 60L);
        // 即使已经到达三秒，也先做最后一次真实写入；网络刚恢复时不应误回退到背包。
        DropInsertContext insertContext = createInsertContext(player, session);
        int processed = 0;
        boolean storageChanged = false;
        boolean fellBack = false;
        List<ItemStack> timedOutRemainders = new ArrayList<>();
        int stackLimit = fallbackEligible ? Math.min(maxStacks, 16) : maxStacks;
        while (processed < stackLimit && System.nanoTime() < deadlineNanos && !buffer.stacks.isEmpty()) {
            ItemStack original = buffer.stacks.removeFirst();
            ItemStack remainder = insertContext.store(original.copy());
            int stored = original.getCount() - remainder.getCount();
            storageChanged |= stored > 0;
            if (stored > 0) {
                // 只有真实外部写入进度才能清除堵塞计时。
                buffer.markStorageProgress();
            }
            if (stored <= 0 && fallbackEligible && !remainder.isEmpty()) {
                remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, remainder);
                if (!remainder.isEmpty()) {
                    mergeRemainder(timedOutRemainders, remainder);
                }
                buffer.bufferedItems -= original.getCount();
                fellBack = true;
            } else if (!remainder.isEmpty()) {
                buffer.stacks.addFirst(remainder);
                buffer.bufferedItems -= stored;
                if (stored <= 0) {
                    buffer.markStorageBlocked(gameTime);
                    break;
                }
            } else {
                buffer.bufferedItems -= original.getCount();
            }
            processed++;
        }
        for (ItemStack remainder : timedOutRemainders) {
            player.drop(remainder, false);
        }
        notifyStorageChanged(player, insertContext, storageChanged);
        if (storageChanged) {
            QuestService.runQuestDetect(player, session, false);
        }
        if (fellBack && buffer.shouldSendFallbackNotice(gameTime, 60L)) {
            RtsDeveloperMetrics.recordBufferFallback(player);
            player.sendSystemMessage(Component.translatable("message.rtsbuilding.drop_buffer.fallback"), false);
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
