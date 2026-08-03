package com.rtsbuilding.rtsbuilding.server.service.mining;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.server.service.QuestService;
import com.rtsbuilding.rtsbuilding.server.service.RtsBoundedItemEntityQuery;
import com.rtsbuilding.rtsbuilding.server.service.RtsDeveloperMetrics;
import com.rtsbuilding.rtsbuilding.server.service.RtsStorageTickService;
import com.rtsbuilding.rtsbuilding.server.service.transfer.RtsTransferInserter;
import com.rtsbuilding.rtsbuilding.server.storage.cache.RtsAggregateStorage;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedHandler;
import com.rtsbuilding.rtsbuilding.server.storage.model.LinkedStorageRef;
import com.rtsbuilding.rtsbuilding.server.storage.resolver.RtsLinkedStorageResolver;
import com.rtsbuilding.rtsbuilding.server.storage.session.RtsStorageSession;
import com.rtsbuilding.rtsbuilding.server.storage.state.RtsMiningDropBufferState;
import com.rtsbuilding.rtsbuilding.server.task.RtsEffectAccumulator;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 采掘掉落的所有权转换边界。每次只从原实体/事件堆栈扣除已被存储或缓冲接受的数量，
 * remainder 始终留给原系统或显式回退，因此任何失败路径都不吞物品。
 */
public final class RtsDropAbsorber {
    private RtsDropAbsorber() { }

    public static boolean absorbNearbyMinedDrops(EntityPlayerMP player, BlockPos center,
            RtsStorageSession session) {
        if (player == null || center == null || session == null) return false;
        DropInsertContext context = createInsertContext(player, session);
        boolean changed = absorbEntities(player, collectDrops(player, Collections.singletonList(center)), context);
        notifyStorageChanged(player, context, changed);
        return changed;
    }

    private static List<EntityItem> collectDrops(EntityPlayerMP player, List<BlockPos> positions) {
        Set<EntityItem> unique = Collections.newSetFromMap(new IdentityHashMap<EntityItem, Boolean>());
        double radius = Config.dropScanRadius();
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            int queryLimit = RtsMiningDropBufferState.MAX_STACKS - unique.size();
            if (queryLimit <= 0) break;
            AxisAlignedBB box = new AxisAlignedBB(pos).grow(radius);
            RtsBoundedItemEntityQuery.Result query = RtsBoundedItemEntityQuery.query(
                    player.getServerWorld(), box, queryLimit,
                    entity -> entity != null && !entity.isDead && !entity.getItem().isEmpty()
                            && !unique.contains(entity));
            unique.addAll(query.entities());
            if (query.saturated()) break;
        }
        return new ArrayList<EntityItem>(unique);
    }

    private static boolean absorbEntities(EntityPlayerMP player, List<EntityItem> entities,
            DropInsertContext context) {
        boolean changed = false;
        for (DropGroup group : groupDrops(entities)) {
            int remaining = group.totalCount;
            int max = Math.max(1, group.template.getMaxStackSize());
            while (remaining > 0) {
                int amount = Math.min(remaining, max);
                ItemStack offered = copyWithCount(group.template, amount);
                ItemStack remainder = context.store(offered);
                if (!remainder.isEmpty()) remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, remainder);
                int accepted = amount - count(remainder);
                if (accepted <= 0) break;
                remaining -= accepted;
                changed = true;
                if (!remainder.isEmpty()) break;
            }
            consumeEntities(group.entities, group.totalCount - remaining);
        }
        return changed;
    }

    private static List<DropGroup> groupDrops(List<EntityItem> entities) {
        List<DropGroup> groups = new ArrayList<DropGroup>();
        for (EntityItem entity : entities) {
            if (entity == null || entity.isDead || entity.getItem().isEmpty()) continue;
            ItemStack stack = entity.getItem();
            DropGroup target = null;
            for (DropGroup group : groups) {
                if (sameExact(group.template, stack)) { target = group; break; }
            }
            if (target == null) {
                target = new DropGroup(copyWithCount(stack, 1));
                groups.add(target);
            }
            target.entities.add(entity);
            target.totalCount += stack.getCount();
        }
        return groups;
    }

    private static void consumeEntities(List<EntityItem> entities, int accepted) {
        int remaining = accepted;
        for (EntityItem entity : entities) {
            if (remaining <= 0) break;
            ItemStack stack = entity.getItem();
            int consumed = Math.min(remaining, stack.getCount());
            remaining -= consumed;
            if (consumed == stack.getCount()) entity.setDead();
            else entity.setItem(copyWithCount(stack, stack.getCount() - consumed));
        }
    }

    public static boolean absorbMinedDropsImmediately(EntityPlayerMP player,
            RtsStorageSession session, BlockPos pos) {
        return player != null && session != null && pos != null
                && enqueueEntities(player, session, collectDrops(player, Collections.singletonList(pos)));
    }

    public static boolean absorbMinedDropsBatch(EntityPlayerMP player, RtsStorageSession session,
            List<BlockPos> positions) {
        return player != null && session != null && positions != null && !positions.isEmpty()
                && enqueueEntities(player, session, collectDrops(player, positions));
    }

    private static boolean enqueueEntities(EntityPlayerMP player, RtsStorageSession session,
            List<EntityItem> entities) {
        boolean changed = false;
        for (EntityItem entity : entities) {
            if (entity == null || entity.isDead || entity.getItem().isEmpty()) continue;
            int accepted = enqueueStack(session.miningDropBuffer, entity.getItem());
            if (accepted <= 0) break;
            int remaining = entity.getItem().getCount() - accepted;
            if (remaining <= 0) entity.setDead();
            else entity.setItem(copyWithCount(entity.getItem(), remaining));
            changed = true;
        }
        finishEnqueue(player, session.miningDropBuffer, changed);
        return changed;
    }

    /**
     * 1.12 HarvestDropsEvent 直接提供 ItemStack 列表。全部接收才 remove；部分接收则只缩减原对象，
     * 这样其他模组后续仍能看到精确 metadata/NBT 的 remainder。
     */
    static boolean enqueueCapturedDrops(EntityPlayerMP player, RtsStorageSession session,
            List<ItemStack> drops) {
        boolean changed = false;
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack == null || stack.isEmpty()) continue;
            int accepted = enqueueStack(session.miningDropBuffer, stack);
            if (accepted <= 0) break;
            if (accepted >= stack.getCount()) iterator.remove();
            else stack.shrink(accepted);
            changed = true;
        }
        finishEnqueue(player, session.miningDropBuffer, changed);
        return changed;
    }

    private static int enqueueStack(RtsMiningDropBufferState buffer, ItemStack stack) {
        return stack == null || stack.isEmpty() ? 0 : buffer.enqueueMerged(stack, stack.getCount());
    }

    private static void finishEnqueue(EntityPlayerMP player, RtsMiningDropBufferState buffer, boolean changed) {
        long tick = player.getServerWorld().getTotalWorldTime();
        buffer.updateFullState(tick);
        if (changed) RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
    }

    public static int drainDropBuffer(EntityPlayerMP player, RtsStorageSession session,
            int maxStacks, long deadlineNanos) {
        RtsMiningDropBufferState buffer = session.miningDropBuffer;
        if (buffer.isEmpty() || maxStacks <= 0) return 0;
        long tick = player.getServerWorld().getTotalWorldTime();
        boolean fallback = buffer.fallbackEligible(tick, 60L);
        DropInsertContext context = createInsertContext(player, session);
        int processed = 0;
        boolean storageChanged = false;
        boolean fellBack = false;
        List<ItemStack> worldRemainders = new ArrayList<ItemStack>();
        int limit = fallback ? Math.min(maxStacks, 16) : maxStacks;
        while (processed < limit && System.nanoTime() < deadlineNanos && !buffer.stacks.isEmpty()) {
            ItemStack original = buffer.stacks.removeFirst();
            ItemStack remainder = context.store(original.copy());
            int stored = original.getCount() - count(remainder);
            storageChanged |= stored > 0;
            if (stored > 0) buffer.markStorageProgress();
            if (stored <= 0 && fallback && !remainder.isEmpty()) {
                remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, remainder);
                if (!remainder.isEmpty()) mergeRemainder(worldRemainders, remainder);
                buffer.bufferedItems -= original.getCount();
                fellBack = true;
            } else if (!remainder.isEmpty()) {
                buffer.stacks.addFirst(remainder);
                buffer.bufferedItems -= stored;
                if (stored <= 0) { buffer.markStorageBlocked(tick); break; }
            } else {
                buffer.bufferedItems -= original.getCount();
            }
            processed++;
        }
        for (ItemStack stack : worldRemainders) player.dropItem(stack, false);
        notifyStorageChanged(player, context, storageChanged);
        if (storageChanged) QuestService.runQuestDetect(player, session, false);
        if (fellBack && buffer.shouldNotifyFallback()) {
            RtsDeveloperMetrics.recordBufferFallback(player);
            player.sendStatusMessage(new TextComponentTranslation("message.rtsbuilding.drop_buffer.fallback"), true);
        }
        buffer.updateFullState(tick);
        if (buffer.shouldNotifyFull(tick, 20L)) {
            player.sendStatusMessage(new TextComponentTranslation("message.rtsbuilding.drop_buffer.full"), true);
            buffer.fullNoticeSent = true;
        }
        buffer.clearTimingWhenEmpty();
        if (processed > 0) RtsEffectAccumulator.INSTANCE.markPersistence(player.getUniqueID(), player.dimension);
        return processed;
    }

    public static void flushDropBufferToPlayer(EntityPlayerMP player, RtsStorageSession session) {
        RtsMiningDropBufferState buffer = session.miningDropBuffer;
        while (!buffer.stacks.isEmpty()) {
            ItemStack remainder = RtsTransferInserter.moveToPlayerInventoryOnly(player, buffer.stacks.removeFirst());
            if (!remainder.isEmpty()) player.dropItem(remainder, false);
        }
        buffer.clearTimingWhenEmpty();
    }

    private static DropInsertContext createInsertContext(EntityPlayerMP player, RtsStorageSession session) {
        // 聚合缓存是挖掘热路径的性能快路，但权限必须比性能优先。
        // 只要存在 Extract Only 链接，就实时解析可写端点，避免模式刚切换、
        // 页面尚未重挂载或第三方 capability 身份变化时，旧聚合视图继续收货。
        if (hasExtractOnlyLinkedStorage(session)) {
            List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
            return new DropInsertContext(null, RtsLinkedStorageResolver.itemHandlersForInsert(linked));
        }
        RtsAggregateStorage aggregate = RtsStorageTickService.INSTANCE.getStorage(player);
        if (aggregate != null && !aggregate.isEmpty())
            return new DropInsertContext(aggregate, Collections.<IItemHandler>emptyList());
        List<LinkedHandler> linked = RtsLinkedStorageResolver.resolveLinkedHandlers(player, session);
        return new DropInsertContext(null, RtsLinkedStorageResolver.itemHandlersForInsert(linked));
    }

    static boolean hasExtractOnlyLinkedStorage(RtsStorageSession session) {
        if (session == null || session.linkedStorageInfo.isEmpty()) return false;
        for (LinkedStorageRef ref : session.linkedStorageInfo.getAll()) {
            if (ref != null && RtsLinkedStorageResolver.isExtractOnlyLink(session, ref)) return true;
        }
        return false;
    }

    private static void notifyStorageChanged(EntityPlayerMP player, DropInsertContext context, boolean changed) {
        if (changed && context.usesAggregate()) RtsStorageTickService.INSTANCE.alert(player.getUniqueID());
    }

    private static void mergeRemainder(List<ItemStack> merged, ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : merged) {
            if (!sameExact(existing, remaining)) continue;
            int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            if (moved > 0) { existing.grow(moved); remaining.shrink(moved); }
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            merged.add(copyWithCount(remaining, amount));
            remaining.shrink(amount);
        }
    }

    private static int count(ItemStack stack) { return stack == null || stack.isEmpty() ? 0 : stack.getCount(); }
    private static ItemStack copyWithCount(ItemStack stack, int amount) {
        ItemStack copy = stack.copy(); copy.setCount(amount); return copy;
    }
    private static boolean sameExact(ItemStack a, ItemStack b) {
        return ItemStack.areItemsEqual(a, b) && ItemStack.areItemStackTagsEqual(a, b);
    }

    private static final class DropGroup {
        final ItemStack template;
        final List<EntityItem> entities = new ArrayList<EntityItem>();
        int totalCount;
        DropGroup(ItemStack template) { this.template = template; }
    }

    private static final class DropInsertContext {
        final RtsAggregateStorage aggregate;
        final List<IItemHandler> handlers;
        DropInsertContext(RtsAggregateStorage aggregate, List<IItemHandler> handlers) {
            this.aggregate = aggregate; this.handlers = handlers;
        }
        ItemStack store(ItemStack stack) {
            if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
            if (aggregate != null && !aggregate.isEmpty()) return aggregate.insert(stack, false);
            return handlers == null || handlers.isEmpty()
                    ? stack.copy() : RtsTransferInserter.storeToLinkedOnly(handlers, stack);
        }
        boolean usesAggregate() { return aggregate != null && !aggregate.isEmpty(); }
    }
}
